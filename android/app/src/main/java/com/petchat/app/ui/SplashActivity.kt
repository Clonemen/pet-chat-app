package com.petchat.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petchat.app.MainActivity
import com.petchat.app.R

/**
 * 启动闪屏：蓝渐变背景 + 浮动 3D 水滴吉祥物 + 「欢迎来到宠物聊天世界」。
 *
 * - 复用 [WaterDropMascot] 与 design/waterdrop_mascot 预览页一致的水滴动画。
 * - 展示约 [SPLASH_DURATION_MS] 后自动进入 MainActivity（登录 / 自动登录流程不变）。
 * - 右上角提供「跳过」按钮，点击立即进入，互不冲突（跳过会取消延时跳转）。
 */
class SplashActivity : ComponentActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 3500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val goNext = Runnable { goToMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SplashScreen(onSkip = ::skip)
        }

        // 延时跳转；用可取消的 Runnable，避免与「跳过」按钮重复跳转
        handler.postDelayed(goNext, SPLASH_DURATION_MS)
    }

    /** 用户点击「跳过」：取消延时并立即进入主流程 */
    private fun skip() {
        handler.removeCallbacks(goNext)
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 防止 Activity 销毁后回调仍触发跳转
        handler.removeCallbacks(goNext)
    }
}

@Composable
private fun SplashScreen(onSkip: () -> Unit) {
    val dropTint = Color(0xFF1B5E8C)

    WaterBackground {
        // 跳过按钮（右上角，3D 玻璃胶囊）
        GlassTextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 12.dp),
            contentColor = dropTint
        ) {
            Text(
                text = "跳过 ›",
                fontSize = 15.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WaterDropMascot(size = 220.dp, showShadow = true)

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "欢迎来到宠物聊天世界",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = dropTint,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "宠物聊天 · 你的桌面伙伴",
                fontSize = 13.sp,
                color = dropTint.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 8.dp)
            )
        }
    }
}
