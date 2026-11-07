package com.petchat.app.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.petchat.app.data.SessionStore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import com.petchat.app.im.PetWebSocketClient

/**
 * 悬浮宠物窗口宿主（单例，架构"桌面层"的宿主内核）。
 *
 * 为什么需要它：这台锤子 ROM 会把"后台应用"的 TYPE_APPLICATION_OVERLAY 窗口
 * 策略性隐藏（dumpsys 可见 mPolicyVisibility=false / isReadyForDisplay=false），
 * 导致退出 App 后宠物消失。
 *
 * 解决方案：提供两种挂载方式——
 * 1. 普通模式：TYPE_APPLICATION_OVERLAY（悬浮窗权限即可，但退后台会被 ROM 隐藏）
 * 2. 无障碍模式：TYPE_ACCESSIBILITY_OVERLAY（由本 App 的无障碍服务挂载，
 *    属系统级图层，ROM 不做后台抑制；需用户在"无障碍"里开启一次）
 *
 * 窗口可随时在两种模式间"平移升级"（remove + re-add，PetOverlayState 是单例不丢状态）。
 */
object PetOverlayHost {

    /** 共享的可观察宠物状态（换窗口类型时状态不丢） */
    val state = PetOverlayState()

    private var wsClient: PetWebSocketClient? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    /** 挂窗用的 Context（普通模式=服务，无障碍模式=无障碍服务实例） */
    private var hostCtx: Context? = null

    var running = false
        private set
    var currentType = 0
        private set

    // ── ComposeView 挂 WindowManager 的 Lifecycle/SavedState 注入（反射跨版本兼容） ──

    private class OverlayLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var composeLifecycleOwner: OverlayLifecycleOwner? = null
    private var composeViewTreeOwner: Any? = null

