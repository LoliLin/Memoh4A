package com.newoether.agora.memoh

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MemohModelPreferenceTest {
    private fun api(): MemohApi = mockk {
        every { scope } returns mockk<MemohTransport.Scope>()
        every { baseUrl } returns "https://example.test"
        every { token } returns ""
        coEvery { agents(any()) } returns emptyList()
        coEvery { agentProfiles() } returns emptyList()
    }

    private fun catalog(id: String = "model-record", name: String = "Actual model") = JSONObject()
        .put("models", JSONArray().put(JSONObject().put("id", id).put("model_id", "provider/model").put("name", name)))

    @Test fun sessionDecodesRememberedPairAndCompareAndSetRevision() {
        val session = decodeSession(JSONObject("""{"id":"chat","title":"Chat","preferred_chat_model_id":"record","preferred_external_model_id":"provider/model","preferred_reasoning_effort":"high","model_preference_revision":"r7"}"""))
        assertEquals("record", session.preferredChatModelId)
        assertEquals("provider/model", session.preferredExternalModelId)
        assertEquals("high", session.preferredReasoningEffort)
        assertEquals("r7", session.modelPreferenceRevision)
    }

    @Test fun botDefaultDisplaysActualNameWithoutBecomingAnExplicitPreference() = runTest {
        val api = api()
        coEvery { api.teamChatModels() } returns catalog()
        val controller = MemohAgentController(api)
        controller.setBotDefault("model-record")
        controller.loadModels()
        assertEquals("Actual model", controller.currentModelLabel)
        assertEquals("model-record", controller.currentModelId)
        assertEquals("", controller.preferredModelId)
    }

    @Test fun openingAnotherConversationRestoresItsPairAndNewChatFollowsBotDefault() = runTest {
        val api = api()
        coEvery { api.teamChatModels() } returns catalog()
        val controller = MemohAgentController(api)
        controller.loadModels()
        controller.setBotDefault("model-record")
        controller.setModelPreference("other", "high")
        controller.beginConversation(MemohSession("chat", "Chat", preferredChatModelId = "model-record", preferredReasoningEffort = "low"))
        assertEquals("model-record", controller.preferredModelId)
        assertEquals("low", controller.preferredReasoningEffort)
        assertEquals("Actual model", controller.currentModelLabel)
        controller.beginConversation(null)
        assertEquals("", controller.preferredModelId)
        assertEquals("", controller.preferredReasoningEffort)
        assertEquals("Actual model", controller.currentModelLabel)
    }

    @Test fun limitedCatalogCannotEraseRememberedModel() = runTest {
        val api = api()
        coEvery { api.teamChatModels() } returns JSONObject().put("models", JSONArray())
        val controller = MemohAgentController(api)
        controller.beginConversation(MemohSession("chat", "Chat", preferredChatModelId = "hidden-record", preferredExternalModelId = "provider/remembered"))
        controller.loadModels()
        assertEquals("hidden-record", controller.preferredModelId)
        assertEquals("provider/remembered", controller.currentModelLabel)
    }

    @Test fun agentRefreshCannotInvalidateInFlightCatalog() = runTest {
        val api = api()
        val pending = CompletableDeferred<JSONObject>()
        coEvery { api.teamChatModels() } coAnswers { pending.await() }
        val controller = MemohAgentController(api)
        launch { controller.loadModels() }
        runCurrent()
        controller.refresh("bot")
        pending.complete(catalog())
        runCurrent()
        controller.setBotDefault("model-record")
        assertFalse(controller.modelsLoading)
        assertEquals("Actual model", controller.currentModelLabel)
    }

    @Test fun clearInvalidatesLateCatalogEvenWhenApiIdentityIsReused() = runTest {
        val api = api()
        val pending = CompletableDeferred<JSONObject>()
        coEvery { api.teamChatModels() } coAnswers { pending.await() }
        val controller = MemohAgentController(api)
        launch { controller.loadModels() }
        runCurrent()
        controller.clear()
        pending.complete(catalog())
        runCurrent()
        assertNull(controller.models)
        assertFalse(controller.modelsLoading)
        assertEquals("", controller.currentModelId)
    }

    @Test fun newerCatalogWinsWhenRequestsFinishOutOfOrder() = runTest {
        val api = api()
        val pending = CompletableDeferred<JSONObject>()
        var calls = 0
        coEvery { api.teamChatModels() } coAnswers { if (++calls == 1) pending.await() else catalog(name = "New catalog") }
        val controller = MemohAgentController(api)
        launch { controller.loadModels() }
        runCurrent()
        controller.loadModels()
        pending.complete(catalog(name = "Old catalog"))
        runCurrent()
        controller.setBotDefault("model-record")
        assertEquals("New catalog", controller.currentModelLabel)
    }

    @Test fun catalogKeepsConfiguredRecordIdSeparateFromProviderModelCode() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, seen ->
                server.reply(exchange, if (seen.path.endsWith("/models"))
                    """{"items":[{"id":"configured-record","model_id":"provider/model","name":"Visible model","type":"chat","enable":true}]}"""
                else """{"items":[]}""")
                true
            }
            val api = MemohApi().apply { configure(server.origin) }
            val model = api.teamChatModels().getJSONArray("models").getJSONObject(0)
            assertEquals("configured-record", model.getString("id"))
            assertEquals("provider/model", model.getString("model_id"))
        }
    }

    @Test fun catalogRetainsServerResolvedReasoningOptionsForTheComposer() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, seen ->
                server.reply(exchange, if (seen.path.endsWith("/models"))
                    """{"items":[{"id":"record","model_id":"model","type":"chat","enable":true,
                    "config":{"context_window":8192,"reasoning_efforts":["low"]},
                    "reasoning":{"supported":true,"can_disable":true,"default_off":false,
                                 "default_effort":"high","efforts":["minimal","high"]}}]}"""
                else """{"items":[]}""")
                true
            }
            val model = MemohApi().apply { configure(server.origin) }.teamChatModels()
                .getJSONArray("models").getJSONObject(0)
            assertEquals(8192, model.getJSONObject("config").getInt("context_window"))
            assertEquals(listOf("disable", "minimal", "high"), model.reasoningEfforts())
            assertEquals("high", model.reasoningDefault())
        }
    }

    @Test fun creationOnlyRemembersExplicitPickerPair() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, _ -> server.reply(exchange, """{"id":"chat","title":"Chat"}"""); true }
            val api = MemohApi().apply { configure(server.origin) }
            api.createSession("bot")
            val implicit = JSONObject(server.requests.last().body)
            assertFalse(implicit.has("preferred_chat_model_id"))
            assertFalse(implicit.has("preferred_reasoning_effort"))
            api.createSession("bot", preferredModelId = "record", preferredEffort = "")
            val explicit = JSONObject(server.requests.last().body)
            assertEquals("record", explicit.getString("preferred_chat_model_id"))
            assertTrue(explicit.has("preferred_reasoning_effort"))
            assertEquals("", explicit.getString("preferred_reasoning_effort"))
        }
    }

    @Test fun patchAlwaysWritesPairAndExpectedRevisionTogether() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, _ -> server.reply(exchange, """{"id":"chat/1","title":"Chat","preferred_chat_model_id":"record","preferred_reasoning_effort":"high","model_preference_revision":"r8"}"""); true }
            val api = MemohApi().apply { configure(server.origin) }
            val updated = api.updateSessionModel("bot/1", "chat/1", "record", "high", "r7")
            val seen = server.requests.last()
            val body = JSONObject(seen.body)
            assertEquals("PATCH", seen.method)
            assertEquals("record", body.getString("preferred_chat_model_id"))
            assertEquals("high", body.getString("preferred_reasoning_effort"))
            assertEquals("r7", body.getString("expected_model_preference_revision"))
            assertEquals("r8", updated.modelPreferenceRevision)
            assertEquals("chat/1", updated.id)
        }
    }

    @Test fun newChatSeedsTheLatestPairThenFallsBackToTheBotDefault() = runTest {
        val api = api()
        coEvery { api.teamChatModels() } returns catalog()
        val controller = MemohAgentController(api)
        controller.loadModels()
        controller.setBotDefault("model-record", "medium")
        controller.setWelcomeSeed("seeded-record", "high", apply = true)
        assertEquals("seeded-record", controller.preferredModelId)
        assertEquals("high", controller.preferredReasoningEffort)
        // A seed with no effort still lands on the bot's own effort, not on an empty override.
        controller.setWelcomeSeed("seeded-record", "", apply = true)
        assertEquals("medium", controller.preferredReasoningEffort)
        // No seed: the composer shows the bot default but carries no override, so the next
        // new conversation still follows a changed bot default.
        controller.setWelcomeSeed("", "", apply = true)
        assertEquals("", controller.preferredModelId)
        assertEquals("model-record", controller.currentModelId)
        // Opening a real conversation restores that conversation's pair, never the seed.
        controller.beginConversation(MemohSession("chat", "Chat", preferredReasoningEffort = "low"))
        assertEquals("", controller.preferredModelId)
        assertEquals("low", controller.preferredReasoningEffort)
    }

    @Test fun switchingModelsReconcilesAStrandedEffortLikeTheWebComposer() {
        val model = JSONObject("""{"reasoning":{"supported":true,"can_disable":true,
                         "efforts":["minimal","high"],"default_effort":"high"}}""")
        assertEquals("high", model.reconcileComposerReasoningEffort("xhigh"))
        assertEquals("disable", model.reconcileComposerReasoningEffort("none"))
        assertEquals("minimal", model.reconcileComposerReasoningEffort("minimal"))
        // An empty override means "inherit", so it must stay empty.
        assertEquals("", model.reconcileComposerReasoningEffort(""))
        val offUnavailable = JSONObject("""{"reasoning":{"supported":true,"can_disable":false,
                         "efforts":["low"],"default_effort":"low"}}""")
        assertEquals("low", offUnavailable.reconcileComposerReasoningEffort("disable"))
        // A model without thinking keeps the dormant preference instead of erasing it.
        val thinkingFree = JSONObject("""{"reasoning":{"supported":false}}""")
        assertEquals("high", thinkingFree.reconcileComposerReasoningEffort("high"))
    }

    @Test fun resourcePickerUsesUserIdsAndNamesInsteadOfRequiringTypedIdentifiers() {
        val choices = resourceChoices(JSONObject("""{"candidates":[{"user_id":"u1","display_name":"First user"},{"id":"u1","username":"duplicate"},{"username":"no id"}]}"""))
        assertEquals(listOf(MemohResourceChoice("u1", "First user")), choices)
    }

    @Test fun modelPickerFiltersWrongTypesAndUnavailableModelsButPreservesCurrentValue() {
        val choices = resourceChoices(JSONObject("""{"items":[{"id":"embed","name":"Embedding","type":"embedding"},{"id":"chat","name":"Chat","type":"chat"},{"id":"off","name":"Disabled","type":"embedding","enable":false},{"id":"missing","name":"Unavailable","type":"embedding","config":{"catalog_available":false}}]}"""), "embedding", "off")
        assertEquals(listOf("embed", "off"), choices.map { it.id })
    }
    @Test fun resourceSearchEncodesQueryAndPreservesExistingFiltersAndCursor() {
        assertEquals("/users?q=Lin%20%2B%20team", resourcePickerPath("/users", query = " Lin + team "))
        assertEquals("/sessions?limit=50&cursor=a%2Bb%2Fc%3D&q=Lin", resourcePickerPath("/sessions?limit=50", "a+b/c=", "Lin"))
        assertEquals("/models", resourcePickerPath("/models"))
    }

}
