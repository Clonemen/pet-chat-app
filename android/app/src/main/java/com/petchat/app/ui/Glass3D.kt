package com.petchat.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/*
 * ============================================================================
 *  Liquid Glass 组件库（液态玻璃）
 * ----------------------------------------------------------------------------
 *  在「流动光斑背景」（见 theme.kt）之上，统一所有按钮 / 卡片 / 对话框 / 菜单 /
 *  输入框的液态玻璃质感：
 *   · 玻璃底     —— 竖向渐变（顶部亮、底部沉），模拟玻璃厚度与透光
 *   · 透镜 warp  —— 中心微亮、边缘微沉的径向渐变，模拟曲面折射
 *   · 动态扫光   —— 一条斜向镜面高光周期性划过玻璃表面
 *   · 表面涟漪   —— 随扫光扩散的极细同心弧线（ripple）
 *   · 色散边缘   —— 彩虹渐变外圈 + 白色 rim 内圈，模拟镜片边缘的光学色散
 *   · 立体投影   —— 带主色色相的柔和外阴影（spot / ambient）
 *   · 按压反馈   —— 按下时缩放 + 投影收缩，形成物理按压手感
 *
 *  Android 10（API 29）没有 RenderEffect，所有效果均由 Canvas 绘制；动画进度
 *  在 draw 阶段读取 State，只重绘不重组。参数集中在 [GlassStyle]。
 * ============================================================================
 */

/* ============================== 全局质感参数 ============================== */

/**
 * 全局液态玻璃质感参数（通透度 / 反光 / 色散 / 圆角）。
 *
 * 液态档位：**高通透 + 动态扫光 + 显式色散**
 *  - 通透：面板/按钮底色整体下调，背后流动光斑与折射等高线能明显透出；
 *  - 扫光：镜面高光周期性划过表面，配合涟漪与透镜 warp 制造「液态」感；
 *  - 色散：边缘彩虹圈比旧版更清晰，形成镜片边缘的光学折射。
 */
object GlassStyle {

    /* ---------- 面板（对话框 / 菜单 / 卡片底） ---------- */

    /** 面板顶部玻璃不透明度（越低越通透） */
    val PANEL_TOP_ALPHA = 0.54f

    /** 面板底部玻璃不透明度 */
    val PANEL_BOTTOM_ALPHA = 0.26f

    /** 面板高光描边不透明度 */
    val PANEL_EDGE_ALPHA = 0.95f

    /** 面板圆角 */
    val PANEL_RADIUS = 26.dp

    /** 对话框圆角（比一般面板更圆润） */
    val DIALOG_RADIUS = 30.dp

    /** 菜单圆角 */
    val MENU_RADIUS = 20.dp

    /* ---------- 反光层 ---------- */

    /** 顶部柔光带峰值不透明度（压低，避免玻璃整体发白） */
    val GLOSS_ALPHA = 0.50f

    /** 顶部柔光带的衰减跨度（越小越集中） */
    val GLOSS_SPREAD = 0.26f

    /** 底部内阴影强度（压出厚度，但比之前收敛以免显脏） */
    val SHADE_ALPHA = 0.16f

    /** 左上角镜面光斑强度 */
    val SPECULAR_ALPHA = 0.42f

    /** 镜面光斑半径占比（缩小 → 更集中） */
    val SPECULAR_RADIUS = 0.60f

    /* ---------- 按钮 ---------- */

    /** 主/次按钮圆角（更圆润） */
    val BUTTON_RADIUS = 20.dp

    /** 主按钮渐变：顶部提亮幅度 */
    val BTN_TOP_LIGHTEN = 0.30f

    /** 主按钮渐变：底部压暗幅度 */
    val BTN_BOTTOM_DARKEN = 0.16f

    /** 次按钮底色（半透明白玻璃） */
    val OUTLINE_TOP_ALPHA = 0.46f
    val OUTLINE_BOTTOM_ALPHA = 0.18f

    /** 文字按钮底色 */
    val TEXT_TOP_ALPHA = 0.42f
    val TEXT_BOTTOM_ALPHA = 0.12f

    /** 图标按钮底色 */
    val ICON_TOP_ALPHA = 0.52f
    val ICON_BOTTOM_ALPHA = 0.18f

