package com.petchat.app.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ============================================================================
 *  Liquid Glass 主题系统（液态玻璃）
 * ----------------------------------------------------------------------------
 *  与旧版「静态水纹玻璃拟态」的区别：
 *   · 背景不再是固定线性渐变 + 两个扩散圆环，而是 4 团持续漂移、呼吸的彩色光斑，
 *     叠加 3 条流动折射等高线 —— 玻璃后面「有东西在流动」，才折得出液态感。
 *   · 玻璃组件不再只有固定左上高光，而是带一条随 [LocalLiquidSweep] 周期扫过的
 *     镜面高光、表面涟漪、中心透镜 warp，以及一圈彩虹色散边缘（见 Glass3D.kt）。
 *   · 档位（[LiquidGlassMode]）切换的是光斑色相 / 玻璃 tint / 色散色，全部保持浅底
 *     深字，任何档位下文字对比度都安全。
 *
 *  性能约束：目标机为 Android 10（API 29），RenderEffect / Modifier.blur 不可用，
 *  因此全部效果用 Canvas 绘制；动画值以 State<Float> 形式在 draw 阶段读取，
 *  只触发重绘、不触发重组，低端机也不会掉帧。
 * ============================================================================
 */

/** 液态玻璃档位：切换光斑色相与玻璃着色，全部为浅色底 + 深色字。 */
enum class LiquidGlassMode(val key: String, val label: String, val hint: String) {
    AQUA("aqua", "清透水蓝", "澄澈流动的水感玻璃"),
    AURORA("aurora", "极光流彩", "紫粉青交织的极光折射"),
    FROST("frost", "霜白凝光", "低饱和霜雾与冷白高光");

    companion object {
        fun fromKey(key: String?): LiquidGlassMode =
            values().firstOrNull { it.key == key } ?: AQUA
    }
}

/** 一个档位的完整调色板。 */
data class LiquidPalette(
    val baseTop: Color,
    val baseMid: Color,
    val baseBottom: Color,
    val orbs: List<Color>,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onBackground: Color,
    val dispersion: List<Color>
)

object LiquidGlassPalettes {

    val AQUA = LiquidPalette(
        baseTop = Color(0xFFF4FBFF),
        baseMid = Color(0xFFD9F0FF),
        baseBottom = Color(0xFFA9D9F6),
        orbs = listOf(
            Color(0xFF63D6FF),
            Color(0xFF8FB8FF),
            Color(0xFF6CE6CC),
            Color(0xFFC1EAFF)
        ),
        primary = Color(0xFF1689C9),
        secondary = Color(0xFF3A6E92),
        tertiary = Color(0xFF2FBDAE),
        onBackground = Color(0xFF0C2A3C),
        dispersion = listOf(
            Color(0xFF7FE7FF), Color(0xFFB3AAFF), Color(0xFFFFB9E8),
            Color(0xFFFFFFFF), Color(0xFF8FE3FF)
        )
    )

    val AURORA = LiquidPalette(
        baseTop = Color(0xFFFBF7FF),
        baseMid = Color(0xFFEADFFF),
        baseBottom = Color(0xFFC9D9FA),
        orbs = listOf(
            Color(0xFFC08BFF),
            Color(0xFFFF9ECB),
            Color(0xFF78D8F2),
            Color(0xFFA6B6FF)
        ),
        primary = Color(0xFF7A53C6),
        secondary = Color(0xFF5C5B8C),
        tertiary = Color(0xFFC056A4),
        onBackground = Color(0xFF281C3E),
        dispersion = listOf(
            Color(0xFFB08BFF), Color(0xFFFFA6D8), Color(0xFF7FE2FF),
            Color(0xFFFFFFFF), Color(0xFFC2A9FF)
        )
    )

    val FROST = LiquidPalette(
        baseTop = Color(0xFFFBFDFF),
        baseMid = Color(0xFFE9F1F7),
        baseBottom = Color(0xFFCFDEE9),
        orbs = listOf(
            Color(0xFFB8D8EA),
            Color(0xFFD9CFEE),
            Color(0xFFBEE7E2),
            Color(0xFFE4EEF7)
        ),
        primary = Color(0xFF4A7B96),
        secondary = Color(0xFF576E80),
        tertiary = Color(0xFF6FA39B),
        onBackground = Color(0xFF1B3040),
        dispersion = listOf(
            Color(0xFFC7E8F7), Color(0xFFDED6F5), Color(0xFFC9EFE9),
            Color(0xFFFFFFFF), Color(0xFFD2E6F2)
        )
    )

    fun of(mode: LiquidGlassMode): LiquidPalette = when (mode) {
        LiquidGlassMode.AQUA -> AQUA
        LiquidGlassMode.AURORA -> AURORA
        LiquidGlassMode.FROST -> FROST
    }
}

/**
 * 全局档位状态。用独立 SharedPreferences（不复用 SessionStore，退出登录 clear()
 * 不会把外观设置一起清掉），切换时立刻驱动所有页面重组。
 */
