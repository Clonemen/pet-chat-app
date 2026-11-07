package com.petchat.app.floating

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 宠物悬浮窗无障碍服务。
 *
 * 作用只有一个：以系统级图层 TYPE_ACCESSIBILITY_OVERLAY 挂载悬浮宠物窗口。
 * 这台锤子 ROM 会把"后台应用"的普通悬浮窗（TYPE_APPLICATION_OVERLAY）策略性隐藏，
 * 而无障碍图层不受该策略影响——宠物即可常驻桌面。
 *
 * 本服务不监听任何无障碍事件、不读取任何窗口内容（canRetrieveWindowContent=false），
 * 纯粹作为窗口宿主，不收集任何数据。
 *
 * 用户操作：系统设置 → 无障碍 → 「宠物聊天·桌面宠物」开启一次即可。
 * 服务连接时若宠物处于开启状态，自动把窗口从普通模式升级为无障碍模式；
 * 服务断开时自动回退普通模式。
 */
class PetOverlayA11yService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 宠物已开启（服务在跑）→ 无缝升级为无障碍图层；否则什么都不做
        if (FloatingPetService.isRunning || PetOverlayHost.running) {
            PetOverlayHost.reattachAsAccessibility(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 刻意留空：本服务不消费任何无障碍事件
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        // 宠物还在开启状态 → 回退普通悬浮窗模式（退后台会被 ROM 隐藏，但 App 内仍可见）
        if (PetOverlayHost.running) {
            PetOverlayHost.fallbackFromAccessibility(applicationContext)
        }
        return super.onUnbind(intent)
    }

    companion object {
        /** 当前连接的实例（供 Host 判断可用性与取 Context） */
        var instance: PetOverlayA11yService? = null
            private set
    }
}
