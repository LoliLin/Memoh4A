package com.newoether.agora.memoh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Socket callbacks for one connection generation.
 *
 * Every callback checks [isCurrent] before touching ViewModel state: a socket that the user
 * already navigated away from must not flip `connected`, deliver a stale event, or sign the
 * user out because its own handshake failed in the background.
 */
internal fun memohSocketListener(
    scope: CoroutineScope,
    generation: Int,
    isCurrent: () -> Boolean,
    onOpen: (WebSocket) -> Unit,
    onEvent: (JSONObject) -> Unit,
    onUnauthorized: () -> Unit,
    onDisconnected: () -> Unit,
) = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        scope.launch { if (isCurrent()) onOpen(webSocket) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        scope.launch { if (isCurrent()) onEvent(json) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (response?.code == 401) {
            scope.launch { if (isCurrent()) onUnauthorized() }
        } else if (generation >= 0) {
            onDisconnected()
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onDisconnected()
    }
}
