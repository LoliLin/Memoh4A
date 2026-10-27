package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class MemohProviderSettingsRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun providerAndModelActionsUseTheSelectedTeamAndShowServerFailures() {
        data class Seen(val path: String, val auth: String?, val team: String?, val body: String)
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += Seen(
                exchange.requestURI.path,
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("X-Team-ID"),
                exchange.requestBody.bufferedReader().readText(),
            )
            val bytes = (if (exchange.requestURI.path.endsWith("/test")) {
                """{"status":"auth_error"}"""
            } else {
                """{"id":"model-new","model_id":"custom-model"}"""
            }).toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val api = MemohApi()
        try {
            api.configure("http://127.0.0.1:${server.address.port}/api", "local-test-token")
            api.selectTeam("team-1")
            val provider = MemohProvider(
                id = "provider-1", name = "Test provider", clientType = "openai",
                icon = "", enabled = true, managed = false, config = JSONObject(),
                baseUrl = "https://provider.test", hasApiKey = true,
            )
            val model = MemohAdminModel(
                id = "model-1", modelId = "old-model", name = "Old model", providerId = "provider-1",
                type = "chat", enabled = true, contextWindow = 0, reasoningEfforts = emptyList(),
            )
            val changes = AtomicInteger()
            val res = ApplicationProvider.getApplicationContext<Application>().resources
            compose.setContent {
                AgoraTheme {
                    MemohProviderDetailPage(
                        admin = MemohAdminApi(api),
                        provider = provider,
                        models = listOf(model),
                        canManage = true,
                        onBack = {},
                        onChanged = { changes.incrementAndGet() },
                    )
                }
            }

            compose.onNodeWithText(res.getString(R.string.memoh_ui_test)).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(res.getString(R.string.memoh_ui_test_failed)).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText(res.getString(R.string.memoh_ui_test_failed)).assertIsDisplayed()

            compose.onNodeWithContentDescription(res.getString(R.string.memoh_ui_options)).performScrollTo().performClick()
            val testEntries = compose.onAllNodesWithText(res.getString(R.string.memoh_ui_test_connection))
            testEntries[testEntries.fetchSemanticsNodes().lastIndex].performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(res.getString(R.string.memoh_ui_test_failed)).fetchSemanticsNodes().size == 2
            }
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_test_failed)).assertCountEquals(2)

            // The create dialog is a platform AlertDialog; Robolectric never reports its window idle,
            // so its request body is covered by MemohSelfHostedContractTest instead of a UI click.
            compose.onNodeWithText(res.getString(R.string.models_add_custom)).performScrollTo().assertIsDisplayed()

            assertEquals(
                listOf("/api/providers/provider-1/test", "/api/models/model-1/test"),
                seen.map { it.path },
            )
            assertTrue(seen.all { it.auth == "Bearer local-test-token" && it.team == "team-1" })
            assertEquals(0, changes.get())
        } finally {
            api.clearIdentity()
            server.stop(0)
        }
    }

    @Test
    fun aMemberWithoutManagePermissionIsNotOfferedEditOrDelete() {
        val api = MemohApi().apply {
            configure("http://127.0.0.1:1/api", "local-test-token")
            selectTeam("team-1")
        }
        try {
            val provider = MemohProvider(
                id = "provider-1", name = "Test provider", clientType = "openai",
                icon = "", enabled = true, managed = false, config = JSONObject(),
                baseUrl = "https://provider.test", hasApiKey = true,
            )
            val model = MemohAdminModel(
                id = "model-1", modelId = "old-model", name = "Old model", providerId = "provider-1",
                type = "chat", enabled = true, contextWindow = 0, reasoningEfforts = emptyList(),
            )
            val res = ApplicationProvider.getApplicationContext<Application>().resources
            compose.setContent {
                AgoraTheme {
                    MemohProviderDetailPage(
                        admin = MemohAdminApi(api),
                        provider = provider,
                        models = listOf(model),
                        canManage = false,
                        onBack = {},
                        onChanged = {},
                    )
                }
            }

            // Read-only surfaces stay, writes disappear.
            compose.onNodeWithText(res.getString(R.string.memoh_ui_models)).assertIsDisplayed()
            compose.onAllNodesWithText(res.getString(R.string.models_add_custom)).assertCountEquals(0)
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_edit_provider)).assertCountEquals(0)
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_remove_provider)).assertCountEquals(0)
            compose.onNodeWithText(res.getString(R.string.memoh_ui_providers_managed_hint)).assertIsDisplayed()

            // The per-model menu keeps its read-only test action but drops disable and remove.
            compose.onNodeWithContentDescription(res.getString(R.string.memoh_ui_options))
                .performScrollTo().performClick()
            // "Test connection" is both the group title and the menu entry; the open menu adds one.
            assertEquals(
                2,
                compose.onAllNodesWithText(res.getString(R.string.memoh_ui_test_connection))
                    .fetchSemanticsNodes().size,
            )
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_remove)).assertCountEquals(0)
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_disable)).assertCountEquals(0)
            compose.onAllNodesWithText(res.getString(R.string.memoh_ui_enable)).assertCountEquals(0)
        } finally {
            api.clearIdentity()
        }
    }
}
