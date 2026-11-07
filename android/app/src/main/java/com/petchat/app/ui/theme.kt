package com.petchat.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 统一「透明水纹式」主题：浅水色玻璃拟态（Glassmorphism）。
 *
 * 设计语言：
 *  - 全 App 共享同一张水色渐变背景（[waterBackgroundBrush]），四角带动态扩散的「水波纹」。
 *  - 所有卡片使用磨砂玻璃（[GlassCard]：半透明白 + 白色描边 + 柔和投影），背景透过卡片隐约可见，
 *    呈现「透明」质感。
 *  - 配色以水蓝 / 海洋蓝 / 薄荷青为主，文字为深海军蓝，保证浅色背景上的可读性。
 */

/** 共享水色渐变（上浅下蓝的清水感） */
fun waterBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFEAF7FF),
        Color(0xFFD2EEFF),
        Color(0xFFB6E3FF),
        Color(0xFF9BD6FF)
    )
)

private val WaterColorScheme = lightColorScheme(
    primary = Color(0xFF1E9FD8),          // 水蓝（主色 / 按钮 / 强调）
    onPrimary = Color(0xFFFFFFFF),         // 主色上的文字（白）
    primaryContainer = Color(0xFFD6F0FB),
    secondary = Color(0xFF3A7CA5),         // 海洋蓝（次级文字 / 说明）
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF56C6B8),          // 薄荷青（点缀 / 水波）
    background = Color.Transparent,        // 背景由 WaterBackground 绘制渐变
    surface = Color(0xFFFFFFFF),           // 玻璃卡片底色（半透明叠加）
    onBackground = Color(0xFF0E2E3F),      // 深海军蓝（正文）
    onSurface = Color(0xFF0E2E3F),
    outline = Color(0xFF9FCFE6),
    error = Color(0xFFE05A5A)
)

@Composable
fun PetChatTheme(
    content: @Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = WaterColorScheme,
        content = content
    )
}

/**
 * 统一水纹背景容器：水色渐变 + 动态水波纹装饰，所有页面共用。
 * 把原本各页 `Column(Modifier.background(scheme.background))` 替换为 `WaterBackground { ... }`。
 */
@Composable
fun WaterBackground(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.background(waterBackgroundBrush())) {
        WaterRipples()
        content()
    }
}

/**
 * 磨砂玻璃卡片：半透明白 + 白色细描边 + 柔和投影，背景透过卡片隐约可见（透明水纹质感）。
 * 作为 [androidx.compose.material3.ElevatedCard] 的统一种替换。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassStyle.PANEL_RADIUS),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White.copy(alpha = GlassStyle.PANEL_TOP_ALPHA * 0.68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        content = content
    )
}

/** 动态水波纹装饰：两处错相位的扩散圆环，缓慢向外荡开再淡出，营造「水纹」流动感。 */
@Composable
private fun WaterRipples() {
    val transition = rememberInfiniteTransition(label = "waterRipple")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = androidx.compose.animation.core.StartOffset(3000)
        ),
        label = "ripple2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRipple(
            center = Offset(w * 0.16f, h * 0.14f),
            progress = p1,
            maxR = minOf(w, h) * 0.55f,
            color = Color.White.copy(alpha = 0.28f),
            stroke = 3.dp.toPx()
        )
        drawRipple(
            center = Offset(w * 0.86f, h * 0.9f),
            progress = p2,
            maxR = minOf(w, h) * 0.6f,
            color = Color(0xFF1E9FD8).copy(alpha = 0.16f),
            stroke = 3.dp.toPx()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRipple(
    center: Offset,
    progress: Float,
    maxR: Float,
    color: Color,
    stroke: Float
) {
    val r = maxR * (0.18f + progress * 0.92f)
    drawCircle(
        color = color.copy(alpha = color.alpha * (1f - progress)),
        radius = r,
        center = center,
        style = Stroke(width = stroke)
    )
}
