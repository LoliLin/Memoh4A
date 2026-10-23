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
internal class MemohAgentController(private val api: MemohApi) {
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
            if (current()) error = e.message ?: "Failed to load Agents"
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
            if (current() && profiles.isEmpty()) error = e.message ?: "ACP Agent profiles are unavailable"
        }
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
