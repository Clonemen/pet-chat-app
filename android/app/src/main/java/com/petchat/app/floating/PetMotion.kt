package com.petchat.app.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petchat.app.model.ActionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 桌面宠物动作种类（架构"宠物引擎"的动作库）。
 * 每个 kind 对应一段编排好的关键帧动画，由 [PetMotionEngine.perform] 顺序播放。
 */
enum class PetMotionKind {
    NONE,       // 无动作（呼吸持续）
    BLINK,      // 眨眼
    TILT,       // 歪头
    WAG,        // 摇尾巴（开心快速摆动）
    JUMP,       // 跳跃（蹲-跳-落地回弹）
    NAP,        // 打盹（闭眼 + Zzz，醒来伸懒腰）
    STRETCH,    // 伸懒腰
    SHAKE,      // 抖毛（快速左右抖动）
    LOOK,       // 东张西望（左右转头张望）
    WAVE        // 挥手（抬起小爪子打招呼）
}

/** 物种眼睛位置（相对图片宽高的比例），用于眨眼/打盹的眼皮覆盖 */
data class PetEyeConfig(
    val leftEye: Pair<Float, Float>,
    val rightEye: Pair<Float, Float>,
    val eyeWidth: Float,   // 眼睛宽度（相对图宽）
    val yBias: Float = 0f  // 眼弧纵向偏移
)

/**
 * 宠物引擎：动作状态机 + 关键帧编排。
 *
 * - 呼吸：无限循环，永远叠加在最外层（持续存在感）
 * - 动作：互斥播放（Mutex），每段动画自带起手/收尾，播完所有数值归位
 * - 眨眼：独立高频随机触发，与其他动作自然叠加
 *
 * 全部数值型 Animatable 由 Compose graphicsLayer 消费，性能开销极低。
 */
class PetMotionEngine {
    val rotation = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val offsetX = Animatable(0f)   // 单位 dp
    val offsetY = Animatable(0f)   // 单位 dp（负 = 向上跳）
    val eyeClose = Animatable(0f)  // 0 睁眼 / 1 闭眼
    val shadow = Animatable(1f)    // 接地阴影缩放（跳起时缩小）

    /** 挥手小爪是否可见 */
    var pawVisible by mutableStateOf(false)
        private set
    /** 打盹 Zzz 是否可见 */
    var zzzVisible by mutableStateOf(false)
        private set

    private val mutex = Mutex()

    /**
     * 播放一段动作（互斥）。pxPerDp 用于把 dp 单位换算成 graphicsLayer 像素。
     */
    suspend fun perform(kind: PetMotionKind, pxPerDp: Float) = mutex.withLock {
        when (kind) {
            PetMotionKind.BLINK -> {
                eyeClose.animateTo(1f, tween(90))
                eyeClose.animateTo(0f, tween(140))
            }

            PetMotionKind.TILT -> {
                rotation.animateTo(13f, spring(dampingRatio = 0.45f, stiffness = 220f))
                delay(420)
                rotation.animateTo(-8f, tween(260))
                rotation.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
            }

            PetMotionKind.WAG -> {
                repeat(5) { i ->
                    val amp = 8f * (1f - i * 0.12f)
                    rotation.animateTo(amp, tween(90, easing = LinearEasing))
                    rotation.animateTo(-amp, tween(90, easing = LinearEasing))
                }
                rotation.animateTo(0f, tween(80))
            }

            PetMotionKind.JUMP -> {
                // 蓄力下蹲
                scaleX.animateTo(1.1f, tween(130))
                scaleY.animateTo(0.86f, tween(130))
                // 起跳（拉伸）
                scaleX.animateTo(0.9f, tween(110))
                scaleY.animateTo(1.14f, tween(110))
                shadow.animateTo(0.6f, tween(160))
                offsetY.animateTo(-34f, tween(200))
                offsetY.animateTo(0f, tween(170))
                shadow.animateTo(1f, tween(120))
                // 落地压扁回弹
                scaleX.animateTo(1.12f, tween(80))
                scaleY.animateTo(0.84f, tween(80))
                scaleX.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
                scaleY.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
                // 也许再来一小跳
                if (Random.nextBoolean()) {
                    offsetY.animateTo(-16f, tween(140))
                    offsetY.animateTo(0f, tween(130))
                    scaleX.animateTo(1.06f, tween(60))
                    scaleY.animateTo(0.92f, tween(60))
                    scaleX.animateTo(1f, tween(90))
                    scaleY.animateTo(1f, tween(90))
                }
            }

            PetMotionKind.NAP -> {
                // 趴下闭眼
                scaleY.animateTo(0.92f, tween(400))
                rotation.animateTo(-5f, tween(400))
                eyeClose.animateTo(1f, tween(300))
                zzzVisible = true
                delay(3200) // 打盹一会儿
                zzzVisible = false
                // 醒来伸懒腰
                eyeClose.animateTo(0f, tween(120))
                scaleX.animateTo(1.14f, tween(420))
                scaleY.animateTo(0.86f, tween(420))
                delay(300)
                scaleX.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
                scaleY.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
                rotation.animateTo(0f, tween(260))
            }

            PetMotionKind.STRETCH -> {
                scaleX.animateTo(1.13f, tween(450))
                scaleY.animateTo(0.88f, tween(450))
                delay(380)
                scaleX.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
                scaleY.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
            }

            PetMotionKind.SHAKE -> {
                repeat(8) {
                    offsetX.animateTo(3.5f, tween(34, easing = LinearEasing))
                    offsetX.animateTo(-3.5f, tween(34, easing = LinearEasing))
                }
                offsetX.animateTo(0f, tween(50))
                scaleX.animateTo(1.06f, tween(70))
                scaleY.animateTo(1.06f, tween(70))
                scaleX.animateTo(1f, tween(110))
                scaleY.animateTo(1f, tween(110))
            }

            PetMotionKind.LOOK -> {
                rotation.animateTo(4f, tween(200))
                offsetX.animateTo(8f, tween(240))
                delay(500)
                offsetX.animateTo(-8f, tween(320))
                rotation.animateTo(-4f, tween(300))
                delay(450)
                offsetX.animateTo(0f, tween(220))
                rotation.animateTo(0f, tween(200))
            }

            PetMotionKind.WAVE -> {
                pawVisible = true
                rotation.animateTo(6f, spring(dampingRatio = 0.5f, stiffness = 260f))
                delay(1300)
                rotation.animateTo(0f, tween(200))
                pawVisible = false
            }

            PetMotionKind.NONE -> { /* nothing */ }
        }
        // 兜底归位（防止动画序列被打断后残留偏移）
        if (kind != PetMotionKind.NAP) {
            rotation.animateTo(0f, tween(120))
        }
        scaleX.animateTo(1f, tween(120))
        scaleY.animateTo(1f, tween(120))
        offsetX.animateTo(0f, tween(120))
        offsetY.animateTo(0f, tween(120))
        shadow.animateTo(1f, tween(120))
    }

