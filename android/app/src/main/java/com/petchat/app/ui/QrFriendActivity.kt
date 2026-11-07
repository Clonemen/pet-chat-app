package com.petchat.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.petchat.app.data.ApiClient
import com.petchat.app.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫码添加好友。
 *
 * 两个模式（顶部无切换，用底部按钮互切）：
 * - 扫码（默认）：打开后置相机，用 ML Kit 识别二维码；识别到本 App 的
 *   `petchat://add` 载荷后，弹出确认框，确认即调用后端 addFriend 添加好友。
 * - 我的二维码：用 ZXing 生成本人好友二维码（uid/username/displayName），
 *   供好友扫描。
 *
 * 二维码载荷格式：petchat://add?uid=<userId>&u=<username>&n=<displayName>
 * （也兼容纯文本当作用户名直接查找）。
 */
class QrFriendActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode" // "scan"（默认）或 "mine"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionStore(this)
        val token = session.token ?: ""
        val serverBase = session.serverBase ?: ""
        val startMine = intent.getStringExtra(EXTRA_MODE) == "mine"

        setContent {
            PetChatTheme {
                QrFriendScreen(
                    activity = this,
                    session = session,
                    token = token,
                    serverBase = serverBase,
                    startMine = startMine,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun QrFriendScreen(
    activity: ComponentActivity,
    session: SessionStore,
    token: String,
    serverBase: String,
    startMine: Boolean,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(if (startMine) "mine" else "scan") }

    WaterBackground {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏：返回 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                GlassIconButton(onClick = onBack) { Text("‹", fontSize = 22.sp) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (mode == "scan") "扫一扫加好友" else "我的二维码",
                    fontSize = 18.sp,
                    color = scheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp)) // 与左侧返回键对称
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (mode) {
                    "scan" -> ScanScreen(
                        activity = activity,
                        token = token,
                        serverBase = serverBase,
                        onShowMine = { mode = "mine" },
                        onAdded = { activity.finish() }
                    )
                    "mine" -> MyQrCard(
                        session = session,
                        onScan = { mode = "scan" }
                    )
                }
            }
        }
    }
}

