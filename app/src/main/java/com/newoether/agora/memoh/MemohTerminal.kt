package com.newoether.agora.memoh

import com.newoether.agora.R

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

private const val TERMINAL_BACKGROUND = 0xFF0E1214.toInt()
private const val TERMINAL_ASSET_URL = "https://appassets.androidplatform.net/assets/terminal/index.html"

private enum class TerminalConnectionState { Idle, Connecting, Connected, Failed }

private sealed interface TerminalOutput {
    data class Text(val value: String) : TerminalOutput
    data class Bytes(val value: ByteString) : TerminalOutput
}

/** Owns one authenticated shell socket and fences callbacks from replaced connections. */
private class MemohTerminalController(
    private val api: MemohApi,
    private val botId: String,
    private val deliver: (TerminalOutput) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    var state by mutableStateOf(TerminalConnectionState.Idle)
        private set
    var message by mutableStateOf("Connect to open a shell")
        private set

    @Volatile private var generation = 0
    @Volatile private var socket: WebSocket? = null
    private var connectJob: Job? = null
    @Volatile private var columns = 80
    @Volatile private var rows = 24

    fun connect(scope: CoroutineScope) {
        if (state == TerminalConnectionState.Connecting || state == TerminalConnectionState.Connected) return
        val currentGeneration = ++generation
        connectJob?.cancel()
        socket?.cancel()
        socket = null
        state = TerminalConnectionState.Connecting
        message = "Opening workspace shell…"

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != currentGeneration) {
                    webSocket.cancel()
                    return
                }
                socket = webSocket
                mainHandler.post {
                    if (generation == currentGeneration) {
                        state = TerminalConnectionState.Connected
                        message = "Connected · ${columns} × ${this@MemohTerminalController.rows}"
                    }
                }
                sendResize()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation == currentGeneration) deliver(TerminalOutput.Text(text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation == currentGeneration) deliver(TerminalOutput.Bytes(bytes))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                response?.close()
                if (socket === webSocket) socket = null
                if (generation != currentGeneration) return
                mainHandler.post {
                    if (generation == currentGeneration) {
                        state = TerminalConnectionState.Failed
                        message = when (code) {
                            401 -> "Sign-in expired · reconnect after signing in again"
                            403 -> "This bot does not allow terminal access"
                            404 -> "Terminal is unavailable for this bot"
                            null -> "Connection lost · tap reconnect to open a new shell"
                            else -> "Terminal connection failed · HTTP $code"
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                mainHandler.post {
                    if (generation == currentGeneration && state == TerminalConnectionState.Connected) {
                        state = TerminalConnectionState.Idle
                        message = "Shell closed · reconnect to open a new one"
                    }
                }
            }
        }

        connectJob = scope.launch {
            try {
                api.terminalSocket(botId, columns, rows, listener).also { opened ->
                    if (generation != currentGeneration) opened.cancel() else socket = opened
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == currentGeneration) {
                    state = TerminalConnectionState.Failed
                    message = when (failure) {
                        is MemohHttpException -> when (failure.status) {
                            401 -> "Sign-in expired · reconnect after signing in again"
                            403 -> "This bot does not allow terminal access"
                            404 -> "Terminal is unavailable for this bot"
                            else -> "Terminal connection failed · HTTP ${failure.status}"
                        }
                        else -> "Could not open terminal · tap reconnect to try again"
                    }
                }
            }
        }
    }

    fun disconnect() {
        generation += 1
        connectJob?.cancel()
        connectJob = null
        socket?.close(1000, "Terminal screen closed")
        socket = null
        state = TerminalConnectionState.Idle
        message = "Terminal paused"
    }

    fun reconnect(scope: CoroutineScope) {
        disconnect()
        connect(scope)
    }

    fun resize(cols: Int, rows: Int) {
        columns = cols.coerceIn(1, 500)
        this.rows = rows.coerceIn(1, 500)
        val activeSocket = socket
        if (activeSocket != null) {
            mainHandler.post {
                if (socket === activeSocket && generation > 0) {
                    message = "Connected · ${columns} × ${this.rows}"
                }
            }
            sendResize()
        }
    }

    fun sendInput(bytes: ByteArray) {
        if (bytes.isNotEmpty()) socket?.send(bytes.toByteString())
    }

    private fun sendResize() {
        val frame = JSONObject().put("type", "resize").put("cols", columns).put("rows", rows)
        socket?.send(frame.toString())
    }
}

private class TerminalJavascriptBridge(
    private val controller: MemohTerminalController,
    private val onReady: () -> Unit,
    private val onControlConsumed: () -> Unit,
) {
    @JavascriptInterface fun ready() = onReady()
    @JavascriptInterface fun resize(cols: Int, rows: Int) = controller.resize(cols, rows)
    @JavascriptInterface fun sendInputBase64(value: String) {
        runCatching { Base64.decode(value, Base64.DEFAULT) }
            .getOrNull()?.let(controller::sendInput)
    }
    @JavascriptInterface fun controlModifierConsumed() = onControlConsumed()
}

