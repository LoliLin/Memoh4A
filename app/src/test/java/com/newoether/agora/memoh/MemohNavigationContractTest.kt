package com.newoether.agora.memoh

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemohNavigationContractTest {
    private fun withServer(response: String, status: Int = 200, test: (MemohApi, () -> JSONObject) -> Unit) {
        var request = JSONObject()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            request = JSONObject().put("method", exchange.requestMethod)
                .put("path", exchange.requestURI.rawPath).put("query", exchange.requestURI.rawQuery)
                .put("body", exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = MemohApi().apply { configure("http://127.0.0.1:${server.address.port}") }
            test(api) { request }
        } finally { server.stop(0) }
    }

    @Test
    fun `schedule runs use session IDs and omit failed fires with no session`() {
        val page = decodeScheduleSessions(JSONObject("""{
            "items":[
                {"log_id":"fire-1","session_id":"chat-1","title":"Result"},
                {"log_id":"fire-2","session_id":"","title":""},
                {"log_id":"fire-3","session_id":null},
                {"log_id":"fire-4","session_id":"chat-1","title":"Result"},
                {"log_id":"fire-5","session_id":"chat-2","title":""}
            ],"next_cursor":"older-token"
        }"""))
        assertEquals(listOf("chat-1", "chat-2"), page.items.map { it.id })
        assertEquals("Result", page.items.first().title)
        assertEquals("older-token", page.nextCursor)
        assertEquals(null, decodeScheduleSessions(JSONObject("""{"items":[],"next_cursor":""}""")).nextCursor)
    }

    @Test
    fun `detail lookup preserves the server Agent and runtime`() = runBlocking {
        withServer("""{"id":"chat/1","title":"Scheduled Result","bot_agent_id":"other-agent","runtime_type":"codex","type":"schedule"}""") { api, request ->
            val result = runBlocking { api.getSession("bot/1", "chat/1") }
            assertEquals("other-agent", result.botAgentId)
            assertEquals("codex", result.runtimeType)
            assertEquals("schedule", result.type)
            // OSS exposes the configured API root directly, including escaped path components.
            assertEquals("/bots/bot%2F1/sessions/chat%2F1", request().getString("path"))
            assertEquals("GET", request().getString("method"))
        }
    }

    @Test
    fun `mismatched detail response never supplies a navigation target`() {
        withServer("""{"id":"wrong-chat"}""") { api, _ ->
            val error = runCatching { runBlocking { api.getSession("bot", "expected-chat") } }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
    }

    @Test
    fun `deleted session surfaces HTTP failure instead of a placeholder`() {
        withServer("""{"detail":"Conversation deleted"}""", 404) { api, _ ->
            val error = runCatching { runBlocking { api.getSession("bot", "deleted") } }.exceptionOrNull()
            assertTrue(error is MemohHttpException)
            assertEquals(404, (error as MemohHttpException).status)
        }
    }

    @Test
    fun `schedule session pagination encodes the opaque cursor`() {
        withServer("""{"items":[{"log_id":"fire","session_id":"chat","title":"Run"}],"next_cursor":"next"}""") { api, request ->
            val page = runBlocking { api.scheduleSessions("bot", "schedule", "a+b/c=") }
            assertEquals("chat", page.items.single().id)
            assertEquals("next", page.nextCursor)
            assertEquals("limit=20&cursor=a%2Bb%2Fc%3D", request().getString("query"))
        }
    }

    @Test
    fun `extract sends only path and reads the server destination`() {
        withServer("""{"destination":"/data/archive","files":2,"directories":1}""") { api, request ->
            val destination = runBlocking { api.extractArchive("bot", "/data/archive.zip") }
            assertEquals("/data/archive", destination)
            assertEquals("POST", request().getString("method"))
            val body = JSONObject(request().getString("body"))
            assertEquals(1, body.length())
            assertEquals("/data/archive.zip", body.getString("path"))
        }
    }

    @Test
    fun `extract conflict remains an error`() {
        withServer("""{"detail":"destination already exists"}""", 409) { api, _ ->
            val error = runCatching { runBlocking { api.extractArchive("bot", "/data/archive.zip") } }.exceptionOrNull()
            assertEquals(409, (error as MemohHttpException).status)
        }
    }

    @Test
    fun `archive actions match supported server formats`() {
        listOf("a.zip", "a.TAR.GZ", "a.TgZ").forEach { assertTrue(it, isArchiveName(it)) }
        listOf("a.tar", "a.gz", "a.bz2", "a.xz", "a.7z", "a.rar", "a.zip.txt").forEach {
            assertFalse(it, isArchiveName(it))
        }
    }

    @Test
    fun `run logs preserve missing sessions and offset pagination`() {
        withServer("""{"items":[{"id":"fire","session_id":null,"status":"error"}]}""") { api, request ->
            val logs = runBlocking { api.scheduleLogs("bot", "schedule", offset = 20) }
            assertEquals("", logs.single().sessionId)
            assertEquals("limit=20&offset=20", request().getString("query"))
        }
    }
}
