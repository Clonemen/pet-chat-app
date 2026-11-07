package com.petchat.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petchat.app.R

/**
 * 可复用的 3D 水滴吉祥物动画组件（忠实复刻 design/waterdrop_mascot 预览页）。
 *
 * 动画：
 *  - float   : 上下浮动 + 轻微左右摇摆
 *  - breathe : 呼吸式挤压（缩放 X/Y 反向）
 *  - shadow  : 接地阴影随浮动缩放 + 淡入淡出
 *  - shimmer : 缓慢扫过的水面高光
 *  - blink   : 周期性眨眼下眼睑
 *
 * 所有尺寸都按 [size] 等比缩放，因此闪屏（大）与登录页（小）都能复用。
 */
@Composable
fun WaterDropMascot(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    showShadow: Boolean = true,
    dropResId: Int = R.drawable.splash_drop
) {
    val s = size.value // 以 dp 数值参与比例换算
    val infinite = rememberInfiniteTransition(label = "drop")

    // 浮动：上移 + 摇摆
    val floatY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -(s * 0.13f),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val rot by infinite.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rot"
    )
    // 呼吸：X 放大 / Y 压扁 反向
    val breatheX by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheX"
    )
    val breatheY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheY"
    )
    // 接地阴影：随浮动缩小 + 变淡
    val shadowScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.66f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadowScale"
    )
    val shadowAlpha by infinite.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadowAlpha"
    )
    // 水面高光：缓慢出现
    val shimmerAlpha by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    // 眨眼：约每 5.2s 闭眼一次
    val blinkScaleY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes<Float> {
                durationMillis = 5200
                0f at 0
                0f at 4700
                1f at 4860
                0f at 4990
                0f at 5200
            }
        ),
        label = "blinkScaleY"
    )

    val sizePx = with(LocalDensity.current) { size.toPx() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 接地阴影（随浮动缩放 + 淡入淡出）
        if (showShadow) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (s * -0.17f).dp)
                    .size((s * 0.75f).dp, (s * 0.12f).dp)
                    .graphicsLayer {
                        scaleX = shadowScale
                        scaleY = shadowScale
                        alpha = shadowAlpha
                    }
                    .background(Color(0xFF1E78C8), CircleShape)
            )
        }

        // 水滴主体（浮动 + 呼吸 + 摇摆）
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(dropResId),
                contentDescription = "水滴宠物吉祥物",
                modifier = Modifier
                    .size(size)
                    .offset(y = floatY.dp)
                    .graphicsLayer {
                        rotationZ = rot
                        scaleX = breatheX
                        scaleY = breatheY
                    }
            )

            // 水面高光：左上角柔光，随浮动同步上下
            Box(
                modifier = Modifier
                    .size(size)
                    .offset(y = floatY.dp)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xFFFFFFFF),
                                0.38f to Color(0x00FFFFFF)
                            ),
                            center = Offset(sizePx * 0.35f, sizePx * 0.3f),
                            radius = sizePx * 0.6f
                        )
                    )
                    .graphicsLayer { alpha = shimmerAlpha }
            )

            // 眨眼下眼睑：盖在眼睛区域，scaleY 由 blink 控制
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (s * 0.37f).dp)
                    .size((s * 0.49f).dp, (s * 0.11f).dp)
                    .graphicsLayer {
                        scaleY = blinkScaleY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF8FD0F2), Color(0xFF5BA9E0))
                        )
                    )
            )
        }
    }
}
