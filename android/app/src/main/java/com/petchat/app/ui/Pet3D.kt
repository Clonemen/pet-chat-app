package com.petchat.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/*
 * ============================================================================
 *  宠物 3D 形象渲染（Liquid 3D Pets）
 * ----------------------------------------------------------------------------
 *  模型来源：由「图片 → 3D」生成服务把原本的 2D 预设图转成带 PBR 材质的 GLB，
 *           经 tools/optimize_glb.py 压缩贴图后放在 assets/pet3d/models/。
 *  渲染方案：GitHub  google/model-viewer（BSD-3-Clause，运行时内置于 assets），
 *           通过 WebView 加载。相比 SceneView/Filament：
 *             · 零 Gradle 依赖，不影响项目 Kotlin 1.9.24 / Compose 1.6 版本链；
 *             · GLB/glTF 原生支持，自动 PBR 光照、阴影、拖拽旋转与自动旋转；
 *             · 完全离线：shouldInterceptRequest 把同源 URL 映射到本地 assets，
 *               不发起任何网络请求，也不受 file:// 下 fetch 被禁的限制。
 *  兜底策略：模型缺失、WebView 过旧或加载失败时，自动回落到原有 2D 图片，
 *           保证低端机 / 老系统 WebView 不会出现白屏。
 * ============================================================================
 */

private const val PET3D_BASE = "https://petchat.local/"

/** 预设/物种 → assets 下的 GLB 文件名 */
object Pet3DAssets {
    private val byPreset = mapOf(
        "corgi" to "models/corgi.glb",
        "cat" to "models/cat.glb",
        "bunny" to "models/bunny.glb",
        "panda" to "models/panda.glb"
    )
    private val speciesToPreset = mapOf(
        "dog" to "corgi", "cat" to "cat", "rabbit" to "bunny", "red_panda" to "panda"
    )

    /** 内置预设优先；AI 生成形象按物种借用同款 3D 模型 */
    fun of(presetId: String?, species: String?): String? =
        byPreset[presetId] ?: byPreset[speciesToPreset[species]]

