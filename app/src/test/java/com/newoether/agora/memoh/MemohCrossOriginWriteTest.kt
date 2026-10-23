package com.newoether.agora.memoh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A cookie session is ambient authority, so the server refuses writes it cannot attribute to
 * its own origin: "cross-origin cookie-authenticated write forbidden".
 *
 * Regression: no request carried an `Origin` header, so the ticket exchange behind every
 * WebSocket handshake was rejected and the chat sat on "Reconnecting..." with no way forward.
 */
@RunWith(RobolectricTestRunner::class)
class MemohCrossOriginWriteTest {

    private suspend fun cookieApi(server: MemohCloudServer): MemohApi =
        MemohApi().apply {
            configure(server.gateway)
            verifyEmailCode("user@example.com", "123456")
            selectTeam("a")
        }

    @Test
    fun `cookie writes declare the server origin`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = cookieApi(server)
            api.teamPlatformRequest("/ws-tickets", org.json.JSONObject(), method = "POST")

            val ticket = server.requests.last { it.path == "/api/v1/ws-tickets" }
            assertNotNull("ticket request sent no Origin", ticket.origin)
            assertEquals(server.origin, ticket.origin)
        }
    }

    @Test
    fun `the socket handshake carries the same origin`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = cookieApi(server)
            runCatching { api.socket("shared", object : okhttp3.WebSocketListener() {}) }

            // The handshake is refused by this fixture, but the request it issued must still
            // have declared its origin before any ticket could be honoured.
            val ticket = server.requests.last { it.path == "/api/v1/ws-tickets" }
            assertEquals(server.origin, ticket.origin)
        }
    }

    @Test
    fun `bearer requests do not pretend to be browser origins`() = runBlocking {
        MemohCloudServer().use { server ->
            // A self-hosted bearer client is not a browser: it must never forge an Origin.
            val api = MemohApi().apply { configure(server.gateway, bearer = "self-hosted-token") }
            runCatching { api.teamPlatformRequest("/ws-tickets", org.json.JSONObject(), method = "POST") }

            val ticket = server.requests.lastOrNull { it.path == "/api/v1/ws-tickets" }
            if (ticket != null) {
                assertNull(
                    "a bearer client is not a browser and must not forge Origin",
                    ticket.origin,
                )
            }
        }
    }
}