/** 我的好友二维码：ZXing 生成，显示在玻璃卡片中 */
@Composable
private fun MyQrCard(session: SessionStore, onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val uid = session.userId ?: ""
    val username = session.username ?: ""
    val displayName = (session.displayName ?: username).ifBlank { username }

    val bitmap = remember(uid, username, displayName) {
        generateQrBitmap(QrFriendCode.encode(uid, username, displayName), 560)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("我的好友二维码", fontSize = 20.sp, color = scheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "让好友扫描下方二维码，即可快速添加你为好友",
            fontSize = 13.sp,
            color = scheme.secondary
        )
        Spacer(Modifier.height(22.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "我的好友二维码",
                    modifier = Modifier
                        .size(220.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    username.ifBlank { "未登录" },
                    fontSize = 16.sp,
                    color = scheme.onBackground
                )
                if (displayName.isNotBlank() && displayName != username) {
                    Spacer(Modifier.height(4.dp))
                    Text(displayName, fontSize = 13.sp, color = scheme.secondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassButton(onClick = onScan) { Text("📷 扫一扫加好友") }
    }
}

/** 相机扫码界面：CameraX 预览 + ML Kit 条码识别 */
@Composable
private fun ScanScreen(
    activity: ComponentActivity,
    token: String,
    serverBase: String,
    onShowMine: () -> Unit,
    onAdded: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val controller = remember { ScanController() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCam = granted }

    LaunchedEffect(Unit) { if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize()) {
        if (hasCam) {
            CameraPreview(controller = controller)

            // 扫码取景框
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(220.dp)
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.9f),
                            RoundedCornerShape(16.dp)
                        )
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要相机权限才能扫码", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                GlassButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }

        // 底部切换到"我的二维码"
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            GlassButton(
                onClick = onShowMine,
                modifier = Modifier.padding(bottom = 30.dp)
            ) { Text("我的二维码 ›") }
        }
    }

    // 识别到二维码 -> 确认添加好友
    val payload = controller.pending.value
    if (payload != null) {
        GlassAlertDialog(
            onDismissRequest = { if (!busy) controller.reset() },
            confirmButton = {
                GlassButton(
                    onClick = {
                        if (busy) return@GlassButton
                        busy = true
                        error = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val api = ApiClient(serverBase)
                                val uid = if (payload.uid != null) {
                                    payload.uid
                                } else {
                                    api.findUser(token, payload.username)?.id
                                }
                                if (uid.isNullOrBlank()) {
                                    withContext(Dispatchers.Main) {
                                        error = "找不到该用户"
                                        busy = false
                                    }
                                } else if (!api.addFriend(token, uid)) {
                                    withContext(Dispatchers.Main) {
                                        error = "添加失败（可能已是好友）"
                                        busy = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        busy = false
                                        onAdded()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    error = e.message ?: "添加出错"
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    horizontalPadding = 26.dp,
                    verticalPadding = 11.dp
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("添加")
                    }
                }
            },
            dismissButton = {
                GlassTextButton(onClick = { controller.reset() }, enabled = !busy) {
                    Text("取消")
                }
            },
            title = {
                Text("添加好友", fontSize = 20.sp, color = scheme.onBackground)
            },
            text = {
                Column {
                    Text(
                        "是否添加「${payload.displayName.ifBlank { payload.username }}」为好友？",
                        fontSize = 14.sp,
                        color = scheme.onBackground
                    )
                    if (error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(error ?: "", color = Color(0xFFFF7A7A), fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

/** CameraX 预览 + 图像分析（ML Kit 识别） */
@Composable
private fun CameraPreview(controller: ScanController) {
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val scanner = BarcodeScanning.getClient()
                    val analyzer = BarcodeAnalyzer(scanner, controller)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(1280, 720))
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)
                        }
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        ctx as LifecycleOwner,
                        selector,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val controller: ScanController
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                for (b in barcodes) {
                    val raw = b.rawValue
                    if (!raw.isNullOrBlank()) {
                        controller.submit(raw)
                        break
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** 扫码状态：保证一次识别只弹一次确认框；支持重置以继续扫描 */
private class ScanController {
    val pending = mutableStateOf<QrPayload?>(null)
    @Volatile var handled = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun submit(content: String) {
        if (handled) return
        handled = true
        mainHandler.post { pending.value = QrFriendCode.parse(content) }
    }

    fun reset() {
        handled = false
        pending.value = null
    }
}

private data class QrPayload(
    val uid: String?,
    val username: String,
    val displayName: String
)

/** 二维码载荷编解码：petchat://add?uid=&u=&n= */
private object QrFriendCode {
    fun encode(userId: String, username: String, displayName: String): String =
        Uri.Builder()
            .scheme("petchat")
            .authority("add")
            .appendQueryParameter("uid", userId)
            .appendQueryParameter("u", username)
            .appendQueryParameter("n", displayName)
            .build()
            .toString()

    fun parse(content: String): QrPayload? {
        val c = content.trim()
        if (c.startsWith("petchat://add", ignoreCase = true)) {
            val uri = Uri.parse(c)
            val uid = uri.getQueryParameter("uid")?.takeIf { it.isNotBlank() } ?: return null
            val username = uri.getQueryParameter("u") ?: ""
            val displayName = (uri.getQueryParameter("n") ?: username).ifBlank { username }
            return QrPayload(uid, username, displayName)
        }
        // 兜底：纯文本当作用户名
        if (c.isNotBlank()) return QrPayload(null, c, c)
        return null
    }
}

/** 用 ZXing 生成二维码 Bitmap（白底黑点，便于各背景扫描） */
private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = hashMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )
    val matrix: BitMatrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints
    )
    val w = matrix.width
    val h = matrix.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) {
        for (x in 0 until w) {
            bmp.setPixel(
                x, y,
                if (matrix.get(x, y)) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            )
        }
    }
    return bmp
}
