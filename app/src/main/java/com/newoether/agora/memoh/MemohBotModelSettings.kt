package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject

/**
 * The bot settings the web console groups as Interaction / Context & Memory / Multimedia.
 *
 * These were missing on Android: a bot could be named and given an avatar, but not told
 * which model answers, how hard it thinks, whether memory is on, or which providers serve
 * search, fetch, speech, transcription and video. Everything here mirrors
 * `pages/bots/components/bot-settings.vue` and `settings.Interaction` on the server.
 */
internal data class MemohBotModelSettings(
    val chatModelId: String = "",
    val reasoningEffort: String = "",
    val defaultBotAgentId: String = "",
    val showToolCallsInIm: Boolean = false,
    val searchProviderId: String = "",
    val fetchProviderId: String = "",
    val memoryEnabled: Boolean = false,
    val ttsModelId: String = "",
    val transcriptionModelId: String = "",
    val imageModelId: String = "",
    val videoModelId: String = "",
)

internal fun decodeBotModelSettings(settings: JSONObject): MemohBotModelSettings = MemohBotModelSettings(
    chatModelId = settings.optString("chat_model_id"),
    // The server's own default tier is "medium"; reading it back keeps the picker honest.
    reasoningEffort = settings.optString("reasoning_effort").ifBlank { "medium" },
    defaultBotAgentId = settings.optString("default_bot_agent_id"),
    showToolCallsInIm = settings.optBoolean("show_tool_calls_in_im"),
    searchProviderId = settings.optString("search_provider_id"),
    fetchProviderId = settings.optString("fetch_provider_id"),
    memoryEnabled = settings.optBoolean("memory_enabled"),
    ttsModelId = settings.optString("tts_model_id"),
    transcriptionModelId = settings.optString("transcription_model_id"),
    imageModelId = settings.optString("image_model_id"),
    videoModelId = settings.optString("video_model_id"),
)

/**
 * Only changed keys are sent. The server treats an omitted key as "keep" and an empty
 * string as "clear", so a full write would silently reset settings this page never shows
 * (memory LLM, discuss probe model, tool approval, display).
 */
internal fun memohBotModelSettingsPatch(
    original: MemohBotModelSettings,
    draft: MemohBotModelSettings,
): JSONObject {
    val body = JSONObject()
    fun putString(key: String, before: String, after: String) {
        if (before != after) body.put(key, after)
    }
    putString("chat_model_id", original.chatModelId, draft.chatModelId)
    putString("reasoning_effort", original.reasoningEffort, draft.reasoningEffort)
    putString("default_bot_agent_id", original.defaultBotAgentId, draft.defaultBotAgentId)
    putString("search_provider_id", original.searchProviderId, draft.searchProviderId)
    putString("fetch_provider_id", original.fetchProviderId, draft.fetchProviderId)
    putString("tts_model_id", original.ttsModelId, draft.ttsModelId)
    putString("transcription_model_id", original.transcriptionModelId, draft.transcriptionModelId)
    putString("image_model_id", original.imageModelId, draft.imageModelId)
    putString("video_model_id", original.videoModelId, draft.videoModelId)
    if (original.showToolCallsInIm != draft.showToolCallsInIm) {
        body.put("show_tool_calls_in_im", draft.showToolCallsInIm)
    }
    if (original.memoryEnabled != draft.memoryEnabled) body.put("memory_enabled", draft.memoryEnabled)
    return body
}

