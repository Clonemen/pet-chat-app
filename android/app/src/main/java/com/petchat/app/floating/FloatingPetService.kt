package com.petchat.app.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.petchat.app.R

/**
 * 桌面悬浮宠物前台服务（保活壳）。
 *
 * 窗口托管已移交 [PetOverlayHost]（单例）：
 * - 普通模式：TYPE_APPLICATION_OVERLAY（App 内可用；这台锤子 ROM 会在退后台后隐藏它）
 * - 无障碍模式：TYPE_ACCESSIBILITY_OVERLAY（由 [PetOverlayA11yService] 挂载，桌面常驻）
 *
 * 本服务职责：前台通知保活（Android 8+ 必需）+ START_STICKY 自愈 + WebSocket 存活。
 */
class FloatingPetService : Service() {

    override fun onCreate() {
        super.onCreate()
        // 访客态点击好友宠物：直接打开与该好友的聊天页
        PetOverlayHost.state.onVisitClick = { friendId, friendName ->
            val intent = Intent(this, com.petchat.app.ui.ChatActivity::class.java).apply {
                putExtra(com.petchat.app.ui.ChatActivity.EXTRA_FRIEND_ID, friendId)
                putExtra(com.petchat.app.ui.ChatActivity.EXTRA_FRIEND_NAME, friendName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        try {
            // Android 14+ 要求前台服务显式声明类型（specialUse，配合 manifest 声明）；
            // Android 10~13 使用两参重载即可，避免传入不被旧系统识别的 type 值导致崩溃。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, buildNotification(), foregroundType())
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            // 通知权限缺失等导致无法作为前台服务运行：直接停止，避免崩溃
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isAutoRestore = (intent == null)
        isRunning = true
        // 窗口/状态/WS 全部由 Host 托管（被杀重启时 Host 从 SessionStore 自愈恢复）
        PetOverlayHost.start(this, autoRestore = isAutoRestore)
        if (isAutoRestore) {
            Toast.makeText(this, "桌面宠物已自动恢复 🐾", Toast.LENGTH_SHORT).show()
        }
        // 被杀后系统重启服务（START_STICKY + SessionStore 持久化 token 方可真正恢复）
        return START_STICKY
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }

    private fun buildNotification(): Notification {
        val channelId = getString(R.string.notification_channel_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelId, NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("桌面宠物运行中")
            .setContentText("你的电子宠物正在桌面上陪着你")
            .setSmallIcon(R.drawable.ic_pet)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        PetOverlayHost.stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** 当前是否正在运行（供 UI 显示开关状态） */
        var isRunning: Boolean = false
            private set

        private const val NOTIF_ID = 1001

        fun start(
            context: Context,
            serverBase: String,
            token: String,
            petImageUrl: String?,
            petName: String,
            petPresetRes: Int? = null
        ) {
            // serverBase/token/宠物信息均以 SessionStore 为准（Host 内部读取），extras 仅保留兼容
            val intent = Intent(context, FloatingPetService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPetService::class.java))
        }
    }
}
