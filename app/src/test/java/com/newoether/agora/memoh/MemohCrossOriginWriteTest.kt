package com.newoether.agora.memoh

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MemohCrossOriginWriteTest {
    private suspend fun cookieApi(server: MemohCloudServer): MemohApi = MemohApi().apply {
        configure(server.gateway)
        verifyEmailCode("user@example.com", "123456")
        selectTeam("a")
    }

    @Test
    fun `a cookie write without Origin is rejected while the transport succeeds`() = runBlocking {
        MemohCloudServer().use { server ->
            OkHttpClient().newCall(Request.Builder().url(server.origin + "/api/v1/ws-tickets")
                .header("Cookie", "sid=user-1").header("X-Team-ID", "a")
                .post("{}".toRequestBody()).build()).execute().use {
                assertEquals(403, it.code)
                assertTrue(it.body!!.string().contains("cross-origin cookie-authenticated write forbidden"))
            }
            val api = cookieApi(server)
            assertEquals("ticket-a", api.teamPlatformRequest("/ws-tickets", JSONObject()).getString("ticket"))
            val ticket = server.requests.last { it.path == "/api/v1/ws-tickets" }
            assertEquals(server.origin, ticket.origin)
        }
    }

    @Test
    fun `the actual websocket upgrade request keeps the origin cookie team and ticket`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = cookieApi(server)
            val completed = CountDownLatch(1)
            val ws = api.socket("shared", object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    response?.close()
                    completed.countDown()
                }
            })
            try {
                assertTrue("WebSocket upgrade never reached the fixture", completed.await(5, TimeUnit.SECONDS))
                val upgrade = server.requests.single { it.path.endsWith("/web/ws") }
                assertEquals(server.origin, upgrade.origin)
                assertEquals("a", upgrade.team)
                assertTrue(upgrade.cookie.orEmpty().contains("sid=user-1"))
                assertTrue(upgrade.query.orEmpty().contains("team_id=a"))
                assertTrue(upgrade.query.orEmpty().contains("ticket=ticket-a"))
            } finally { ws.cancel() }
        }
    }

    @Test
    fun `bearer requests succeed without an Origin header`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply {
                configure(server.gateway, bearer = "self-hosted-test-token")
                selectTeam("a")
            }
            assertEquals("ticket-a", api.teamPlatformRequest("/ws-tickets", JSONObject()).getString("ticket"))
            assertNull(server.requests.last { it.path == "/api/v1/ws-tickets" }.origin)
        }
    }

    @Test
    fun `logout clears the local cookie immediately and sends its own Origin`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = cookieApi(server)
            val finish = api.endSession()
            assertFalse(api.hasSessionCookie())
            finish()
            val logout = server.requests.single { it.path.endsWith("/auth/logout") }
            assertEquals(server.origin, logout.origin)
            assertTrue(logout.cookie.orEmpty().contains("sid=user-1"))
        }
    }
}