@Composable
internal fun MemohTerminalDialog(api: MemohApi, botId: String, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val webViewRef = remember { AtomicReference<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var controlModifier by remember { mutableStateOf(false) }
    var moreKeysExpanded by remember { mutableStateOf(false) }

    val controller = remember(api, botId) {
        MemohTerminalController(api, botId) { output ->
            webViewRef.get()?.post {
                val webView = webViewRef.get() ?: return@post
                when (output) {
                    is TerminalOutput.Text -> webView.evaluateJavascript(
                        "window.writeTerminalText(${JSONObject.quote(output.value)})", null,
                    )
                    is TerminalOutput.Bytes -> {
                        val encoded = Base64.encodeToString(output.value.toByteArray(), Base64.NO_WRAP)
                        webView.evaluateJavascript("window.writeTerminalBase64('$encoded')", null)
                    }
                }
            }
        }
    }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val bridge = remember(controller) {
        TerminalJavascriptBridge(
            controller = controller,
            onReady = { webViewRef.get()?.post { pageReady = true } },
            onControlConsumed = { webViewRef.get()?.post { controlModifier = false } },
        )
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.disconnect()
            webViewRef.get()?.let { webView ->
                webView.post {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("AndroidTerminal")
                    webView.destroy()
                    webViewRef.compareAndSet(webView, null)
                }
            }
        }
    }
    LaunchedEffect(pageReady, foreground, controller) {
        if (pageReady && foreground) controller.connect(this)
        else if (!foreground) controller.disconnect()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiResources.getString(R.string.memoh_ui_close_terminal))
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(uiResources.getString(R.string.memoh_ui_terminal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            controller.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (controller.state == TerminalConnectionState.Failed)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (controller.state == TerminalConnectionState.Connecting) {
                        CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            val view = webViewRef.get()
                            if (view != null) {
                                view.evaluateJavascript("window.fitTerminal()", null)
                                controller.reconnect(scope)
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = uiResources.getString(R.string.memoh_ui_reconnect_terminal))
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(TERMINAL_BACKGROUND)),
                    factory = { viewContext ->
                        createTerminalWebView(viewContext, assetLoader, bridge).also { webView ->
                            webViewRef.set(webView)
                            webView.loadUrl(TERMINAL_ASSET_URL)
                        }
                    },
                    update = { webViewRef.compareAndSet(null, it) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalKeyButton("Esc") { sendTerminalKey(webViewRef.get(), "Esc") }
                    FilledTonalButton(
                        onClick = {
                            controlModifier = !controlModifier
                            webViewRef.get()?.evaluateJavascript("window.setControlModifier($controlModifier)", null)
                        },
                        modifier = Modifier.height(40.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (controlModifier) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) { Text("Ctrl", fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                    TerminalKeyButton("Tab") { sendTerminalKey(webViewRef.get(), "Tab") }
                    TerminalKeyButton("←") { sendTerminalKey(webViewRef.get(), uiResources.getString(R.string.memoh_ui_left)) }
                    TerminalKeyButton("↓") { sendTerminalKey(webViewRef.get(), uiResources.getString(R.string.memoh_ui_down)) }
                    TerminalKeyButton("↑") { sendTerminalKey(webViewRef.get(), uiResources.getString(R.string.memoh_ui_up)) }
                    TerminalKeyButton("→") { sendTerminalKey(webViewRef.get(), uiResources.getString(R.string.memoh_ui_right)) }
                    Box {
                        TerminalKeyButton("···") { moreKeysExpanded = true }
                        DropdownMenu(expanded = moreKeysExpanded, onDismissRequest = { moreKeysExpanded = false }) {
                            (1..12).forEach { index ->
                                DropdownMenuItem(
                                    text = { Text("F$index", fontFamily = FontFamily.Monospace) },
                                    onClick = { moreKeysExpanded = false; sendTerminalKey(webViewRef.get(), "F$index") },
                                )
                            }
                            listOf("Home", "End", uiResources.getString(R.string.memoh_ui_page_up), uiResources.getString(R.string.memoh_ui_page_down), uiResources.getString(R.string.memoh_ui_delete), "~", "|", "/", "-").forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(key, fontFamily = FontFamily.Monospace) },
                                    onClick = { moreKeysExpanded = false; sendTerminalKey(webViewRef.get(), key) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { toggleTerminalKeyboard(context, webViewRef.get()) }) {
                        Icon(Icons.Default.Keyboard, contentDescription = uiResources.getString(R.string.memoh_ui_show_or_hide_keyboard))
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalKeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp, vertical = 0.dp),
        shape = CircleShape,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1)
    }
}

private fun sendTerminalKey(webView: WebView?, key: String) {
    webView?.evaluateJavascript("window.sendTerminalKey(${JSONObject.quote(key)})", null)
}

private fun toggleTerminalKeyboard(context: Context, webView: WebView?) {
    if (webView == null) return
    val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    val immVisible = ViewCompat.getRootWindowInsets(webView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    if (immVisible) {
        manager.hideSoftInputFromWindow(webView.windowToken, 0)
    } else {
        webView.requestFocus()
        webView.evaluateJavascript("window.focusTerminal()", null)
        webView.post { manager.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTerminalWebView(
    context: Context,
    assetLoader: WebViewAssetLoader,
    bridge: TerminalJavascriptBridge,
): WebView = WebView(context).apply {
    setBackgroundColor(TERMINAL_BACKGROUND)
    isFocusable = true
    isFocusableInTouchMode = true
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.domStorageEnabled = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    addJavascriptInterface(bridge, "AndroidTerminal")
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.host != WebViewAssetLoader.DEFAULT_DOMAIN
    }
}
