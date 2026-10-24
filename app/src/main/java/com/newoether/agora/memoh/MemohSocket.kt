package com.newoether.agora.memoh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** No OkHttp thread mutates UI state; every callback checks connection ownership. */
internal fun memohSocketListener(
    scope: CoroutineScope,
    isCurrent: () -> Boolean,
    onOpen: (WebSocket) -> Unit,
    onEvent: (JSONObject) -> Unit,
    onDisconnected: (Throwable?, Int?) -> Unit,
) = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        scope.launch { if (isCurrent()) onOpen(webSocket) else webSocket.cancel() }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val event = runCatching { JSONObject(text) }.getOrNull() ?: return
        scope.launch { if (isCurrent()) onEvent(event) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val status = response?.code
        response?.close()
        scope.launch { if (isCurrent()) onDisconnected(t, status) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch { if (isCurrent()) onDisconnected(null, null) }
    }
}
