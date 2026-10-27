package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.R
import com.newoether.agora.ui.theme.AgoraTheme
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1200dp-xhdpi")
class MemohProviderOAuthRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceAuthorizationRefreshesManagedModelsAfterSuccess() {
        val paths = CopyOnWriteArrayList<String>()
        val teams = CopyOnWriteArrayList<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += "${exchange.requestMethod} ${exchange.requestURI.path}"
            teams += exchange.requestHeaders.getFirst("X-Team-ID")
            val payload = when {
                exchange.requestURI.path.endsWith("/status") -> """{"configured":true,"mode":"device","has_token":false}"""
                exchange.requestURI.path.endsWith("/authorize") -> """{"mode":"device","device":{"pending":true,"user_code":"TEST-CODE","verification_uri":"https://example.test/device","interval_seconds":2}}"""
                exchange.requestURI.path.endsWith("/poll") -> """{"configured":true,"mode":"device","has_token":true,"account":{"login":"person"}}"""
                exchange.requestURI.path.endsWith("/import-models") -> """{"created":1,"updated":0,"skipped":0}"""
                else -> "{}"
            }.toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        val api = MemohApi()
        try {
            api.configure("http://127.0.0.1:${server.address.port}/api", "test-token")
            api.selectTeam("team-1")
            val provider = MemohProvider("provider-1", "Copilot", "github-copilot", "", true, false, JSONObject(), "", false)
            val changes = AtomicInteger()
            val resources = ApplicationProvider.getApplicationContext<Application>().resources
            val admin = MemohAdminApi(api)
            compose.setContent {
                AgoraTheme { Column { MemohProviderOAuthSection(admin, provider, true) { changes.incrementAndGet() } } }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(resources.getString(R.string.memoh_ui_oauth_connect)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(resources.getString(R.string.memoh_ui_oauth_connect)).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("TEST-CODE").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(20_000) { changes.get() == 1 }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("@person").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf(
                "GET /api/providers/provider-1/oauth/status",
                "GET /api/providers/provider-1/oauth/authorize",
                "POST /api/providers/provider-1/oauth/poll",
                "POST /api/providers/provider-1/import-models",
            ), paths.toList())
            assertTrue(teams.all { it == "team-1" })
        } finally {
            api.clearIdentity()
            server.stop(0)
        }
    }

    @Test fun cancelStopsLocalPollingWithoutRevokingServerCredentials() {
        val paths = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += "${exchange.requestMethod} ${exchange.requestURI.path}"
            val payload = (if (exchange.requestURI.path.endsWith("/authorize"))
                """{"mode":"device","device":{"pending":true,"user_code":"CANCEL-CODE","verification_uri":"https://example.test/device","interval_seconds":2}}"""
            else """{"configured":true,"mode":"device","has_token":false}""").toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        val api = MemohApi()
        try {
            api.configure("http://127.0.0.1:${server.address.port}/api", "test-token")
            api.selectTeam("team-1")
            val provider = MemohProvider("provider-1", "Copilot", "github-copilot", "", true, false, JSONObject(), "", false)
            val resources = ApplicationProvider.getApplicationContext<Application>().resources
            val admin = MemohAdminApi(api)
            compose.setContent {
                AgoraTheme { Column { MemohProviderOAuthSection(admin, provider, true) {} } }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(resources.getString(R.string.memoh_ui_oauth_connect)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(resources.getString(R.string.memoh_ui_oauth_connect)).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("CANCEL-CODE").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(resources.getString(R.string.memoh_ui_cancel)).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(resources.getString(R.string.memoh_ui_oauth_connect)).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(2300)
            assertEquals(listOf("GET /api/providers/provider-1/oauth/status", "GET /api/providers/provider-1/oauth/authorize"), paths.toList())
        } finally {
            api.clearIdentity()
            server.stop(0)
        }
    }
}
