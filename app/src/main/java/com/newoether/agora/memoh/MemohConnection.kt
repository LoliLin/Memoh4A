package com.newoether.agora.memoh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

internal enum class MemohConnectionState { Idle, Connecting, Connected, Retrying, Blocked }

/** Owns one socket, its ticket request and retry timer. All callbacks enter the owning scope. */
internal class MemohConnection(
    private val scope: CoroutineScope,
    private val open: suspend (String, WebSocketListener) -> WebSocket,
    private val onConnected: (WebSocket) -> Unit,
    private val onEvent: (JSONObject) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onUnauthorized: () -> Unit,
) {
    var state by mutableStateOf(MemohConnectionState.Idle)
        private set
    var error by mutableStateOf("")
        private set
    private var botId: String? = null
    private var generation = 0
    private var socket: WebSocket? = null
    private var opening: Job? = null
    private var retrying: Job? = null
    private var retryDelayMs = 1_000L

    fun connect(id: String) {
        close()
        botId = id
        attempt(id)
    }

    fun retry() {
        val id = botId ?: return
        connect(id)
    }

    fun send(value: JSONObject): Boolean {
        if (state != MemohConnectionState.Connected) return false
        if (socket?.send(value.toString()) == true) return true
        failed(generation, java.io.IOException("The live connection stopped accepting messages"), null)
        return false
    }

    fun close() {
        generation++
        botId = null
        opening?.cancel()
        retrying?.cancel()
        opening = null
        retrying = null
        socket?.cancel()
        socket = null
        state = MemohConnectionState.Idle
        error = ""
        retryDelayMs = 1_000L
    }

    private fun attempt(id: String) {
        val current = ++generation
        state = MemohConnectionState.Connecting
        opening = scope.launch {
            try {
                val opened = open(id, memohSocketListener(
                    scope = scope,
                    isCurrent = { current == generation && botId == id },
                    onOpen = { webSocket ->
                        socket = webSocket
                        state = MemohConnectionState.Connected
                        error = ""
                        retryDelayMs = 1_000L
                        onConnected(webSocket)
                    },
                    onEvent = onEvent,
                    onDisconnected = { failure, status -> failed(current, failure, status) },
                ))
                if (current != generation || botId != id) opened.cancel() else socket = opened
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failed(current, failure, (failure as? MemohHttpException)?.status)
            }
        }
    }

    private fun failed(current: Int, failure: Throwable?, status: Int?) {
        if (current != generation || botId == null) return
        val id = botId ?: return
        // Invalidate immediately: cancellation can itself queue another failure callback.
        val next = ++generation
        socket?.cancel()
        socket = null
        error = if (status != null) "Live connection failed: HTTP $status"
            else failure?.message?.replace(Regex("(?i)(?:https?|wss?)://\\S+"), "<server>")
                ?.takeIf { it.isNotBlank() } ?: "The live connection closed"
        onDisconnected()
        if (next != generation || botId != id) return
        if (status == 401) {
            state = MemohConnectionState.Blocked
            onUnauthorized()
            return
        }
        if (status != null && status in 400..499 && status != 408 && status != 429) {
            state = MemohConnectionState.Blocked
            return
        }
        state = MemohConnectionState.Retrying
        val wait = retryDelayMs
        // Homem doubles the backoff capped at 30s; the previous 1.5x/10s gave up sooner
        // on flaky links that the reference client rides through.
        retryDelayMs = (wait * 2).coerceAtMost(30_000L)
        retrying = scope.launch {
            delay(wait)
            if (next == generation && botId == id) attempt(id)
        }
    }
}