    /** 按钮高光描边不透明度 */
    val BTN_EDGE_ALPHA = 0.70f

    /* ---------- 输入框 ---------- */

    /** 输入框聚焦 / 未聚焦底色 */
    val FIELD_FOCUSED_ALPHA = 0.58f
    val FIELD_UNFOCUSED_ALPHA = 0.36f

    /** 输入框圆角 */
    val FIELD_RADIUS = 16.dp

    /* ---------- 液态玻璃新增：折射 / 色散 / 扫光 ---------- */

    /** 色散边缘（彩虹圈）宽度 */
    val RIM_WIDTH = 1.8.dp

    /** 色散边缘整体不透明度（越高越像真实镜片折射） */
    val DISPERSION_ALPHA = 0.80f

    /** 中心透镜高光强度 */
    val LENS_ALPHA = 0.20f

    /** 透镜边缘压深强度 */
    val WARP_ALPHA = 0.10f

    /** 斜向扫过镜面高光的峰值不透明度 */
    val SWEEP_ALPHA = 0.40f

    /** 表面涟漪强度 */
    val RIPPLE_ALPHA = 0.22f
}

/* ============================== 颜色工具 ============================== */

private fun Color.lighten(f: Float): Color = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha
)

private fun Color.darken(f: Float): Color = Color(
    red = red * (1f - f),
    green = green * (1f - f),
    blue = blue * (1f - f),
    alpha = alpha
)

/* ============================== 液态玻璃反光层 ============================== */

/**
 * 液态玻璃反光层：顶部柔光带 + 底部内阴影 + 中心透镜 warp + 周期扫过的镜面高光
 * + 表面涟漪 + 左上角点高光。
 *
 * 扫光进度从 [LocalLiquidSweep] 以 State 形式取得，并在 `onDrawBehind` 里读取
 * —— 只在 draw 阶段取值，动画每帧只重绘、不触发任何重组。
 */
@Composable
private fun Modifier.glassSheen(strength: Float = 1f): Modifier {
    val sweep = LocalLiquidSweep.current
    val palette = LocalLiquidPalette.current
    return drawWithCache {
        val topGloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = GlassStyle.GLOSS_ALPHA * strength),
            GlassStyle.GLOSS_SPREAD to Color.White.copy(alpha = 0.12f * strength),
            0.62f to Color.Transparent
        )
        val bottomShade = Brush.verticalGradient(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to Color(0xFF07314C).copy(alpha = GlassStyle.SHADE_ALPHA * strength)
        )
        val lens = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = GlassStyle.LENS_ALPHA * strength),
                Color.Transparent,
                palette.primary.copy(alpha = GlassStyle.WARP_ALPHA * strength)
            ),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.minDimension * 0.95f
        )
        onDrawBehind {
            val w = size.width
            val h = size.height
            val unit = size.minDimension
            val p = sweep.value

            drawRect(topGloss)
            drawRect(bottomShade)
            drawRect(lens)

            // 斜向扫过的镜面高光：液态玻璃「活着」的关键特征
            val sweepX = -w * 0.40f + p * w * 1.80f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = GlassStyle.SWEEP_ALPHA * strength),
                        Color.Transparent
                    ),
                    start = Offset(sweepX - w * 0.30f, 0f),
                    end = Offset(sweepX + w * 0.30f, h)
                )
            )

            // 表面涟漪：两圈随扫光扩散的极细弧线
            for (k in 0 until 2) {
                val progress = (p + k * 0.5f) % 1f
                drawCircle(
                    color = Color.White.copy(
                        alpha = GlassStyle.RIPPLE_ALPHA * (1f - progress) * strength
                    ),
                    radius = unit * (0.14f + progress * 0.66f),
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 1.1.dp.toPx())
                )
            }

            // 固定左上角点高光（保留原有玻璃质感）
            val r = unit * GlassStyle.SPECULAR_RADIUS
            val c = Offset(w * 0.28f, h * 0.01f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = GlassStyle.SPECULAR_ALPHA * strength),
                        Color.Transparent
                    ),
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )
        }
    }
}

