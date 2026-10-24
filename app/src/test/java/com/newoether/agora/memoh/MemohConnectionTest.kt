package com.newoether.agora.memoh

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MemohConnectionTest {
    private fun socket(): WebSocket = mockk(relaxed = true) { every { send(any<String>()) } returns true }
    private fun response(code: Int = 101): Response = Response.Builder()
        .request(Request.Builder().url("https://app.memoh.net/").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("test").body("{}".toResponseBody()).build()

    @Test
    fun `a failed ticket is retried and a successful open clears the error`() = runTest {
        var attempts = 0
        lateinit var listener: WebSocketListener
        val ws = socket()
        val connection = MemohConnection(this,
            open = { _, callback ->
                attempts++
                if (attempts == 1) throw IOException("temporarily offline")
                listener = callback
                ws
            }, onConnected = {}, onEvent = {}, onDisconnected = {}, onUnauthorized = {})
        connection.connect("bot")
        runCurrent()
        assertEquals(MemohConnectionState.Retrying, connection.state)
        assertEquals("temporarily offline", connection.error)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, attempts)
        listener.onOpen(ws, response())
        runCurrent()
        assertEquals(MemohConnectionState.Connected, connection.state)
        assertEquals("", connection.error)
        connection.close()
    }

    @Test
    fun `a permanent rejection stops automatic retry but allows an explicit retry`() = runTest {
        var attempts = 0
        val ws = socket()
        val connection = MemohConnection(this,
            open = { _, _ -> attempts++; throw MemohHttpException(403, "write forbidden") },
            onConnected = {}, onEvent = {}, onDisconnected = {}, onUnauthorized = {})
        connection.connect("bot")
        runCurrent()
        assertEquals(MemohConnectionState.Blocked, connection.state)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, attempts)
        connection.retry()
        runCurrent()
        assertEquals(2, attempts)
        connection.close()
    }

    @Test
    fun `old socket callbacks cannot disconnect replace or sign out the new connection`() = runTest {
        val listeners = mutableListOf<WebSocketListener>()
        val sockets = mutableListOf<WebSocket>()
        var unauthorized = 0
        var events = 0
        val connection = MemohConnection(this,
            open = { _, callback -> listeners += callback; socket().also { sockets += it } },
            onConnected = {}, onEvent = { events++ }, onDisconnected = {}, onUnauthorized = { unauthorized++ })
        connection.connect("old")
        runCurrent()
        connection.connect("new")
        runCurrent()
        listeners[1].onOpen(sockets[1], response())
        listeners[0].onOpen(sockets[0], response())
        listeners[0].onMessage(sockets[0], """{"type":"runtime_snapshot"}""")
        listeners[0].onFailure(sockets[0], IOException("old"), response(401))
        listeners[0].onClosed(sockets[0], 1000, "old")
        runCurrent()
        assertEquals(MemohConnectionState.Connected, connection.state)
        assertEquals(0, unauthorized)
        assertEquals(0, events)
        connection.close()
    }

    @Test
    fun `closing cancels an outstanding ticket request and its retry timer`() = runTest {
        var cancelled = false
        val connection = MemohConnection(this,
            open = { _, _ -> try { awaitCancellation() } finally { cancelled = true } },
            onConnected = {}, onEvent = {}, onDisconnected = {}, onUnauthorized = {})
        connection.connect("bot")
        runCurrent()
        connection.close()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(MemohConnectionState.Idle, connection.state)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(MemohConnectionState.Idle, connection.state)
    }

    @Test
    fun `unauthorized tickets end the session once without a retry loop`() = runTest {
        var unauthorized = 0
        var attempts = 0
        val connection = MemohConnection(this,
            open = { _, _ -> attempts++; throw MemohHttpException(401, "expired") },
            onConnected = {}, onEvent = {}, onDisconnected = {}, onUnauthorized = { unauthorized++ })
        connection.connect("bot")
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(1, unauthorized)
        assertEquals(MemohConnectionState.Blocked, connection.state)
        connection.close()
    }

    @Test
    fun `a rejected websocket send changes the state and schedules recovery`() = runTest {
        val ws = socket()
        lateinit var listener: WebSocketListener
        val connection = MemohConnection(this,
            open = { _, callback -> listener = callback; ws }, onConnected = {}, onEvent = {},
            onDisconnected = {}, onUnauthorized = {})
        connection.connect("bot")
        runCurrent()
        listener.onOpen(ws, response())
        runCurrent()
        every { ws.send(any<String>()) } returns false
        assertFalse(connection.send(org.json.JSONObject().put("type", "message")))
        assertEquals(MemohConnectionState.Retrying, connection.state)
        connection.close()
    }
}