    companion object {
        /** 随机动作池（带权重：可爱的小动作更常见，打盹/跳跃较稀有） */
        fun randomKind(): PetMotionKind {
            val pool = listOf(
                PetMotionKind.TILT, PetMotionKind.TILT,
                PetMotionKind.WAG, PetMotionKind.WAG,
                PetMotionKind.LOOK,
                PetMotionKind.STRETCH,
                PetMotionKind.SHAKE,
                PetMotionKind.WAVE,
                PetMotionKind.JUMP,
                PetMotionKind.NAP
            )
            return pool.random()
        }

        /** ActionType → 对应动作（消息/戳一戳触发的反应动画） */
        fun fromAction(action: ActionType): PetMotionKind? = when (action) {
            ActionType.HAPPY -> PetMotionKind.WAG
            ActionType.GREETING -> PetMotionKind.WAVE
            ActionType.SURPRISED -> PetMotionKind.SHAKE
            ActionType.SAD -> PetMotionKind.TILT
            ActionType.THINKING -> PetMotionKind.LOOK
            ActionType.IDLE -> null
        }
    }
}

/** 预设形象对应的眼睛位置配置（按抠图后实际画框目测标定） */
fun eyeConfigForSpecies(species: String?): PetEyeConfig? = when (species) {
    "dog" -> PetEyeConfig(0.34f to 0.42f, 0.64f to 0.42f, 0.14f, yBias = 0.02f)
    "cat" -> PetEyeConfig(0.33f to 0.46f, 0.65f to 0.46f, 0.15f, yBias = 0f)
    "rabbit" -> PetEyeConfig(0.36f to 0.62f, 0.64f to 0.62f, 0.13f, yBias = 0.03f)
    "red_panda" -> PetEyeConfig(0.36f to 0.38f, 0.64f to 0.38f, 0.15f, yBias = 0.03f)
    else -> null
}

/**
 * 宠物引擎渲染层。
 *
 * - 呼吸永远叠加（infiniteTransition）
 * - 引擎的位移/旋转/缩放值经 graphicsLayer 应用到宠物贴图
 * - 闭眼眼皮由 Canvas 按物种眼睛位置绘制（眨眼/打盹共用）
 * - 地面椭圆阴影：跳起时缩小，营造"趴在桌面上"的接地感
 * - 打盹 Zzz / 挥手小爪 覆盖层
 *
 * @param motion 由随机调度器或 ActionType 反应触发
 * @param species 当前形象物种（决定眼皮位置；AI 生成形象传 null 则不画眼皮）
 */
