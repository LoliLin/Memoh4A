package com.newoether.agora.memoh

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bot model settings parity with the web console's Interaction / Context & Memory /
 * Multimedia groups: only changed keys travel, the effort follows the chosen model, and
 * an unusable Agent can never be saved as the default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemohBotModelSettingsTest {
    private fun settings(json: String) = decodeBotModelSettings(JSONObject(json))

    @Test fun patchSendsOnlyChangedKeysAndCanClearASelection() {
        val original = settings(
            """
            {"chat_model_id":"m1","reasoning_effort":"high","default_bot_agent_id":"a1",
             "search_provider_id":"s1","fetch_provider_id":"f1","memory_enabled":true,
             "tts_model_id":"t1","transcription_model_id":"tr1","image_model_id":"i1",
             "video_model_id":"v1","show_tool_calls_in_im":false}
            """,
        )
        val untouched = memohBotModelSettingsPatch(original, original.copy())
        assertEquals(0, untouched.length())

        val edited = memohBotModelSettingsPatch(
            original,
            original.copy(chatModelId = "m2", searchProviderId = "", memoryEnabled = false),
        )
        assertEquals(setOf("chat_model_id", "search_provider_id", "memory_enabled"), bodyKeys(edited))
        assertEquals("m2", edited.getString("chat_model_id"))
        // An empty string is the server's "clear", which must be sent rather than omitted.
        assertEquals("", edited.getString("search_provider_id"))
        assertFalse(edited.getBoolean("memory_enabled"))
    }

    @Test fun missingReasoningFallsBackToTheServerDefault() {
        assertEquals("medium", settings("""{"chat_model_id":"m1"}""").reasoningEffort)
        assertEquals("low", settings("""{"reasoning_effort":"low"}""").reasoningEffort)
    }

    @Test fun choosingMemohAgentClearsTheRuntimeAndDirectAgentKeepsIt() {
        val original = settings("""{"default_bot_agent_id":"a1"}""")
        val direct = MemohAgent("a2", "Codex", "codex", true, true, "", JSONObject())
        val directBody = memohDefaultAgentPatch(original, original.copy(defaultBotAgentId = "a2"), direct)
        assertEquals("a2", directBody.getString("default_bot_agent_id"))
        assertEquals("codex", directBody.getString("chat_runtime"))
        assertEquals("", directBody.getString("chat_acp_agent_id"))
        // A direct runtime carries no ACP project settings.
        assertFalse(directBody.has("chat_acp_project_path"))

        val acp = MemohAgent("a3", "Hermes", "acp", true, true, "", JSONObject().put("provider", "hermes"))
        val acpBody = memohDefaultAgentPatch(original, original.copy(defaultBotAgentId = "a3"), acp)
        assertEquals("acp_agent", acpBody.getString("chat_runtime"))
        assertEquals("hermes", acpBody.getString("chat_acp_agent_id"))
        assertEquals("/data", acpBody.getString("chat_acp_project_path"))
        assertEquals("project", acpBody.getString("chat_acp_project_mode"))

        val memoh = memohDefaultAgentPatch(original, original.copy(defaultBotAgentId = ""), null)
        assertEquals("", memoh.getString("default_bot_agent_id"))
        assertEquals("model", memoh.getString("chat_runtime"))
        assertEquals("", memoh.getString("chat_acp_agent_id"))

        // No change means no write at all.
        assertEquals(0, memohDefaultAgentPatch(original, original, direct).length())
    }

    @Test fun onlyConfiguredAndEnabledAgentsAreSelectable() {
        val profiles = listOf(MemohAgentProfile("hermes", "Hermes", ""))
        val agents = listOf(
            MemohAgent("codex", "Codex", "codex", true, true, "", JSONObject()),
            MemohAgent("codex-off", "Codex (disabled)", "codex", false, true, "", JSONObject()),
            MemohAgent("claude", "Claude Code", "claude-code", true, false, "", JSONObject().put("auth", "api_key")),
            MemohAgent("claude-workspace", "Claude (workspace)", "claude-code", true, false, "", JSONObject().put("auth", "workspace")),
            MemohAgent("hermes", "Hermes", "acp", true, false, "", JSONObject().put("provider", "hermes")),
            MemohAgent("orphan", "No profile", "acp", true, false, "", JSONObject().put("provider", "missing")),
        )
        val ids = memohDefaultAgentChoices(agents, profiles, "").map { it.id }
        assertEquals(listOf("codex", "claude-workspace", "hermes"), ids)
        // A saved-but-broken agent stays visible so the page can warn instead of hiding it.
        assertTrue(memohDefaultAgentChoices(agents, profiles, "orphan").any { it.id == "orphan" })
    }

    @Test fun modelsFollowTheServersTypeAndModalityRules() {
        val models = listOf(
            JSONObject().put("id", "chat").put("type", "chat").put("enable", true),
            JSONObject().put("id", "off").put("type", "chat").put("enable", false),
            JSONObject().put("id", "gone").put("type", "chat").put("enable", true)
                .put("config", JSONObject().put("catalog_available", false)),
            JSONObject().put("id", "embed").put("type", "embedding").put("enable", true),
            JSONObject().put("id", "image").put("type", "chat").put("enable", true).put(
                "config",
                JSONObject().put("modalities", JSONObject().put("output", JSONArray().put("text").put("image"))),
            ),
        )
        assertEquals(listOf("chat", "image"), memohChatModelChoices(models).map { it.optString("id") })
        assertEquals(listOf("image"), memohImageModelChoices(models).map { it.optString("id") })
    }

    @Test fun switchingTheChatModelReconcilesTheStoredTier() {
        val models = listOf(
            JSONObject("""{"id":"a","reasoning":{"supported":true,"can_disable":true,
                "efforts":["minimal","high"],"default_effort":"high"}}"""),
            JSONObject("""{"id":"plain","reasoning":{"supported":false}}"""),
        )
        assertEquals("high", memohReconciledEffort(models, "a", "xhigh"))
        assertEquals("disable", memohReconciledEffort(models, "a", "none"))
        // A model with no thinking keeps the dormant value instead of wiping it.
        assertEquals("high", memohReconciledEffort(models, "plain", "high"))
        // An unknown model cannot be reconciled, so nothing is invented.
        assertEquals("xhigh", memohReconciledEffort(models, "unknown", "xhigh"))
        assertEquals("high", memohModelDefaultEffort(models, "a"))
        assertEquals(listOf("disable", "minimal", "high"), memohModelEfforts(models, "a"))
    }

    @Test fun providerAndVoiceListsKeepTheirIdentifiers() {
        val providers = memohProviderChoicesFrom(
            JSONObject().put("items", JSONArray("""
                [{"id":"s1","name":"Exa","provider":"exa","enable":true},
                 {"id":"s2","name":"Off","enable":false},
                 {"id":"","name":"No id"}]
            """)),
            enabledOnly = true,
        )
        assertEquals(listOf(MemohNamedChoice("s1", "Exa")), providers)
        // A provider with no name falls back to its provider id, then to the row id.
        val fallback = memohProviderChoicesFrom(
            JSONObject().put("items", JSONArray("""[{"id":"f1","provider":"native"}]""")),
            enabledOnly = true,
        )
        assertEquals("native", fallback.single().label)

        val voice = memohVoiceModelChoices(
            JSONObject().put("items", JSONArray("""[{"id":"t1","name":"","model_id":"tts-1"}]""")),
        )
        assertEquals(MemohNamedChoice("t1", "tts-1"), voice.single())
    }

    private fun bodyKeys(body: JSONObject): Set<String> = body.keys().asSequence().toSet()
}
