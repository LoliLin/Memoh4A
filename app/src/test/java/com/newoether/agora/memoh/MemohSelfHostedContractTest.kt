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

    @Test
    fun `administration requests share the selected server credentials and team`() = runBlocking {
        data class Seen(val method: String, val path: String, val auth: String?, val team: String?, val body: String)
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += Seen(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("X-Team-ID"),
                exchange.requestBody.bufferedReader().readText(),
            )
            val response = (if (exchange.requestURI.path.endsWith("/test")) {
                """{"status":"auth_error"}"""
            } else {
                """{"id":"provider-1"}"""
            }).toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val api = MemohApi()
        try {
            api.configure("http://127.0.0.1:${server.address.port}/proxy/api", "test-jwt")
            api.selectTeam("team-1")
            val admin = MemohAdminApi(api)
            admin.createProviderFromRequest(JSONObject().put("name", "Test"))
            val result = admin.testProvider("provider-1")
            admin.createModel(modelRequestBody("provider-1", " vendor/model ", "", true, emptyList(), 0, create = true))
            admin.testModel("model-1")
            admin.updateModel("model-1", JSONObject().put("enable", false))
            admin.deleteProvider("provider-1")

            assertEquals(MemohConnectivityStatus.FAILED, memohConnectivityStatus(result))
            assertEquals(
                listOf(
                    "POST" to "/proxy/api/providers",
                    "POST" to "/proxy/api/providers/provider-1/test",
                    "POST" to "/proxy/api/models",
                    "POST" to "/proxy/api/models/model-1/test",
                    "PUT" to "/proxy/api/models/model-1",
                    "DELETE" to "/proxy/api/providers/provider-1",
                ),
                seen.map { it.method to it.path },
            )
            assertTrue(seen.all { it.auth == "Bearer test-jwt" && it.team == "team-1" })
            val modelBody = JSONObject(seen[2].body)
            assertEquals("provider-1", modelBody.getString("provider_id"))
            assertEquals("vendor/model", modelBody.getString("model_id"))
            assertEquals("chat", modelBody.getString("type"))
            assertTrue(modelBody.getBoolean("enable"))

            api.selectTeam("team-2")
            try {
                admin.providers()
                fail("A client captured for the old team must not send requests after a switch")
            } catch (_: kotlinx.coroutines.CancellationException) {
                // The settings page must create a new client for the new team.
            }
            assertEquals(6, seen.size)
        } finally {
            api.clearIdentity()
            server.stop(0)
        }
    }
}
