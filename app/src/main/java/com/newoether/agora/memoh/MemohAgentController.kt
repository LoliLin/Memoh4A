package com.newoether.agora.memoh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/**
 * Server-owned Agent catalog and configuration. A chat session binds to one
 * Agent at creation time; changing Agent starts a new draft instead of
 * silently changing the runtime of an existing conversation.
 */
/**
 * [agentsFallback] / [profilesFallback] are the localized messages this controller shows when the
 * server returns no text of its own; it has no Context of its own to look them up with.
 */
internal class MemohAgentController(
    private val api: MemohApi,
    private val agentsFallback: () -> String = { "Failed to load Agents" },
    private val profilesFallback: () -> String = { "ACP Agent profiles are unavailable" },
) {
    var agents by mutableStateOf<List<MemohAgent>>(emptyList())
        private set
    var profiles by mutableStateOf<List<MemohAgentProfile>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var selectedAgentId by mutableStateOf("")
        private set
    // Chat-time model preference for the next send (web composer parity).
    var models by mutableStateOf<JSONObject?>(null)
        private set
    var modelsLoading by mutableStateOf(false)
        private set
    var preferredModelId by mutableStateOf("")
    var preferredReasoningEffort by mutableStateOf("")
    /** Server-confirmed preference from model_preference_settled events. */
    var settledModelId by mutableStateOf("")
        private set
    var botDefaultModelId by mutableStateOf("")
        private set
    var botDefaultReasoningEffort by mutableStateOf("")
        private set
    private var welcomeSeedModelId = ""
    private var welcomeSeedEffort = ""
    private var modelLabelHint by mutableStateOf("")
    var preferenceRevision = 0
        private set
    private var catalogGeneration = 0

    val currentModelId: String
        get() = preferredModelId.ifBlank { settledModelId.ifBlank { botDefaultModelId } }

    val currentModelLabel: String
        get() {
            val entries = models?.optJSONArray("models")
            val selected = entries?.let { list ->
                (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                    .firstOrNull { it.optString("id") == currentModelId || it.optString("model_id") == currentModelId }
            }
            return selected?.optString("name").orEmpty()
                .ifBlank { selected?.optString("model_id").orEmpty() }
                .ifBlank { modelLabelHint }
        }

    /** Opening/repointing a conversation restores the persisted pair, never another chat's picker. */
    fun beginConversation(session: MemohSession?) {
        preferenceRevision++
        preferredModelId = session?.preferredChatModelId.orEmpty()
            .ifBlank { session?.preferredExternalModelId.orEmpty() }
        preferredReasoningEffort = session?.preferredReasoningEffort.orEmpty()
        modelLabelHint = session?.preferredExternalModelId.orEmpty()
        settledModelId = ""
        if (session == null) applyWelcomeSeed()
    }

    fun setBotDefault(modelId: String, effort: String = "") {
        botDefaultModelId = modelId
        botDefaultReasoningEffort = effort
    }

    /** The server seed belongs to the latest native session, not to the bot settings. */
    fun setWelcomeSeed(modelId: String, effort: String, apply: Boolean) {
        welcomeSeedModelId = modelId
        welcomeSeedEffort = effort
        if (apply) applyWelcomeSeed()
    }

    fun applyWelcomeSeed() {
        // The seed is a chat-model record id, so it only ever belongs on the native composer;
        // an external ACP agent owns its own models.
        if (selectedAgent?.runtime == "acp") return
        // Without a seed the composer follows the bot default but carries nothing on send,
        // so a later change to that default still reaches the next new conversation.
        if (welcomeSeedModelId.isBlank()) {
            preferredModelId = ""
            preferredReasoningEffort = ""
            modelLabelHint = ""
            return
        }
        preferredModelId = welcomeSeedModelId
        // Seeded pair first, then the bot's own effort, then the web composer's fallback.
        preferredReasoningEffort = welcomeSeedEffort
            .ifBlank { botDefaultReasoningEffort }
            .ifBlank { "medium" }
        modelLabelHint = ""
    }

    val selectedAgent: MemohAgent?
        get() = agents.firstOrNull { it.id == selectedAgentId }

    val options: List<MemohAgentOption>
        get() = buildList {
            add(MemohAgentOption("codex", "Codex", "codex", "OpenAI Codex runtime"))
            add(MemohAgentOption("claude-code", "Claude Code", "claude-code", "Anthropic coding runtime"))
            add(MemohAgentOption("grok-build", "Grok Build", "grok-build", "xAI coding runtime"))
            profiles.filter { it.id !in setOf("codex", "claude-code", "grok-build") }.forEach { profile ->
                add(MemohAgentOption(profile.id, profile.name, "acp", profile.description))
            }
        }

    fun select(id: String) {
        selectedAgentId = id.takeIf { candidate -> agents.any { it.id == candidate && it.enabled } }.orEmpty()
    }

    fun selectFromSession(id: String) {
        selectedAgentId = id
    }

    private var refreshGeneration = 0

    fun clear() {
        refreshGeneration++
        agents = emptyList()
        profiles = emptyList()
        selectedAgentId = ""
        error = ""
        loading = false
        models = null
        modelsLoading = false
        preferredModelId = ""
        preferredReasoningEffort = ""
        settledModelId = ""
        botDefaultModelId = ""
        botDefaultReasoningEffort = ""
        welcomeSeedModelId = ""
        welcomeSeedEffort = ""
        modelLabelHint = ""
        preferenceRevision++
        catalogGeneration++
    }

    suspend fun refresh(botId: String) {
        val generation = ++refreshGeneration
        val identity = api.scope
        fun current() = generation == refreshGeneration && api.scope === identity
        loading = true
        error = ""
        try {
            val updatedAgents = api.agents(botId)
            currentCoroutineContext().ensureActive()
            if (!current()) return
            agents = updatedAgents
            if (selectedAgentId.isNotBlank() && agents.none { it.id == selectedAgentId && it.enabled }) {
                selectedAgentId = ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (current()) error = e.message ?: agentsFallback()
            throw e
        } finally {
            if (current()) loading = false
        }
        try {
            if (!current()) return
            val updatedProfiles = api.agentProfiles()
            currentCoroutineContext().ensureActive()
            if (current()) profiles = updatedProfiles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (current() && profiles.isEmpty()) error = e.message ?: profilesFallback()
        }
    }

    /** The catalog and Agent refreshes have independent generations: either may finish first. */
    suspend fun loadModels() {
        val generation = ++catalogGeneration
        val identity = api.scope
        modelsLoading = true
        try {
            val catalog = api.teamChatModels()
            if (generation == catalogGeneration && api.scope === identity) {
                models = catalog
                // The catalog carries each model's resolved efforts; a restored preference
                // can only be checked once they are here.
                reconcileComposerPreference()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the server's remembered model usable without model-list permission.
        } finally {
            if (generation == catalogGeneration && api.scope === identity) modelsLoading = false
        }
    }

    fun catalogModel(id: String): JSONObject? {
        val entries = models?.optJSONArray("models") ?: return null
        return (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .firstOrNull { it.optString("id") == id || it.optString("model_id") == id }
    }

    /**
     * Switching models can strand the composer on a tier the new model does not offer.
     * An empty override means "inherit the bot default", so it is left alone.
     */
    fun reconcileComposerPreference() {
        if (preferredReasoningEffort.isEmpty()) return
        val model = catalogModel(currentModelId) ?: return
        val next = model.reconcileComposerReasoningEffort(preferredReasoningEffort)
        if (next.isNotEmpty() && next != preferredReasoningEffort) preferredReasoningEffort = next
    }

    fun setModelPreference(modelId: String, reasoningEffort: String) {
        preferenceRevision++
        modelLabelHint = ""
        preferredModelId = modelId
        preferredReasoningEffort = reasoningEffort
    }

    /** Record the server's own settled preference (model_preference_settled event). */
    fun onModelPreferenceSettled(modelId: String) {
        settledModelId = modelId
    }

    suspend fun create(botId: String, name: String, optionId: String): MemohAgent {
        val option = options.firstOrNull { it.id == optionId }
            ?: throw IllegalArgumentException("Choose a valid Agent runtime")
        val created = api.createAgent(botId, name, option)
        refresh(botId)
        selectedAgentId = created.id.takeIf { agents.any { it.id == created.id && it.enabled } }.orEmpty()
        return agents.firstOrNull { it.id == created.id } ?: created
    }

    suspend fun rename(botId: String, agent: MemohAgent, name: String) {
        require(name.isNotBlank()) { "Agent name is required" }
        api.updateAgent(botId, agent.id, name = name.trim())
        refresh(botId)
    }

    suspend fun setEnabled(botId: String, agent: MemohAgent, enabled: Boolean) {
        if (enabled && agent.runtime != "acp" && agent.runtime != "claude-code" &&
            !agent.credentialConnected && agent.metadata.optString("auth") !in setOf("chatgpt", "grok_account")) {
            throw IllegalStateException("Connect this Agent's account or API key before enabling it.")
        }
        api.updateAgent(botId, agent.id, enabled = enabled)
        refresh(botId)
    }

    suspend fun delete(botId: String, agent: MemohAgent) {
        api.deleteAgent(botId, agent.id)
        if (selectedAgentId == agent.id) selectedAgentId = ""
        refresh(botId)
    }

    suspend fun setAuthMode(botId: String, agent: MemohAgent, auth: String) {
        require(agent.runtime in setOf("codex", "claude-code", "grok-build")) {
            "This Agent uses a server-managed ACP profile."
        }
        if (agent.credentialConnected) {
            throw IllegalStateException("Disconnect the current credential before changing authentication mode.")
        }
        val allowed = when (agent.runtime) {
            "codex" -> setOf("chatgpt", "api_key")
            "claude-code" -> setOf("workspace", "api_key", "oauth_token")
            else -> setOf("grok_account", "api_key")
        }
        require(auth in allowed) { "Unsupported authentication mode for this runtime." }
        val metadata = JSONObject(agent.metadata.toString()).put("provider", agent.runtime).put("auth", auth)
        api.updateAgent(botId, agent.id, metadata = metadata)
        refresh(botId)
    }

    suspend fun saveCredential(botId: String, agent: MemohAgent, secret: String) {
        require(secret.isNotBlank()) { "Credential cannot be empty" }
        api.saveAgentCredential(botId, agent, secret)
        refresh(botId)
    }

    suspend fun disconnectCredential(botId: String, agent: MemohAgent) {
        api.deleteAgentCredential(botId, agent.id)
        refresh(botId)
    }

    suspend fun startDeviceLogin(botId: String, agent: MemohAgent): MemohDeviceLogin {
        require(
            (agent.runtime == "codex" && agent.metadata.optString("auth") == "chatgpt") ||
                (agent.runtime == "grok-build" && agent.metadata.optString("auth") == "grok_account"),
        ) { "This Agent does not use device-code login." }
        return api.startDeviceLogin(botId, agent.id)
    }

    suspend fun pollDeviceLogin(botId: String, agent: MemohAgent, loginId: String): MemohDeviceLoginResult =
        api.pollDeviceLogin(botId, agent.id, loginId)

    suspend fun cancelDeviceLogin(botId: String, agent: MemohAgent, loginId: String) {
        api.cancelDeviceLogin(botId, agent.id, loginId)
    }
}
