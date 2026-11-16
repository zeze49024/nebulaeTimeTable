package cn.nebulae.timetable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val LocalPage = "file:///android_asset/index.html"
private val Brand = Color(0xFFD71935)
private val Ink = Color(0xFF23262B)
private val Paper = Color(0xFFF5F6F8)
private val TabLabels = listOf("首页", "首末班", "到站提醒")
private val TabIcons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_schedule, R.drawable.ic_tab_arrivals)

class MainActivity : ComponentActivity() {
    private var web: WebView? = null
    private var pendingFile: ValueCallback<Array<Uri>>? = null
    private var page by mutableStateOf(PageLayout())
    private var webBackdrop by mutableStateOf<ImageBitmap?>(null)
    private var captureScheduled = false
    private var lastCaptureTime = 0L
    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingFile
        pendingFile = null
        if (callback != null) {
            callback.onReceiveValue(uri?.let { arrayOf(it) })
        } else if (uri != null) {
            lifecycleScope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }.getOrNull()
                }
                if (text == null) {
                    runPageScript("popup('导入失败：无法读取文件')")
                } else {
                    runPageScript("importRailText(" + JSONObject.quote(text) + ")")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(page.canGoBack) { runPageScript("back()") }
            val backdrop = rememberLayerBackdrop()
            val selectedTab = remember { { page.tab } }
            val fullGlass = Build.VERSION.SDK_INT >= 33
            Box(Modifier.fillMaxSize().background(Paper).safeDrawingPadding()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { makeWebView() },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = {
                            it.removeJavascriptInterface("RailAndroid")
                            it.destroy()
                            web = null
                        }
                    )
                    webBackdrop?.let { frame ->
                        Image(
                            frame,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize().alpha(0f).layerBackdrop(backdrop)
                        )
                    }
                    if (page.width > 0) {
                        val scale = maxWidth.value / page.width
                        page.controls.forEachIndexed { index, control ->
                            val surface = when {
                                control.kind == "primary" -> Brand.copy(alpha = if (fullGlass) 0.87f else 1f)
                                fullGlass -> Color.White.copy(alpha = 0.64f)
                                else -> Color.White
                            }
                            val position = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .offset(x = (control.x * scale).dp, y = (control.y * scale).dp)
                                .size((control.width * scale).dp, (control.height * scale).dp)
                            LiquidButton(
                                backdrop = backdrop,
                                onClick = { runPageScript("androidControl($index)") },
                                surfaceColor = surface,
                                horizontalContentPadding = if (control.kind == "entry") 8.dp else 3.dp,
                                modifier = position
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (control.kind == "entry") {
                                        val firstLast = control.text[0] == "◷"
                                        Box(
                                            Modifier.padding(bottom = 9.dp).size(49.dp).background(
                                                if (firstLast) Color(0xFFFFE9EE) else Color(0xFFE1F4F6),
                                                RoundedCornerShape(16.dp)
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            RailText(control.text[0], if (firstLast) Brand else Color(0xFF12A6BA), 24)
                                        }
                                        RailText(control.text[1], Ink, 16, FontWeight.Bold)
                                        RailText(control.text[2], Ink.copy(alpha = 0.68f), 11)
                                    } else {
                                        val label = if (control.kind == "back") "返回" else control.text[0]
                                        Box(Modifier.clearAndSetSemantics { contentDescription = label }) {
                                            RailText(control.text[0], if (control.kind == "primary") Color.White else Ink, control.font)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (page.width > 0) {
                        LiquidBottomTabs(
                            selectedTabIndex = selectedTab,
                            onTabSelected = { index -> runPageScript("androidTab($index)") },
                            backdrop = backdrop,
                            tabsCount = TabLabels.size,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).widthIn(max = 516.dp)
                        ) {
                            TabLabels.forEachIndexed { index, label ->
                                LiquidBottomTab(onClick = { runPageScript("androidTab($index)") }) {
                                    Image(
                                        painter = painterResource(TabIcons[index]),
                                        contentDescription = null,
                                        modifier = Modifier.size(23.dp)
                                    )
                                    RailText(label, Ink, 11)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun makeWebView() = WebView(this).apply {
        web = this
        setBackgroundColor(android.graphics.Color.rgb(245, 246, 248))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.blockNetworkLoads = true
        addJavascriptInterface(RailBridge(), "RailAndroid")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.toString() != LocalPage
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                pendingFile?.onReceiveValue(null)
                pendingFile = callback
                launchFilePicker()
                return true
            }
        }
        loadUrl(LocalPage)
    }

    private fun launchFilePicker() {
        openJson.launch(arrayOf("application/json", "text/plain"))
    }

    private fun runPageScript(script: String) {
        web?.evaluateJavascript(script, null)
    }

    // WebView interop is drawn outside Compose's graphics layer. Record its viewport
    // for Backdrop while keeping the actual WebView visible and interactive.
    private fun scheduleBackdrop() {
        val view = web ?: return
        if (Build.VERSION.SDK_INT < 33 || captureScheduled) return
        captureScheduled = true
        val delay = (48L - (SystemClock.uptimeMillis() - lastCaptureTime)).coerceAtLeast(0)
        view.postDelayed({
            captureScheduled = false
            if (web === view && view.width > 0 && view.height > 0) {
                val scale = minOf(1f, 540f / view.width)
                val bitmap = Bitmap.createBitmap(
                    (view.width * scale).toInt().coerceAtLeast(1),
                    (view.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                canvas.scale(scale, scale)
                canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                view.draw(canvas)
                webBackdrop = bitmap.asImageBitmap()
                lastCaptureTime = SystemClock.uptimeMillis()
            }
        }, delay)
    }

    private inner class RailBridge {
        @JavascriptInterface
        fun openImport() {
            runOnUiThread { launchFilePicker() }
        }

        @JavascriptInterface
        fun render(serialized: String) {
            val layout = runCatching { PageLayout.parse(serialized) }.getOrNull() ?: return
            runOnUiThread {
                page = layout
                scheduleBackdrop()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RailText(text: String, color: Color, size: Int, weight: FontWeight = FontWeight.Medium) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = TextAlign.Center))
}

private data class GlassControl(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val kind: String,
    val text: List<String>,
    val font: Int
)

private data class PageLayout(
    val width: Float = 0f,
    val tab: Int = 0,
    val canGoBack: Boolean = false,
    val controls: List<GlassControl> = emptyList()
) {
    companion object {
        fun parse(serialized: String): PageLayout {
            val root = JSONObject(serialized)
            val controls = root.getJSONArray("controls")
            return PageLayout(
                width = root.getDouble("width").toFloat(),
                tab = root.getInt("tab"),
                canGoBack = root.getInt("tab") != 0,
                controls = List(controls.length()) { index ->
                    val item = controls.getJSONObject(index)
                    val labels = item.getJSONArray("text")
                    GlassControl(
                        x = item.getDouble("x").toFloat(),
                        y = item.getDouble("y").toFloat(),
                        width = item.getDouble("w").toFloat(),
                        height = item.getDouble("h").toFloat(),
                        kind = item.getString("kind"),
                        text = List(labels.length()) { labels.getString(it) },
                        font = item.getDouble("font").toInt()
                    )
                }
            )
        }
    }
}
