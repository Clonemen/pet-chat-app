package com.petchat.app.floating

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.petchat.app.data.PetPersona
import com.petchat.app.data.PetPresets
import com.petchat.app.model.ActionType

/**
 * 悬浮宠物的可观察状态（共享给 WebSocket 客户端与 Compose 视图）。
 * 所有写操作都切到主线程，确保 Compose 快照系统正确触发重组。
 */
class PetOverlayState {

    val petImageUrl: MutableState<String?> = mutableStateOf(null)
    /** 内置 3D 预设形象的 drawable 资源 id（优先于 petImageUrl 显示） */
    val petResId: MutableState<Int?> = mutableStateOf(null)
    val petName: MutableState<String> = mutableStateOf("宠物")
    val action: MutableState<ActionType> = mutableStateOf(ActionType.IDLE)
    val bubbleText: MutableState<String?> = mutableStateOf(null)

    /** 当前人设（戳一戳/自言自语/打招呼台词），随预设形象切换 */
    @Volatile
    var persona: PetPersona = PetPresets.DEFAULT_PERSONA

    // ── 访客变身：好友来消息时，宠物临时变成对方的宠物 ──────────────────

    /** 好友宠物临时形象（URL，优先级最高） */
    val visitImageUrl: MutableState<String?> = mutableStateOf(null)
    /** 好友宠物临时形象（本地 3D 预设资源 id，URL 为空时使用） */
    val visitResId: MutableState<Int?> = mutableStateOf(null)
    /** 访客态显示的宠物名（好友的宠物名 / 好友昵称） */
    val visitName: MutableState<String?> = mutableStateOf(null)
    /** 是否处于访客态（气泡不自动收起，由访客计时器收） */
    val inVisit: MutableState<Boolean> = mutableStateOf(false)
    /** 访客对应的好友 id（供点击打开聊天） */
    var visitFriendId: String? = null
        private set
    /** 点击访客宠物时打开聊天（由 Service 注入） */
    var onVisitClick: ((String, String) -> Unit)? = null

    private var visitRevert: Runnable? = null

    /**
     * 好友来消息：变身对方的宠物 + 弹出消息气泡。
     * VISIT_MS 后自动变回自己的宠物；期间再来消息会刷新形象并续时。
     */
    fun showFriendVisit(
        friendId: String?,
        senderName: String?,
        senderPetName: String?,
        senderPetImage: String?,
        senderPetSpecies: String?,
        messageText: String?,
        presetResBySpecies: Int?
    ) {
        main.post {
            visitRevert?.let { main.removeCallbacks(it) }
            visitFriendId = friendId
            visitName.value = senderPetName?.takeIf { it.isNotBlank() } ?: senderName
            visitImageUrl.value = senderPetImage?.takeIf { it.isNotBlank() }
            visitResId.value = presetResBySpecies
            inVisit.value = true
            action.value = ActionType.GREETING
            bubbleText.value = if (messageText.isNullOrBlank())
                "${senderName ?: "好友"} 来找你玩啦 🐾"
            else
                "${senderName ?: "好友"}：$messageText"
            val revert = Runnable { endFriendVisit() }
            visitRevert = revert
            main.postDelayed(revert, VISIT_MS)
        }
    }

    /** 访客计时到点：变回自己的宠物 */
    fun endFriendVisit() {
        main.post {
            visitRevert = null
            visitFriendId = null
            visitImageUrl.value = null
            visitResId.value = null
            visitName.value = null
            inVisit.value = false
            action.value = ActionType.IDLE
            bubbleText.value = null
        }
    }

    companion object {
        /** 访客变身持续时长 */
        private const val VISIT_MS = 8000L
    }

    private val main = Handler(Looper.getMainLooper())

    fun setPet(imageUrl: String?, name: String) {
        main.post {
            petImageUrl.value = imageUrl
            petName.value = name
        }
    }

    fun setPresetRes(resId: Int?) {
        main.post { petResId.value = resId }
    }

    fun applyPersona(p: PetPersona) {
        persona = p
    }

    /** 收到好友消息时调用：切换动作 + 气泡 */
    fun react(action: ActionType, text: String?) {
        main.post {
            this.action.value = action
            this.bubbleText.value = text
        }
    }

    /** 动作播完自动回到 idle，并收起气泡 */
    fun backToIdle() {
        main.post {
            action.value = ActionType.IDLE
            bubbleText.value = null
        }
    }
}
