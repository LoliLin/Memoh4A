package com.newoether.agora.memoh

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Contract from OSS AuthHandler.Register and the Desktop configurable API base. */
@RunWith(RobolectricTestRunner::class)
class MemohSelfHostedContractTest {
    @Test
    fun `OSS root and reverse proxy retain password bearer and socket routes`() = runBlocking {
        for (prefix in listOf("", "/proxy/api")) {
            val seen = CopyOnWriteArrayList<Triple<String, String?, String?>>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                seen += Triple(path, authorization, exchange.requestHeaders.getFirst("X-Team-ID"))
                val payload = exchange.requestBody.bufferedReader().readText()
                var status = 200
                val text = when (path) {
                    "$prefix/auth/login" -> {
                        val body = JSONObject(payload)
                        if (body.optString("username") != "user@example.com" || body.has("email")) status = 400
                        """{"access_token":"test-jwt"}"""
                    }
                    "$prefix/bots" -> {
                        if (authorization != "Bearer test-jwt") status = 401
                        """{"items":[{"id":"oss","name":"Open source"}]}"""
                    }
                    "$prefix/bots/oss/web/ws" -> { status = 400; "{}" }
                    else -> { status = 404; "{}" }
                }
                val bytes = text.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val api = MemohApi()
            try {
                api.configure("http://127.0.0.1:${server.address.port}$prefix")
                assertEquals("test-jwt", api.login("user@example.com", "test-password"))
                assertFalse(api.usesSessionCookie)
                assertEquals("oss", api.bots().single().id)
                val finished = CountDownLatch(1)
                val socket = api.socket("oss", object : WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        finished.countDown()
                    }
                })
                try { assertTrue(finished.await(5, TimeUnit.SECONDS)) } finally { socket.cancel() }
                assertEquals(listOf("$prefix/auth/login", "$prefix/bots", "$prefix/bots/oss/web/ws"), seen.map { it.first })
                assertTrue(seen.drop(1).all { it.second == "Bearer test-jwt" && it.third == null })
            } finally { api.clearIdentity(); server.stop(0) }
        }
    }
}