/** 液态玻璃的彩虹色散边缘：外圈多色折射 + 内圈白色 rim。 */
@Composable
private fun Modifier.liquidRim(
    shape: Shape,
    width: Dp = GlassStyle.RIM_WIDTH,
    inner: Color = Color.White.copy(alpha = GlassStyle.PANEL_EDGE_ALPHA)
): Modifier {
    val palette = LocalLiquidPalette.current
    val rim = palette.dispersion.map { it.copy(alpha = it.alpha * GlassStyle.DISPERSION_ALPHA) }
    return this
        .border(width, Brush.linearGradient(rim + rim.asReversed()), shape)
        .border(width * 0.5f, inner, shape)
}

/* ============================== 玻璃面板 ============================== */

/**
 * 3D 玻璃面板：全 App 通用的立体玻璃容器（对话框、下拉菜单等复用）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassStyle.PANEL_RADIUS),
    topColor: Color = Color.White.copy(alpha = GlassStyle.PANEL_TOP_ALPHA),
    bottomColor: Color = Color(0xFFDCEFFB).copy(alpha = GlassStyle.PANEL_BOTTOM_ALPHA),
    edgeColor: Color = Color.White.copy(alpha = GlassStyle.PANEL_EDGE_ALPHA),
    shadowColor: Color = Color(0xFF1E9FD8),
    elevation: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = shadowColor.copy(alpha = 0.26f),
                spotColor = shadowColor.copy(alpha = 0.38f)
            )
            .clip(shape)
    ) {
        // 玻璃底色（竖向渐变，模拟厚度）+ 当前档位色相渗透，让 tint 随主题流动
        val palette = LocalLiquidPalette.current
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, palette.primary.copy(alpha = 0.12f))
                    )
                )
                .glassSheen()
        )
        // 液态玻璃边缘：彩虹色散外圈 + 白色 rim 内圈
        Box(Modifier.matchParentSize().liquidRim(shape, inner = edgeColor))
        content()
    }
}

/* ============================== 按钮 ============================== */

