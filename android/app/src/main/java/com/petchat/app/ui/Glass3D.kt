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
 *  3D 玻璃组件库（Glass 3D Kit）
 * ----------------------------------------------------------------------------
 *  在「透明水纹式」主题之上，统一所有按钮 / 对话框 / 菜单 / 输入框的立体玻璃质感：
 *   · 玻璃底     —— 竖向渐变（顶部亮、底部沉），模拟玻璃厚度与透光
 *   · 顶部反光   —— 白色柔光层 + 左上角镜面光斑（drawWithCache）
 *   · 底部内阴影 —— 底部压深，制造「厚度」与立体感
 *   · 玻璃边缘   —— 1~1.4dp 高光描边
 *   · 立体投影   —— 带主色色相的柔和外阴影（spot / ambient）
 *   · 按压反馈   —— 按下时缩放 + 投影收缩，形成物理按压手感
 *
 *  所有视觉参数集中在 [GlassStyle]，改一处即可全 App 统一调档。
 * ============================================================================
 */

/* ============================== 全局质感参数 ============================== */

/**
 * 全局玻璃质感参数（通透度 / 反光 / 圆角）。
 *
 * 当前档位：**更通透 + 反光更聚拢 + 更圆润**
 *  - 通透：面板/按钮底色不透明度整体下调约 15%，背景水纹能明显透出；
 *  - 反光：顶部柔光带收窄变亮、镜面光斑缩小提亮（更像真玻璃的"点高光"而非"整体发白"）；
 *  - 圆润：按钮与面板圆角统一上调，轮廓更软萌。
 */
object GlassStyle {

    /* ---------- 面板（对话框 / 菜单 / 卡片底） ---------- */

    /** 面板顶部玻璃不透明度（越低越通透） */
    val PANEL_TOP_ALPHA = 0.72f

    /** 面板底部玻璃不透明度 */
    val PANEL_BOTTOM_ALPHA = 0.40f

    /** 面板高光描边不透明度 */
    val PANEL_EDGE_ALPHA = 0.95f

    /** 面板圆角 */
    val PANEL_RADIUS = 26.dp

    /** 对话框圆角（比一般面板更圆润） */
    val DIALOG_RADIUS = 30.dp

    /** 菜单圆角 */
    val MENU_RADIUS = 20.dp

    /* ---------- 反光层 ---------- */

    /** 顶部柔光带峰值不透明度（收窄但更亮 → 更像"点高光"） */
    val GLOSS_ALPHA = 0.62f

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
    val OUTLINE_TOP_ALPHA = 0.60f
    val OUTLINE_BOTTOM_ALPHA = 0.26f

    /** 文字按钮底色 */
    val TEXT_TOP_ALPHA = 0.52f
    val TEXT_BOTTOM_ALPHA = 0.18f

    /** 图标按钮底色 */
    val ICON_TOP_ALPHA = 0.66f
    val ICON_BOTTOM_ALPHA = 0.26f

    /** 按钮高光描边不透明度 */
    val BTN_EDGE_ALPHA = 0.70f

    /* ---------- 输入框 ---------- */

    /** 输入框聚焦 / 未聚焦底色 */
    val FIELD_FOCUSED_ALPHA = 0.58f
    val FIELD_UNFOCUSED_ALPHA = 0.36f

    /** 输入框圆角 */
    val FIELD_RADIUS = 16.dp
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

/* ============================== 玻璃反光层 ============================== */

/**
 * 玻璃反光层：顶部柔光带 + 底部内阴影 + 左上角镜面光斑。
 * 叠加在玻璃底色之上，塑造「有厚度的透明玻璃」立体感。
 *
 * 相比初版：柔光带更窄更亮、镜面光斑更小更集中、底部阴影更浅 → 通透而不发白。
 */
private fun Modifier.glassSheen(strength: Float = 1f): Modifier = drawWithCache {
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
    onDrawBehind {
        drawRect(topGloss)
        drawRect(bottomShade)
        val r = size.minDimension * GlassStyle.SPECULAR_RADIUS
        val c = Offset(size.width * 0.28f, size.height * 0.01f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = GlassStyle.SPECULAR_ALPHA * strength), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
    }
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
        // 玻璃底色（竖向渐变，模拟厚度）
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .glassSheen()
        )
        // 玻璃高光边缘
        Box(Modifier.matchParentSize().border(1.2.dp, edgeColor, shape))
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
            .border(1.4.dp, Color.White.copy(alpha = GlassStyle.BTN_EDGE_ALPHA), shape)
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
            .border(1.3.dp, contentColor.copy(alpha = 0.55f), shape)
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
            .border(1.dp, Color.White.copy(alpha = 0.78f), shape)
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
            .border(1.2.dp, edgeFill, shape)
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
