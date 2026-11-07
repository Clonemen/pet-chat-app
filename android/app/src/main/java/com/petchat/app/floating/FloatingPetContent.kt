package com.petchat.app.floating

import com.petchat.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petchat.app.data.PetPresets
import com.petchat.app.model.ActionType
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 桌面悬浮宠物的 Compose 内容（架构"桌面层"的视图）。
 *
 * 视觉：**完全透明**——宠物贴图（透明背景 PNG）直接叠在桌面上，
 * 只有"接地阴影"和小名牌，无卡片、无边框、无底色，呈"宠物趴在桌面"效果。
 *
 * 布局：快捷菜单（长按出现，上） / 反应气泡（上） / 宠物形象+阴影（中） / 宠物名（下）。
 *
 * 手势：
 * - 拖动：移动悬浮窗（onDrag 回调更新 WindowManager 坐标）
 * - 单击：戳一戳，按当前人设随机反应 + 气泡
 * - 双击：打招呼（人设专属台词）
 * - 长按：弹出/收起快捷菜单（关闭悬浮窗）
 *
 * 动画：PetMotion 引擎驱动——呼吸常驻 + 眨眼自发 + 随机小动作（歪头/摇尾/
 * 跳跃/打盹/伸懒腰/抖毛/张望/挥手），反应动作由 ActionType 映射触发。
 * 形象优先级：内置 3D 预设（petResId） > AI 生成图（imageUrl） > 吉祥物占位。
 */

@Composable
fun FloatingPetContent(
    state: PetOverlayState,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClose: () -> Unit
) {
    val action by state.action
    val bubble by state.bubbleText
    val name by state.petName
    val imageUrl by state.petImageUrl
    val presetRes by state.petResId
    val visitImage by state.visitImageUrl
    val visitRes by state.visitResId
    val visitName by state.visitName
    val inVisit by state.inVisit
    var showMenu by remember { mutableStateOf(false) }
    // 访客头像兜底：SVG data URI（Coil 无 SVG 解码器）直接视为不可用；加载失败也回退本地预设
    val visitImageUsable = visitImage?.takeUnless {
        it.contains("image/svg", ignoreCase = true)
    }
    var visitImageFailed by remember { mutableStateOf(false) }
    LaunchedEffect(visitImage) { visitImageFailed = false }

    // 动作播完自动回到 idle 并收起气泡（访客态除外——气泡由访客计时器收）
    LaunchedEffect(action, inVisit) {
        if (action != ActionType.IDLE && !inVisit) {
            kotlinx.coroutines.delay(1800)
            state.backToIdle()
        }
    }

    // 闲置生命感：每 30~60 秒按人设随机自言自语（仅在完全空闲时打扰）
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(30_000L, 60_000L))
            if (action == ActionType.IDLE && bubble == null && !inVisit) {
                val (a, t) = state.persona.idle.random()
                state.react(a, t)
            }
        }
    }

    // 当前形象物种（驱动眨眼眼皮位置；AI 生成形象无标定则不画眼皮）
    val displayedRes = visitRes ?: presetRes
    val species = PetPresets.speciesOfRes(displayedRes)
        ?.takeIf { visitRes != null || presetRes != null }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 快捷菜单（长按宠物出现）
        AnimatedVisibility(visible = showMenu) {
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(Color(0xCC171A21), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(12.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MenuChip(label = "收起菜单") { showMenu = false }
                MenuChip(label = "关闭宠物", highlight = true) {
                    showMenu = false
                    onClose()
                }
            }
        }

        // 反应气泡
        AnimatedVisibility(visible = bubble != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .widthIn(max = 220.dp)
                    .background(Color(0xCC171A21), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x552A2F3A), RoundedCornerShape(12.dp))
                    .padding(8.dp, 5.dp)
            ) {
                Text(
                    text = bubble ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 宠物形象：拖动 / 单击 / 双击 / 长按（触摸区 = 阴影 + 本体 + 名牌，其余透明不挡桌面）
        PetMotion(action = action, inVisit = inVisit, species = species) {
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 128.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val fid = state.visitFriendId
                                if (inVisit && fid != null) {
                                    // 访客态：单击直接打开与该好友的聊天
                                    state.onVisitClick?.invoke(fid, visitName ?: "好友")
                                } else {
                                    val (a, t) = state.persona.poke.random()
                                    state.react(a, t)
                                }
                            },
                            onDoubleTap = {
                                state.react(ActionType.GREETING, state.persona.greeting)
                            },
                            onLongPress = { showMenu = !showMenu }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                when {
                    // 访客态：好友的宠物临时"来访"（URL 优先，其次按物种借本地 3D 预设）
                    visitImageUsable != null && !visitImageFailed -> {
                        AsyncImage(
                            model = visitImageUsable,
                            contentDescription = visitName ?: name,
                            modifier = Modifier.size(width = 108.dp, height = 108.dp),
                            contentScale = ContentScale.Fit,
                            onError = { visitImageFailed = true }
                        )
                    }
                    visitRes != null -> {
                        Image(
                            painter = painterResource(visitRes!!),
                            contentDescription = visitName ?: name,
                            modifier = Modifier.size(width = 108.dp, height = 108.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    presetRes != null -> {
                        // 内置 3D 预设形象（透明背景，Pixar 风毛绒质感，趴在桌面上）
                        Image(
                            painter = painterResource(presetRes!!),
                            contentDescription = name,
                            modifier = Modifier.size(width = 108.dp, height = 108.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    imageUrl != null -> {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = name,
                            modifier = Modifier.size(width = 108.dp, height = 108.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        // 占位（未取到图时，使用主题吉祥物矢量图）
                        Image(
                            painter = painterResource(R.drawable.ic_mascot),
                            contentDescription = name,
                            modifier = Modifier.size(96.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // 宠物名标签（访客态显示对方的宠物名）
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(
                    if (inVisit) Color(0xB32A3A55) else Color(0xB3171A21),
                    RoundedCornerShape(999.dp)
                )
                .padding(8.dp, 2.dp)
        ) {
            Text(
                text = if (inVisit) "${visitName ?: "好友"} · 来访" else name,
                color = if (inVisit) Color(0xFF9FC2FF) else Color(0xFF9AA3B2),
                fontSize = 10.sp
            )
        }
    }
}

/** 快捷菜单里的小按钮 */
@Composable
private fun MenuChip(label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Color(0xFF3A2A2A) else Color(0xFF22262F))
            .border(
                1.dp,
                if (highlight) Color(0xFF6A3A3A) else Color(0xFF2A2F3A),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(10.dp, 5.dp)
    ) {
        Text(
            text = label,
            color = if (highlight) Color(0xFFFF8A8A) else Color(0xFFC5CBD6),
            fontSize = 11.sp
        )
    }
}