    fun exists(context: Context, asset: String?): Boolean {
        if (asset == null) return false
        return try {
            context.assets.open("pet3d/$asset").close()
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** 把 https://petchat.local/xxx 的请求映射到 assets/pet3d/xxx，实现纯离线加载 */
private class Pet3DAssetClient(private val context: Context) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        if (!url.startsWith(PET3D_BASE)) return null
        val rel = url.removePrefix(PET3D_BASE).substringBefore('?').substringBefore('#')
        if (rel.isBlank() || rel.contains("..")) return null
        val mime = when {
            rel.endsWith(".js") -> "application/javascript"
            rel.endsWith(".glb") -> "model/gltf-binary"
            rel.endsWith(".gltf") -> "model/gltf+json"
            rel.endsWith(".html") -> "text/html"
            rel.endsWith(".png") -> "image/png"
            rel.endsWith(".jpg") || rel.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
        return try {
            WebResourceResponse(mime, "UTF-8", context.assets.open("pet3d/$rel")).apply {
                responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
            }.also { Log.d("Pet3D", "asset ok: $rel ($mime)") }
        } catch (e: Exception) {
            Log.w("Pet3D", "asset missing: $rel", e)
            null
        }
    }
}

private class Pet3DBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var readyReported = false

    @Volatile
    private var errorReported = false

    @JavascriptInterface
    fun onReady() {
        if (readyReported) return
        readyReported = true
        Log.d("Pet3D", "model loaded & rendered")
        main.post { onReady() }
    }

    @JavascriptInterface
    fun onError(message: String?) {
        if (errorReported) return
        errorReported = true
        Log.w("Pet3D", "model failed: ${message ?: "unknown"}")
        main.post { onError(message ?: "unknown") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildPetWebView(
    context: Context,
    modelAsset: String,
    autoRotate: Boolean,
    bridge: Pet3DBridge
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    setBackgroundColor(AndroidColor.TRANSPARENT)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    isHorizontalScrollBarEnabled = false
    isVerticalScrollBarEnabled = false
    webViewClient = Pet3DAssetClient(context)
    addJavascriptInterface(bridge, "Pet3DBridge")
    loadDataWithBaseURL(PET3D_BASE, pet3dHtml(modelAsset, autoRotate), "text/html", "UTF-8", null)
}

private fun pet3dHtml(modelAsset: String, autoRotate: Boolean): String {
    val rotate = if (autoRotate) {
        "auto-rotate auto-rotate-delay=\"0\" rotation-per-second=\"16deg\""
    } else ""
    return """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
  html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden}
  model-viewer{display:block;width:100%;height:100%;background:transparent;
    --poster-color:transparent;--progress-bar-color:transparent;--progress-mask:transparent}
</style>
<script>
/* 老 WebView（Chromium 75）兼容补丁：补齐缺失的 ES API 与半实现的 WebGL 扩展 */
(function(){
  if(!Promise.allSettled){Promise.allSettled=function(xs){return Promise.all(Array.prototype.map.call(xs,function(p){return Promise.resolve(p).then(function(v){return {status:'fulfilled',value:v};},function(e){return {status:'rejected',reason:e};});}));};}
  if(!String.prototype.replaceAll){String.prototype.replaceAll=function(a,b){return this.split(a).join(b);};}
  [window.WebGLRenderingContext,window.WebGL2RenderingContext].forEach(function(Ctor){
    if(!Ctor||!Ctor.prototype)return;
    var orig=Ctor.prototype.getExtension;
    Ctor.prototype.getExtension=function(name){
      var ext=orig.call(this,name);
      if(ext&&name==='WEBGL_compressed_texture_astc'&&typeof ext.getSupportedProfiles!=='function'){
        try{ext.getSupportedProfiles=function(){return [];};}catch(e){}
      }
      return ext;
    };
  });
})();
</script>
<script type="module" src="model-viewer.min.js"></script>
</head><body>
<model-viewer id="pet" src="$modelAsset" alt="3D 宠物形象"
  camera-controls $rotate
  shadow-intensity="0.55" shadow-softness="0.9" exposure="1.1"
  environment-image="neutral" camera-orbit="0deg 78deg 110%"
  min-camera-orbit="auto 40deg 75%" max-camera-orbit="auto 100deg 190%"
  interaction-prompt="none" disable-pan loading="eager">
</model-viewer>
<script>
(function(){
  var mv=document.getElementById('pet'), done=false;
  function ready(){ if(done) return; done=true; window.Pet3DBridge && Pet3DBridge.onReady(); }
  function fail(m){ if(done) return; done=true; window.Pet3DBridge && Pet3DBridge.onError(String(m)); }
  mv.addEventListener('load', ready);
  mv.addEventListener('error', function(e){ fail(e && e.detail ? e.detail : 'model load error'); });
  window.addEventListener('error', function(e){ fail(e && e.message ? e.message : 'script error'); });
  setTimeout(function(){ fail('timeout'); }, 20000);
})();
</script>
</body></html>
""".trimIndent()
}

/**
 * 可交互的 3D 宠物视图。
 *
 * @param modelAsset assets/pet3d/ 下的相对路径（如 models/corgi.glb）
 * @param fallback   3D 不可用 / 加载中显示的 2D 内容
 */
@Composable
fun Pet3DView(
    modelAsset: String?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    autoRotate: Boolean = true
) {
    val context = LocalContext.current
    val available = remember(modelAsset) { Pet3DAssets.exists(context, modelAsset) }

    if (modelAsset == null || !available) {
        fallback()
        return
    }

    Box(modifier = modifier) {
        // modelAsset 变化时整棵子树重建（销毁旧 WebView、重新加载新模型）
        key(modelAsset) {
            var ready by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            var webView by remember { mutableStateOf<WebView?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    webView?.destroy()
                    webView = null
                }
            }

            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    val bridge = Pet3DBridge(
                        onReady = { ready = true; failed = false },
                        onError = { failed = true }
                    )
                    buildPetWebView(ctx, modelAsset, autoRotate, bridge).also { webView = it }
                }
            )
            // 加载完成前、或 3D 不可用时显示 2D 形象，避免白屏
            if (!ready || failed) {
                fallback()
            }
        }
    }
}
