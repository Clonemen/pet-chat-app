package com.petchat.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.petchat.app.MainActivity
import com.petchat.app.data.SessionStore
import com.petchat.app.floating.FloatingPetService

/**
 * 设置页：
 * - 桌面宠物的开启 / 关闭总开关（含 SYSTEM_ALERT_WINDOW 与 POST_NOTIFICATIONS 权限流程）；
 *   开关状态持久化到 [SessionStore.petEnabled]，App 重启后会依此自动恢复。
 * - 服务器地址修改（切换服务器会清除凭据退回登录）。
 * - 退出登录。
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var session: SessionStore
    private val petRunningState = mutableStateOf(FloatingPetService.isRunning)

    // 悬浮窗权限返回后，继续走通知权限检查
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) ensureNotifPermissionThenStart()
    }

    // 通知权限请求结果
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPet()
        else Toast.makeText(
            this, "需要通知权限才能运行桌面宠物（前台服务必须显示通知）", Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this)

        setContent {
            PetChatTheme {
                SettingsScreen(
                    initialServer = session.serverBase ?: "http://47.107.227.28:3000",
                    petOn = petRunningState,
                    onTogglePet = { togglePet() },
                    onSaveServer = { server ->
                        val trimmed = server.trim().removeSuffix("/")
                        val changed = trimmed != (session.serverBase ?: "")
                        session.serverBase = trimmed
                        if (changed) {
                            // 切换服务器：原 token 失效，清空凭据退回登录
                            FloatingPetService.stop(this)
                            session.petEnabled = false
                            session.token = null
                            session.userId = null
                            session.clear()
                            session.serverBase = trimmed
                            Toast.makeText(this, "已切换服务器，请重新登录", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "服务器地址已保存", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogout = {
                        FloatingPetService.stop(this)
                        session.petEnabled = false
                        session.clear()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun togglePet() {
        if (FloatingPetService.isRunning) {
            FloatingPetService.stop(this)
            petRunningState.value = false
            session.petEnabled = false
            return
        }
        // 先确保悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay()
            return
        }
        // 再确保通知权限（Android 13+ 必需）
        ensureNotifPermissionThenStart()
    }

    private fun ensureNotifPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPet()
        }
    }

    private fun requestOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayLauncher.launch(intent)
    }

    private fun startPet() {
        val server = session.serverBase ?: return
        val token = session.token ?: return
        val pet = session.getPet()
        FloatingPetService.start(
            context = this,
            serverBase = server,
            token = token,
            petImageUrl = pet?.imageUrl,
            petName = pet?.name ?: "宠物",
            petPresetRes = com.petchat.app.data.PetPresets.resOf(session.presetId)
        )
        petRunningState.value = true
        session.petEnabled = true
        Toast.makeText(this, "桌面宠物已启动，返回桌面查看 🐾", Toast.LENGTH_SHORT).show()
        requestIgnoreBatteryOptimizations()
    }

    /**
     * 请求"忽略电池优化"：激进省电的 ROM（锤子/小米等）会在锁屏后杀后台服务，
     * 弹一次系统确认框把本应用加入白名单，宠物才能长期后台常驻。
     */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    initialServer: String,
    petOn: androidx.compose.runtime.State<Boolean>,
    onTogglePet: () -> Unit,
    onSaveServer: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var server by remember { mutableStateOf(initialServer) }
    val on by petOn
    val scheme = MaterialTheme.colorScheme

    WaterBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            Text("设置", fontSize = 20.sp, color = scheme.onBackground)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // 桌面宠物总开关
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🐾 桌面宠物", fontSize = 16.sp, color = scheme.onBackground)
                    Text(
                        if (on) "已开启，返回桌面即可看到" else "关闭后宠物将从桌面消失",
                        fontSize = 12.sp,
                        color = scheme.secondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = on, onCheckedChange = { onTogglePet() })
            }
        }

        Spacer(Modifier.height(16.dp))

        // 液态玻璃档位：切换背景光斑色相 / 玻璃 tint / 边缘色散，即时生效并持久化
        val context = LocalContext.current
        val currentMode = LiquidGlassState.mode
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✦ 液态玻璃", fontSize = 15.sp, color = scheme.onBackground)
                Text(
                    "选择玻璃的光斑色相与折射质感，切换后立即生效",
                    fontSize = 11.sp,
                    color = scheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiquidGlassMode.values().forEach { mode ->
                        val selected = currentMode == mode
                        GlassTextButton(
                            onClick = { LiquidGlassState.set(context, mode) },
                            modifier = Modifier.weight(1f),
                            contentColor = if (selected) scheme.primary else scheme.secondary,
                            horizontalPadding = 6.dp,
                            verticalPadding = 9.dp
                        ) {
                            Text(
                                (if (selected) "● " else "○ ") + mode.label,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    currentMode.hint,
                    fontSize = 11.sp,
                    color = scheme.secondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务器地址", fontSize = 15.sp, color = scheme.onBackground)
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = server,
                    onValueChange = { server = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "默认已指向云服务器 http://47.107.227.28:3000；如需改用其他地址可在此修改",
                    fontSize = 11.sp,
                    color = scheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                GlassButton(
                    onClick = { onSaveServer(server) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存服务器地址")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        GlassOutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            contentColor = Color(0xFFE05A5A)
        ) {
            Text("退出登录")
        }
        } // 关闭 Column
    } // 关闭 WaterBackground
}