@Composable
fun PetMotion(
    action: ActionType,
    inVisit: Boolean,
    species: String?,
    content: @Composable () -> Unit
) {
    val engine = remember { PetMotionEngine() }
    val densityPx = androidx.compose.ui.platform.LocalDensity.current.density
    val pxPerDp = densityPx

    val infinite = rememberInfiniteTransition(label = "idle")
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "breathe"
    )

    // ActionType 反应 → 对应动作（好友消息/戳一戳）
    LaunchedEffect(action) {
        val kind = PetMotionEngine.fromAction(action)
        if (kind != null) engine.perform(kind, pxPerDp)
    }

    // 随机动作调度器：空闲时每 7~16 秒随机来一个小动作，自然切换
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(7_000L, 16_000L))
            if (action == ActionType.IDLE && !inVisit) {
                engine.perform(PetMotionEngine.randomKind(), pxPerDp)
            }
        }
    }

    // 自发眨眼：每 2.5~6 秒一次，让宠物"活"起来
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2_500L, 6_000L))
            engine.perform(PetMotionKind.BLINK, pxPerDp)
        }
    }

    val eyeCfg = remember(species) { eyeConfigForSpecies(species) }

    Box(contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
        // ── 接地阴影：宠物"趴在桌面"的落影 ──
        Canvas(
            modifier = Modifier
                .offset { IntOffset(0, (-2 * pxPerDp).roundToInt()) }
                .size(width = 84.dp, height = 12.dp)
                .graphicsLayer {
                    scaleX = engine.shadow.value
                    alpha = 0.9f
                }
        ) {
            val r = size.height / 2f
            drawOval(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f),
                size = size,
                alpha = 0.8f
            )
            // 柔边：中心深、边缘浅
            drawOval(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(-r * 0.4f, -r * 0.35f),
                size = size.copy(width = size.width + r * 0.8f, height = size.height + r * 0.7f)
            )
        }

        // ── 宠物本体（含动作变换） ──
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = engine.offsetX.value * pxPerDp
                translationY = engine.offsetY.value * pxPerDp
                rotationZ = engine.rotation.value
                val s = breathe
                scaleX = engine.scaleX.value * s
                scaleY = engine.scaleY.value * s
                // 变换原点：底部中心（跳跃/压扁以脚底为轴，才像趴在地上）
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.92f)
            }
        ) {
            content()
        }

        // ── 闭眼眼皮覆盖（按物种标定位置） ──
        if (eyeCfg != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val close = engine.eyeClose.value
                if (close > 0.01f) {
                    val stroke = Stroke(width = size.width * 0.022f, cap = StrokeCap.Round)
                    val color = androidx.compose.ui.graphics.Color(0xFF3A2E28).copy(alpha = close)
                    fun drawEye(cx: Float, cy: Float, ew: Float) {
                        val halfW = size.width * ew / 2f
                        val y = size.height * cy + size.width * eyeCfg.yBias
                        // 闭眼：向下弯的温柔弧线 ‿
                        drawArc(
                            color = color,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(cx - halfW, y - halfW * 0.55f),
                            size = androidx.compose.ui.geometry.Size(halfW * 2f, halfW * 1.1f),
                            style = stroke
                        )
                    }
                    drawEye(size.width * eyeCfg.leftEye.first, size.height * eyeCfg.leftEye.second, eyeCfg.eyeWidth)
                    drawEye(size.width * eyeCfg.rightEye.first, size.height * eyeCfg.rightEye.second, eyeCfg.eyeWidth)
                }
            }
        }

        // ── 打盹 Zzz ──
        if (engine.zzzVisible) {
            NapZzz()
        }

        // ── 挥手小爪 ──
        if (engine.pawVisible) {
            WavePaw()
        }
    }
}

/** 打盹时飘起的 Zzz */
@Composable
private fun NapZzz() {
    val t = rememberInfiniteTransition(label = "zzz")
    val float by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "zzzFloat"
    )
    val phase = (float * 3f).toInt()
    Box {
        listOf("z", "z", "z").forEachIndexed { i, z ->
            val local = ((float * 3f) - i).coerceIn(0f, 1f)
            if (phase >= i) {
                Text(
                    text = z,
                    fontSize = (13 + i * 3).sp,
                    color = androidx.compose.ui.graphics.Color(0xFFBFD4FF).copy(alpha = 1f - local * 0.7f),
                    modifier = Modifier
                        .offset(x = (10 + i * 11).dp, y = (-6 - local * 22 - i * 4).dp)
                        .rotate(12f + i * 5f)
                )
            }
        }
    }
}

/** 挥手时摆动的小爪 */
@Composable
private fun WavePaw() {
    val t = rememberInfiniteTransition(label = "paw")
    val swing by t.animateFloat(
        initialValue = -22f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse),
        label = "pawSwing"
    )
    Text(
        text = "🐾",
        fontSize = 20.sp,
        modifier = Modifier
            .offset(x = 58.dp, y = 30.dp)
            .rotate(swing)
    )
}