/**
 * 3D 玻璃主按钮：水蓝玻璃实体 + 顶部反光 + 底部压深 + 按压缩放。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GlassStyle.BUTTON_RADIUS),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    horizontalPadding: Dp = 22.dp,
    verticalPadding: Dp = 13.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.955f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassBtnScale"
    )
    val elev by animateFloatAsState(
        targetValue = if (pressed && enabled) 3f else 11f,
        animationSpec = tween(120),
        label = "glassBtnElev"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .shadow(
                elevation = elev.dp,
                shape = shape,
                ambientColor = containerColor.copy(alpha = 0.38f),
                spotColor = containerColor.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor.lighten(GlassStyle.BTN_TOP_LIGHTEN),
                        containerColor,
                        containerColor.darken(GlassStyle.BTN_BOTTOM_DARKEN)
                    )
                )
            )
            .glassSheen(0.9f)
            .liquidRim(shape, width = 1.4.dp, inner = Color.White.copy(alpha = GlassStyle.BTN_EDGE_ALPHA))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * 3D 玻璃次按钮（描边款）：半透明白玻璃 + 主色描边。
 */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GlassStyle.BUTTON_RADIUS),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    horizontalPadding: Dp = 22.dp,
    verticalPadding: Dp = 13.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassOutlineScale"
    )
    val elev by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 5f,
        animationSpec = tween(120),
        label = "glassOutlineElev"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .shadow(
                elevation = elev.dp,
                shape = shape,
                ambientColor = contentColor.copy(alpha = 0.16f),
                spotColor = contentColor.copy(alpha = 0.26f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = GlassStyle.OUTLINE_TOP_ALPHA),
                        Color.White.copy(alpha = GlassStyle.OUTLINE_BOTTOM_ALPHA)
                    )
                )
            )
            .glassSheen(0.75f)
            .liquidRim(shape, width = 1.3.dp, inner = contentColor.copy(alpha = 0.55f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * 3D 玻璃文字按钮（胶囊形）：用于「返回 / 取消 / 跳过」等轻量操作。
 */
@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(999.dp),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassTextScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = contentColor.copy(alpha = 0.12f),
                spotColor = contentColor.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = GlassStyle.TEXT_TOP_ALPHA),
                        Color.White.copy(alpha = GlassStyle.TEXT_BOTTOM_ALPHA)
                    )
                )
            )
            .glassSheen(0.6f)
            .liquidRim(shape, width = 1.dp, inner = Color.White.copy(alpha = 0.78f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * 3D 玻璃图标按钮（圆形）：用于顶部栏的 ⚙ 等小图标入口。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassIconScale"
    )

    // 默认半透明白玻璃；提供 containerColor 时改用该色渐变玻璃（如主色发送按钮）
    val (topFill, bottomFill, edgeFill, ambientFill, spotFill) = if (containerColor != null) {
        val c = containerColor
        listOf(
            c.lighten(GlassStyle.BTN_TOP_LIGHTEN),
            c.darken(GlassStyle.BTN_BOTTOM_DARKEN),
            Color.White.copy(alpha = GlassStyle.BTN_EDGE_ALPHA),
            c.copy(alpha = 0.38f),
            c.copy(alpha = 0.55f)
        )
    } else {
        listOf(
            Color.White.copy(alpha = GlassStyle.ICON_TOP_ALPHA),
            Color.White.copy(alpha = GlassStyle.ICON_BOTTOM_ALPHA),
            Color.White.copy(alpha = 0.82f),
            contentColor.copy(alpha = 0.16f),
            contentColor.copy(alpha = 0.26f)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .shadow(
                elevation = if (containerColor != null) 8.dp else 6.dp,
                shape = shape,
                ambientColor = ambientFill,
                spotColor = spotFill
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(topFill, bottomFill))
            )
            .glassSheen(if (containerColor != null) 0.9f else 0.8f)
            .liquidRim(shape, width = 1.2.dp, inner = edgeFill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/* ============================== 对话框 ============================== */

/**
 * 3D 玻璃对话框：替代 Material3 的 [androidx.compose.material3.AlertDialog]。
 * 容器为立体玻璃面板，整体带深蓝半透明遮罩，点击遮罩关闭。
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 全屏深蓝半透明遮罩（点击关闭）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF06283D).copy(alpha = 0.30f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    )
            )
            // 居中的立体玻璃面板（吞掉内部点击，避免穿透到遮罩导致误关闭）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        ),
                    shape = RoundedCornerShape(GlassStyle.DIALOG_RADIUS),
                    elevation = 24.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        if (title != null) {
                            title()
                            Spacer(Modifier.height(14.dp))
                        }
                        if (text != null) {
                            text()
                            Spacer(Modifier.height(22.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                Spacer(Modifier.width(10.dp))
                            }
                            if (confirmButton != null) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================== 下拉菜单 ============================== */

private object GlassMenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom)
}

/**
 * 3D 玻璃下拉菜单：替代 Material3 的 DropdownMenu，容器为立体玻璃面板。
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    Popup(
        popupPositionProvider = GlassMenuPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        GlassSurface(
            modifier = modifier.widthIn(min = 190.dp),
            shape = RoundedCornerShape(GlassStyle.MENU_RADIUS),
            elevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

/** 玻璃菜单项 */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(text, fontSize = 14.sp, color = scheme.onBackground)
    }
}

/* ============================== 输入框 ============================== */

/**
 * 3D 玻璃输入框：半透明玻璃底 + 主色描边 + 圆角，与玻璃按钮/面板视觉统一。
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(GlassStyle.FIELD_RADIUS),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = GlassStyle.FIELD_FOCUSED_ALPHA),
            unfocusedContainerColor = Color.White.copy(alpha = GlassStyle.FIELD_UNFOCUSED_ALPHA),
            disabledContainerColor = Color.White.copy(alpha = 0.22f),
            focusedBorderColor = scheme.primary.copy(alpha = 0.80f),
            unfocusedBorderColor = scheme.primary.copy(alpha = 0.30f),
            disabledBorderColor = scheme.primary.copy(alpha = 0.16f),
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.secondary,
            focusedTextColor = scheme.onBackground,
            unfocusedTextColor = scheme.onBackground,
            focusedPlaceholderColor = scheme.secondary.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = scheme.secondary.copy(alpha = 0.6f),
            cursorColor = scheme.primary
        )
    )
}