object LiquidGlassState {
    private const val PREFS = "petchat_liquid_glass"
    private const val KEY_MODE = "mode"

    var mode by mutableStateOf(LiquidGlassMode.AQUA)
        private set

    fun load(context: Context) {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        mode = LiquidGlassMode.fromKey(key)
    }

    fun set(context: Context, value: LiquidGlassMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value.key).apply()
        mode = value
    }
}

/** 镜面高光扫过的进度（0→1 循环），draw 阶段读取，避免重组。 */
val LocalLiquidSweep = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** 背景光斑漂移的进度（0→1 循环）。 */
val LocalLiquidFlow = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** 当前调色板，供玻璃组件取色散色 / tint。 */
val LocalLiquidPalette = staticCompositionLocalOf { LiquidGlassPalettes.AQUA }

@Composable
fun PetChatTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { LiquidGlassState.load(context) }

    val palette = LiquidGlassPalettes.of(LiquidGlassState.mode)
    val transition = rememberInfiniteTransition(label = "liquidGlass")
    val sweepState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidSweep"
    )
    val flowState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidFlow"
    )

    CompositionLocalProvider(
        LocalLiquidSweep provides sweepState,
        LocalLiquidFlow provides flowState,
        LocalLiquidPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorSchemeOf(palette),
            content = content
        )
    }
}

private fun colorSchemeOf(p: LiquidPalette) = lightColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.baseMid,
    secondary = p.secondary,
    onSecondary = Color.White,
    tertiary = p.tertiary,
    background = Color.Transparent,   // 背景由 WaterBackground 绘制
    surface = Color.White,
    onBackground = p.onBackground,
    onSurface = p.onBackground,
    outline = p.primary.copy(alpha = 0.42f),
    error = Color(0xFFE05A5A)
)

/** 兼容旧调用：默认档位的静态背景渐变（窗口背景 / 无需动画的场合）。 */
fun waterBackgroundBrush(): Brush = Brush.verticalGradient(
    listOf(
        LiquidGlassPalettes.AQUA.baseTop,
        LiquidGlassPalettes.AQUA.baseMid,
        LiquidGlassPalettes.AQUA.baseBottom
    )
)

/**
 * 全 App 共享的液态背景：渐变底 + 持续漂移的彩色光斑 + 流动折射等高线。
 * 页面内容叠在光斑之上，半透明玻璃面板因此能「透出流动的背景」。
 */
@Composable
fun WaterBackground(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidPalette.current
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(palette.baseTop, palette.baseMid, palette.baseBottom)
            )
        )
    ) {
        LiquidFlowField(palette)
        content()
    }
}

/** 流动光斑 + 折射等高线：全部在 DrawScope 里读动画值，只重绘不重组。 */
@Composable
private fun LiquidFlowField(palette: LiquidPalette) {
    val flow = LocalLiquidFlow.current
    val sweep = LocalLiquidSweep.current

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = flow.value * 2f * PI.toFloat()
        val s = sweep.value
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)

        // ① 四团缓慢漂移 / 呼吸的彩色光斑（液态底色的来源）
        val specs = listOf(
            Triple(0.18f, 0.15f, 0.52f),
            Triple(0.86f, 0.26f, 0.44f),
            Triple(0.26f, 0.84f, 0.50f),
            Triple(0.90f, 0.90f, 0.40f)
        )
        specs.forEachIndexed { i, (bx, by, baseR) ->
            val phase = t + i * 1.65f
            val cx = w * (bx + sin(phase) * 0.085f)
            val cy = h * (by + cos(phase * 0.82f) * 0.07f)
            val r = unit * baseR * (0.92f + 0.12f * sin(phase * 1.3f))
            val orb = palette.orbs[i]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb.copy(alpha = 0.60f), orb.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        // ② 三条流动折射等高线：模拟玻璃下方的液面起伏
        for (k in 0 until 3) {
            val baseY = h * (0.28f + k * 0.23f) + sin(t * 0.7f + k) * h * 0.028f
            val amp = h * (0.09f + 0.02f * sin(t + k))
            val path = Path().apply {
                moveTo(-w * 0.12f, baseY)
                quadraticBezierTo(w * 0.26f, baseY - amp, w * 0.54f, baseY)
                quadraticBezierTo(w * 0.82f, baseY + amp, w * 1.12f, baseY - amp * 0.25f)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.22f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // ③ 缓慢扩散的液面涟漪（低对比，只提供「活着的」质感）
        for (k in 0 until 2) {
            val progress = (s + k * 0.5f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.14f * (1f - progress)),
                radius = unit * (0.12f + progress * 0.72f),
                center = Offset(w * 0.52f, h * 0.46f),
                style = Stroke(width = 1.6.dp.toPx())
            )
        }
    }
}

/** 液态玻璃卡片：统一走 [GlassSurface]，自动获得色散边缘 / 扫光 / 透镜质感。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassStyle.PANEL_RADIUS),
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        elevation = 14.dp
    ) {
        content()
    }
}
