package com.petchat.app.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 宠物远程控制台（调试 / 自动化用）。
 *
 * 通过显式广播启动或关闭悬浮宠物，无需经过 App UI 点击：
 *   am broadcast -n com.petchat.app/.floating.PetControlReceiver \
 *       -a com.petchat.app.action.START_PET --receiver-include-background
 *   am broadcast -n com.petchat.app/.floating.PetControlReceiver \
 *       -a com.petchat.app.action.STOP_PET --receiver-include-background
 */
class PetControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> {
                // 走真正的「前台服务」路径（与 App 内点开关一致），更贴近真实使用
                val svc = Intent(context, FloatingPetService::class.java)
                context.startForegroundService(svc)
            }
            ACTION_STOP -> {
                if (PetOverlayHost.running) PetOverlayHost.stopAll()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.petchat.app.action.START_PET"
        const val ACTION_STOP = "com.petchat.app.action.STOP_PET"
    }
}