/** The default agent is stored as an id plus the runtime it belongs to. */
internal fun memohDefaultAgentPatch(
    original: MemohBotModelSettings,
    draft: MemohBotModelSettings,
    agent: MemohAgent?,
): JSONObject {
    if (original.defaultBotAgentId == draft.defaultBotAgentId) return JSONObject()
    val body = JSONObject().put("default_bot_agent_id", draft.defaultBotAgentId)
    // Choosing Memoh's own loop clears the runtime the previous agent pinned, and the
    // reverse: picking a direct agent records its runtime so a channel conversation
    // opens on the same runtime the picker shows.
    return when {
        agent == null -> body.put("chat_runtime", "model").put("chat_acp_agent_id", "")
        agent.runtime in setOf("codex", "claude-code", "grok-build") -> body
            .put("chat_runtime", agent.runtime)
            .put("chat_acp_agent_id", "")
        else -> body
            .put("chat_runtime", "acp_agent")
            .put("chat_acp_agent_id", agent.metadata.optString("provider").ifBlank { agent.runtime })
            .put("chat_acp_project_path", "/data")
            .put("chat_acp_project_mode", "project")
    }
}

internal fun memohModelsArray(response: JSONObject): List<JSONObject> {
    val array = response.optJSONArray("items") ?: response.optJSONArray("data")
        ?: response.optJSONArray("models") ?: JSONArray()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

/** Chat models the bot may pick as its default, in provider order like the web list. */
internal fun memohChatModelChoices(models: List<JSONObject>): List<JSONObject> =
    models.filter { model ->
        model.optString("type").ifBlank { "chat" } == "chat" &&
            model.optBoolean("enable", true) &&
            model.optJSONObject("config")?.optBoolean("catalog_available", true) != false
    }

/** The web offers an image model only among chat models whose output includes images. */
internal fun memohImageModelChoices(models: List<JSONObject>): List<JSONObject> =
    memohChatModelChoices(models).filter { model ->
        val output = model.optJSONObject("config")?.optJSONObject("modalities")?.optJSONArray("output")
            ?: return@filter false
        (0 until output.length()).any { output.optString(it) == "image" }
    }

/** A model row's own tier list, preferring the server's resolved reasoning options. */
internal fun memohModelEfforts(models: List<JSONObject>, modelId: String): List<String> =
    models.firstOrNull { it.optString("id") == modelId }?.reasoningEfforts().orEmpty()

internal fun memohModelDefaultEffort(models: List<JSONObject>, modelId: String): String =
    models.firstOrNull { it.optString("id") == modelId }?.reasoningDefault().orEmpty()

/** Switching the chat model must not strand the stored tier on a model that lacks it. */
internal fun memohReconciledEffort(models: List<JSONObject>, modelId: String, stored: String): String {
    val model = models.firstOrNull { it.optString("id") == modelId } ?: return stored
    val next = model.reconcileComposerReasoningEffort(stored)
    return next.ifBlank { stored }
}

internal data class MemohDefaultAgentChoice(
    val id: String,
    val label: String,
    val configured: Boolean,
)

/**
 * Memoh's own loop plus the bot's enabled Agents that are actually usable. The web hides an
 * agent whose credential is missing, so a saved-but-unconfigured agent cannot be picked.
 */
internal fun memohDefaultAgentChoices(
    agents: List<MemohAgent>,
    profiles: List<MemohAgentProfile>,
    currentId: String,
): List<MemohDefaultAgentChoice> {
    val profileIds = profiles.mapTo(mutableSetOf()) { it.id }
    return agents.filter { agent ->
        agent.enabled && agent.id.isNotBlank() && (agent.id == currentId || memohAgentConfigured(agent, profileIds))
    }.map { agent ->
        MemohDefaultAgentChoice(agent.id, agent.name.ifBlank { agent.runtime }, memohAgentConfigured(agent, profileIds))
    }
}

internal fun memohAgentConfigured(agent: MemohAgent, profileIds: Set<String>): Boolean {
    if (agent.runtime in setOf("codex", "claude-code", "grok-build")) {
        if (agent.credentialConnected) return true
        // A workspace login stores no credential of its own, so it still counts as set up.
        return agent.metadata.optString("auth") == "workspace"
    }
    val provider = agent.metadata.optString("provider").ifBlank { agent.runtime }
    return agent.credentialConnected || provider in profileIds
}