    private fun buildViewTreeOwner(ctx: Context): Any? = try {
        val lifecycleOwnerClass = Class.forName("androidx.lifecycle.LifecycleOwner")
        val savedStateOwnerClass = Class.forName("androidx.savedstate.SavedStateRegistryOwner")
        val controllerClass = Class.forName("androidx.savedstate.SavedStateRegistryController")

        val registry = composeLifecycleOwner!!.lifecycle
        var savedStateRegistry: Any? = null
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getLifecycle" -> registry
                "getSavedStateRegistry" -> savedStateRegistry
                "equals" -> (proxy === args?.firstOrNull())
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OverlayViewTreeOwner"
                else -> null
            }
        }
        val proxy = Proxy.newProxyInstance(
            ctx.classLoader,
            arrayOf(lifecycleOwnerClass, savedStateOwnerClass),
            handler
        )
        val controller = try {
            controllerClass.getMethod("create", savedStateOwnerClass).invoke(null, proxy)
        } catch (_: Throwable) {
            val companion = controllerClass.getField("Companion").get(null)
            companion.javaClass.getMethod("create", savedStateOwnerClass).invoke(companion, proxy)
        }
        controllerClass.getMethod("performRestore", Bundle::class.java).invoke(controller, null)
        savedStateRegistry = controllerClass.getMethod("getSavedStateRegistry").invoke(controller)
        composeLifecycleOwner!!.registry.currentState = Lifecycle.State.RESUMED
        proxy
    } catch (e: Throwable) {
        val cause = (e as? InvocationTargetException)?.cause ?: e
        android.util.Log.e("FloatingPet", "buildViewTreeOwner FAILED: ${cause.javaClass.name}: ${cause.message}")
        null
    }

    private fun bindLifecycleOwnerToComposeView(view: ComposeView, owner: LifecycleOwner) {
        try {
            Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                .getMethod("set", android.view.View::class.java, LifecycleOwner::class.java)
                .invoke(null, view, owner)
        } catch (e: Throwable) {
            android.util.Log.e("FloatingPet", "set LifecycleOwner FAILED: ${e.message}")
        }
        val ssOwner = composeViewTreeOwner
        if (ssOwner != null) {
            try {
                Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                    .getMethod(
                        "set", android.view.View::class.java,
                        Class.forName("androidx.savedstate.SavedStateRegistryOwner")
                    )
                    .invoke(null, view, ssOwner)
            } catch (e: Throwable) {
                android.util.Log.e("FloatingPet", "set SavedStateRegistryOwner FAILED: ${e.message}")
            }
        }
    }

    // ── 对外入口 ──

    /**
     * 启动/刷新宠物（App UI 里点"开启桌面宠物"时调用）。
     * 若无障碍服务已连接则直接以无障碍模式挂窗，否则普通模式。
     */
    fun start(context: Context, autoRestore: Boolean) {
        val app = context.applicationContext
        hostCtx = if (PetOverlayA11yService.instance != null) PetOverlayA11yService.instance else app
        running = true

        // 后台自愈：进程被系统杀死后 START_STICKY 重启时 intent 为 null，
        // 此时从 SessionStore 恢复 server/token/宠物信息，宠物"满血复活"。
        val session = SessionStore(app)
        val savedPet = session.getPet()
        val presetId = session.presetId
        val petUrl = savedPet?.imageUrl
        val petName = savedPet?.name ?: "宠物"
        val presetRes = com.petchat.app.data.PetPresets.resOf(presetId)

        state.setPet(petUrl, petName)
        state.setPresetRes(presetRes)
        state.applyPersona(com.petchat.app.data.PetPresets.personaOf(presetId))

        val type = if (PetOverlayA11yService.instance != null)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        attachWindow(type, showToast = autoRestore)

        connectWs(app)
    }

    /** 无障碍服务连接后调用：把窗口升级为系统级无障碍图层（退后台不再被 ROM 隐藏） */
    fun reattachAsAccessibility(service: PetOverlayA11yService) {
        if (!running) return
        hostCtx = service
        if (currentType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) return
        attachWindow(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, showToast = false)
        android.util.Log.i("FloatingPet", "已升级为无障碍悬浮图层（桌面常驻模式）")
    }

    /** 无障碍服务断开后调用：回退普通模式 */
    fun fallbackFromAccessibility(appContext: Context) {
        if (!running) return
        hostCtx = appContext
        attachWindow(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, showToast = false)
    }

    /** 关闭宠物（UI 开关/长按菜单） */
    fun stopAll() {
        running = false
        wsClient?.close()
        wsClient = null
        composeView?.let { cv ->
            windowManager?.let { wm -> runCatching { wm.removeView(cv) } }
        }
        composeView = null
        params = null
        windowManager = null
        hostCtx = null
        currentType = 0
    }

    // ── 内部实现 ──

    private fun connectWs(app: Context) {
        if (wsClient != null) return
        val session = SessionStore(app)
        val serverBase = session.serverBase.orEmpty()
        val token = session.token.orEmpty()
        if (serverBase.isBlank() || token.isBlank()) return
        wsClient = PetWebSocketClient(state) { /* 连接状态回调，暂仅日志 */ }
        wsClient?.connect(baseWs = serverBase.replaceFirst("^http".toRegex(), "ws"), token = token)
    }

    private fun attachWindow(type: Int, showToast: Boolean) {
        val ctx = hostCtx ?: return
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 旧窗口先摘掉（升级/降级窗口类型必须 remove + re-add）
        composeView?.let { old -> runCatching { wm.removeView(old) } }

        composeLifecycleOwner = OverlayLifecycleOwner()
        composeViewTreeOwner = buildViewTreeOwner(ctx)

        val cv = ComposeView(ctx).apply {
            bindLifecycleOwnerToComposeView(this, composeLifecycleOwner!!)
            setContent {
                FloatingPetContent(
                    state = state,
                    onDrag = { dx, dy -> moveWindow(dx, dy) },
                    onClose = { stopAll() }
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            x = prefs.getInt(KEY_X, 200)
            y = prefs.getInt(KEY_Y, 320)
        }
        try {
            wm.addView(cv, lp)
            composeView = cv
            params = lp
            windowManager = wm
            currentType = type
            if (showToast && type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                Toast.makeText(ctx, "桌面宠物已启动，返回桌面查看 🐾", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingPet", "attachWindow FAILED(type=$type): ${e.message}")
            if (type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                // 无障碍挂载失败：回退普通模式
                hostCtx = ctx.applicationContext
                attachWindow(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, showToast = false)
            } else {
                Toast.makeText(ctx, "无法创建悬浮窗：${e.message}", Toast.LENGTH_LONG).show()
                stopAll()
            }
        }
    }

    private fun moveWindow(dx: Float, dy: Float) {
        val lp = params ?: return
        val cv = composeView ?: return
        val wm = windowManager ?: return
        val dm = hostCtx?.resources?.displayMetrics ?: return
        // 视图还没量出尺寸时，用透明宠物区的估算值兜底（约 120dp 宽 / 170dp 高）
        val w = if (cv.width > 0) cv.width else (120 * dm.density).toInt()
        val h = if (cv.height > 0) cv.height else (170 * dm.density).toInt()
        // 夹在屏幕范围内，避免宠物被拖出屏幕找不回来
        lp.x = (lp.x + dx.toInt()).coerceIn(0, (dm.widthPixels - w).coerceAtLeast(0))
        lp.y = (lp.y + dy.toInt()).coerceIn(0, (dm.heightPixels - h).coerceAtLeast(0))
        wm.updateViewLayout(cv, lp)
        savePosition()
    }

    private fun savePosition() {
        val lp = params ?: return
        val ctx = hostCtx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_X, lp.x)
            .putInt(KEY_Y, lp.y)
            .apply()
    }

    /** 用户是否已在系统"无障碍"里开启我们的服务 */
    fun isA11yEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${PetOverlayA11yService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                    it.equals("${context.packageName}/.floating.PetOverlayA11yService", ignoreCase = true)
        }
    }

    private const val PREFS = "floating_pet"
    private const val KEY_X = "last_x"
    private const val KEY_Y = "last_y"
}
