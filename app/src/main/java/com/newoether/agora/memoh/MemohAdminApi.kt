package com.newoether.agora.memoh

import android.net.Uri
import org.json.JSONObject

/**
 * Server-side provider/model administration that the Memoh web console exposes.
 *
 * These wrap the team platform API for the settings surface. On Memoh Cloud many of
 * these routes answer 403 for ordinary members; the UI treats that as "managed by
 * your administrator" rather than an error state.
 */
internal class MemohAdminApi(parent: MemohTransport? = null) : MemohTransport(parent) {

    // ---- LLM providers ----

    suspend fun providers(): JSONObject = request("/providers")

    suspend fun provider(id: String): JSONObject = request("/providers/${Uri.encode(id)}")

    suspend fun createProvider(body: JSONObject): JSONObject = request("/providers", body)

    suspend fun updateProvider(id: String, body: JSONObject): JSONObject =
        request("/providers/${Uri.encode(id)}", body, method = "PUT")

    suspend fun deleteProvider(id: String) {
        request("/providers/${Uri.encode(id)}", method = "DELETE")
    }

    suspend fun providerModels(id: String): JSONObject = request("/providers/${Uri.encode(id)}/models")

    suspend fun importProviderModels(id: String): JSONObject =
        request("/providers/${Uri.encode(id)}/import-models", JSONObject(), method = "POST")

    suspend fun testProvider(id: String): JSONObject =
        request("/providers/${Uri.encode(id)}/test", JSONObject(), method = "POST")

    suspend fun providerTemplates(): JSONObject = request("/provider-templates")

    suspend fun providerTemplate(id: String): JSONObject = request("/provider-templates/${Uri.encode(id)}")

    // ---- Models ----

    suspend fun models(): JSONObject = request("/models")

    suspend fun model(id: String): JSONObject = request("/models/${Uri.encode(id)}")

    suspend fun createModel(body: JSONObject): JSONObject = request("/models", body)

    suspend fun updateModel(id: String, body: JSONObject): JSONObject =
        request("/models/${Uri.encode(id)}", body, method = "PUT")

    suspend fun deleteModel(id: String) {
        request("/models/${Uri.encode(id)}", method = "DELETE")
    }

    suspend fun testModel(id: String): JSONObject =
        request("/models/${Uri.encode(id)}/test", JSONObject(), method = "POST")

    suspend fun deleteModelByModelId(modelId: String) {
        request("/models/model/${Uri.encode(modelId)}", method = "DELETE")
    }

    // ---- Search / fetch providers ----

    suspend fun searchProviders(): JSONObject = request("/search-providers")

    suspend fun searchProviderMeta(): JSONObject = request("/search-providers/meta")

    suspend fun createSearchProvider(body: JSONObject): JSONObject = request("/search-providers", body)

    suspend fun updateSearchProvider(id: String, body: JSONObject): JSONObject =
        request("/search-providers/${Uri.encode(id)}", body, method = "PUT")

    suspend fun deleteSearchProvider(id: String) {
        request("/search-providers/${Uri.encode(id)}", method = "DELETE")
    }

    suspend fun fetchProviders(): JSONObject = request("/fetch-providers")

    suspend fun fetchProviderMeta(): JSONObject = request("/fetch-providers/meta")

    suspend fun createFetchProvider(body: JSONObject): JSONObject = request("/fetch-providers", body)

    suspend fun updateFetchProvider(id: String, body: JSONObject): JSONObject =
        request("/fetch-providers/${Uri.encode(id)}", body, method = "PUT")

    suspend fun deleteFetchProvider(id: String) {
        request("/fetch-providers/${Uri.encode(id)}", method = "DELETE")
    }

    // ---- Speech / transcription / video ----

    suspend fun speechProviders(): JSONObject = request("/speech-providers")

    suspend fun speechProviderModels(id: String): JSONObject =
        request("/speech-providers/${Uri.encode(id)}/models")

    suspend fun speechModels(): JSONObject = request("/speech-models")

    suspend fun updateSpeechModel(id: String, body: JSONObject): JSONObject =
        request("/speech-models/${Uri.encode(id)}", body, method = "PUT")

    suspend fun transcriptionProviders(): JSONObject = request("/transcription-providers")

    suspend fun transcriptionModels(): JSONObject = request("/transcription-models")

    suspend fun updateTranscriptionModel(id: String, body: JSONObject): JSONObject =
        request("/transcription-models/${Uri.encode(id)}", body, method = "PUT")

    suspend fun videoProviders(): JSONObject = request("/video-providers")

    suspend fun videoModels(): JSONObject = request("/video-models")

    suspend fun updateVideoModel(id: String, body: JSONObject): JSONObject =
        request("/video-models/${Uri.encode(id)}", body, method = "PUT")

    // ---- Connectors (bot-scoped) ----

    suspend fun connectors(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/connectors")

    suspend fun connectorCatalog(): JSONObject = request("/connectors/catalog")

    suspend fun connector(botId: String, connectionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/connectors/${Uri.encode(connectionId)}")

    suspend fun setConnectorEnabled(botId: String, connectionId: String, enabled: Boolean): JSONObject =
        request("/bots/${Uri.encode(botId)}/connectors/${Uri.encode(connectionId)}",
            JSONObject().put("enabled", enabled), method = "PATCH")

    suspend fun disconnectConnector(botId: String, connectionId: String) {
        request("/bots/${Uri.encode(botId)}/connectors/${Uri.encode(connectionId)}", method = "DELETE")
    }

    suspend fun reauthConnector(botId: String, connectionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/connectors/${Uri.encode(connectionId)}/reauth",
            JSONObject(), method = "POST")

    // ---- MCP OAuth + import/export ----

    suspend fun mcpOAuthStatus(botId: String, id: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/oauth/status")

    suspend fun mcpOAuthDiscover(botId: String, id: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/oauth/discover",
            JSONObject(), method = "POST")

    suspend fun mcpOAuthAuthorize(botId: String, id: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/oauth/authorize",
            JSONObject(), method = "POST")

    suspend fun mcpOAuthExchange(botId: String, id: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/oauth/exchange", body, method = "POST")

    suspend fun revokeMcpTokens(botId: String, id: String) {
        request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/oauth/token", method = "DELETE")
    }

    suspend fun exportMcpConnections(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp-ops/export")

    suspend fun importMcpConnections(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp-ops/import", body, method = "PUT")

    suspend fun batchDeleteMcpConnections(botId: String, ids: List<String>): JSONObject =
        request("/bots/${Uri.encode(botId)}/mcp-ops/batch-delete",
            JSONObject().put("ids", org.json.JSONArray(ids)), method = "POST")

    // ---- Bot ACL rules (channel access control) ----

    suspend fun aclRules(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/rules")

    suspend fun createAclRule(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/rules", body)

    suspend fun updateAclRule(botId: String, ruleId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/rules/${Uri.encode(ruleId)}", body, method = "PUT")

    suspend fun deleteAclRule(botId: String, ruleId: String) {
        request("/bots/${Uri.encode(botId)}/acl/rules/${Uri.encode(ruleId)}", method = "DELETE")
    }

    suspend fun aclDefaultEffect(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/default-effect")

    suspend fun setAclDefaultEffect(botId: String, effect: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/default-effect",
            JSONObject().put("effect", effect), method = "PUT")

    // ---- Supermarket (skill apps marketplace) ----

    suspend fun supermarketApps(): JSONObject = request("/supermarket/apps")

    suspend fun supermarketSkills(): JSONObject = request("/supermarket/skills")

    suspend fun supermarketCategories(): JSONObject = request("/supermarket/categories")

    suspend fun supermarketRegistries(): JSONObject = request("/supermarket/registries")

    suspend fun supermarketRegistryApps(registryId: String): JSONObject =
        request("/supermarket/registries/${Uri.encode(registryId)}/apps")

    // ---- Misc ----

    suspend fun siteIcon(url: String): JSONObject = request("/site-icon?url=${Uri.encode(url)}")

    suspend fun ping(): JSONObject = request("/ping")

    suspend fun botHooksEvents(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/hooks/events")

    suspend fun testBotHooks(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/hooks/test", body, method = "POST")

    suspend fun quickAction(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/quick-actions/execute", body, method = "POST")

    suspend fun userChannelIdentityList(): JSONObject = request("/users/me/channel-identities")

    suspend fun disconnectChannelIdentity(id: String) {
        request("/users/me/channel-identities/${Uri.encode(id)}", method = "DELETE")
    }

    suspend fun channelCapabilities(): JSONObject = request("/channels")

    suspend fun computerAccessGrants(): JSONObject = request("/users/me/computer-access")

    // ---- Container lifecycle (start/stop, snapshots, metrics, dependencies) ----

    suspend fun startContainer(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/start", JSONObject(), method = "POST")

    suspend fun stopContainer(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/stop", JSONObject(), method = "POST")

    suspend fun containerMetrics(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/metrics")

    suspend fun updateContainerMetrics(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/metrics", body, method = "PUT")

    suspend fun containerSnapshots(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/snapshots")

    suspend fun createContainerSnapshot(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/snapshots", JSONObject(), method = "POST")

    suspend fun rollbackContainerSnapshot(botId: String, version: Int): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/snapshots/rollback",
            JSONObject().put("version", version), method = "POST")

    suspend fun cancelContainerRemoval(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/removal/cancel", JSONObject(), method = "POST")

    suspend fun restoreContainerData(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/data/restore", body, method = "POST")

    suspend fun containerDependencies(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies")

    suspend fun checkDependencyUpdates(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/check-updates", JSONObject(), method = "POST")

    suspend fun installDependency(botId: String, depId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/${Uri.encode(depId)}/install",
            JSONObject(), method = "POST")

    suspend fun updateDependency(botId: String, depId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/${Uri.encode(depId)}/update",
            JSONObject(), method = "POST")

    suspend fun rollbackDependency(botId: String, depId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/${Uri.encode(depId)}/rollback",
            JSONObject(), method = "POST")

    suspend fun reinstallDependency(botId: String, depId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/${Uri.encode(depId)}/reinstall",
            JSONObject(), method = "POST")

    suspend fun dependencyScript(botId: String, depId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/${Uri.encode(depId)}/script")

    suspend fun dependencyPreflight(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/dependencies/preflight", JSONObject(), method = "POST")

    suspend fun containerFsInfo(botId: String, path: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/fs?path=${Uri.encode(path)}")

    suspend fun containerTerminalAvailability(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/terminal")

    suspend fun workspaceDependencyMetadata(): JSONObject = request("/workspace-dependencies")

    // ---- ACP runtimes (unbound pre-session picker) ----

    suspend fun createAcpRuntime(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/acp-runtimes", body)

    suspend fun acpRuntime(botId: String, runtimeId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acp-runtimes/${Uri.encode(runtimeId)}")

    suspend fun closeAcpRuntime(botId: String, runtimeId: String) {
        request("/bots/${Uri.encode(botId)}/acp-runtimes/${Uri.encode(runtimeId)}", method = "DELETE")
    }

    suspend fun setAcpRuntimeMode(botId: String, runtimeId: String, modeId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acp-runtimes/${Uri.encode(runtimeId)}/mode",
            JSONObject().put("mode_id", modeId), method = "PATCH")

    suspend fun setAcpRuntimeModel(botId: String, runtimeId: String, modelId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acp-runtimes/${Uri.encode(runtimeId)}/model",
            JSONObject().put("model_id", modelId), method = "PATCH")

    suspend fun setAcpRuntimeReasoning(botId: String, runtimeId: String, effort: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acp-runtimes/${Uri.encode(runtimeId)}/reasoning",
            JSONObject().put("reasoning_effort", effort), method = "PATCH")

    // ---- Provider OAuth (device flow for self-hosted LLM providers) ----

    suspend fun providerOAuthStatus(id: String): JSONObject =
        request("/providers/${Uri.encode(id)}/oauth/status")

    suspend fun providerOAuthAuthorize(id: String): JSONObject =
        request("/providers/${Uri.encode(id)}/oauth/authorize")

    suspend fun providerOAuthPoll(id: String): JSONObject =
        request("/providers/${Uri.encode(id)}/oauth/poll", JSONObject(), method = "POST")

    suspend fun revokeProviderTokens(id: String) {
        request("/providers/${Uri.encode(id)}/oauth/token", method = "DELETE")
    }

    suspend fun createProviderFromTemplate(body: JSONObject): JSONObject =
        request("/providers/from-template", body)

    suspend fun providerByName(name: String): JSONObject =
        request("/providers/name/${Uri.encode(name)}")

    suspend fun providersCount(): JSONObject = request("/providers/count")

    suspend fun modelsCount(): JSONObject = request("/models/count")

    // ---- Agent authorizations (independent Claude browser flow) ----

    suspend fun startAgentAuthorization(body: JSONObject): JSONObject =
        request("/agent-authorizations", body)

    suspend fun agentAuthorization(id: String): JSONObject =
        request("/agent-authorizations/${Uri.encode(id)}")

    suspend fun discardAgentAuthorization(id: String) {
        request("/agent-authorizations/${Uri.encode(id)}", method = "DELETE")
    }

    suspend fun pollAgentAuthorization(id: String): JSONObject =
        request("/agent-authorizations/${Uri.encode(id)}/poll", JSONObject(), method = "POST")

    suspend fun exchangeAgentAuthorization(id: String, body: JSONObject): JSONObject =
        request("/agent-authorizations/${Uri.encode(id)}/exchange", body, method = "POST")

    // ---- Bot channel administration ----

    suspend fun channelManagers(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel-managers")

    suspend fun setChannelManager(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel-managers", body)

    suspend fun clearChannelManager(botId: String, channelIdentityId: String) {
        request("/bots/${Uri.encode(botId)}/channel-managers/${Uri.encode(channelIdentityId)}",
            method = "DELETE")
    }

    suspend fun aclChannelIdentityCandidates(botId: String, query: String, limit: Int = 20): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/channel-identities?q=${Uri.encode(query)}&limit=$limit")

    suspend fun aclChannelIdentityConversations(botId: String, channelIdentityId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/channel-identities/${Uri.encode(channelIdentityId)}/conversations")

    suspend fun aclChannelTypeConversations(botId: String, channelType: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/acl/channel-types/${Uri.encode(channelType)}/conversations")

    suspend fun setChannelWebhookEndpoint(botId: String, platform: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}/webhook-endpoint", body, method = "POST")

    suspend fun botNameAvailability(name: String): JSONObject =
        request("/bots/name-availability?name=${Uri.encode(name)}")

    suspend fun transferBotOwner(botId: String, userId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/owner", JSONObject().put("user_id", userId), method = "PUT")

    // ---- Apps extras ----

    suspend fun appRemovalPreview(botId: String, installationId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/apps/${Uri.encode(installationId)}/removal-preview")

    suspend fun resumeAppInstall(botId: String, installationId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/apps/${Uri.encode(installationId)}/resume",
            JSONObject(), method = "POST")

    suspend fun updateApp(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/apps/update", body, method = "POST")

    // ---- Speech / transcription / video extras ----

    suspend fun speechModelCapabilities(id: String): JSONObject =
        request("/speech-models/${Uri.encode(id)}/capabilities")

    suspend fun testSpeechModel(id: String): JSONObject =
        request("/speech-models/${Uri.encode(id)}/test", JSONObject(), method = "POST")

    suspend fun importSpeechModels(providerId: String): JSONObject =
        request("/speech-providers/${Uri.encode(providerId)}/import-models",
            JSONObject(), method = "POST")

    suspend fun speechProviderMeta(): JSONObject = request("/speech-providers/meta")

    suspend fun transcriptionModelCapabilities(id: String): JSONObject =
        request("/transcription-models/${Uri.encode(id)}/capabilities")

    suspend fun testTranscriptionModel(id: String): JSONObject =
        request("/transcription-models/${Uri.encode(id)}/test", JSONObject(), method = "POST")

    suspend fun importTranscriptionModels(providerId: String): JSONObject =
        request("/transcription-providers/${Uri.encode(providerId)}/import-models",
            JSONObject(), method = "POST")

    suspend fun transcriptionProviderMeta(): JSONObject = request("/transcription-providers/meta")

    suspend fun importVideoModels(providerId: String): JSONObject =
        request("/video-providers/${Uri.encode(providerId)}/import-models",
            JSONObject(), method = "POST")

    suspend fun videoProviderModels(providerId: String): JSONObject =
        request("/video-providers/${Uri.encode(providerId)}/models")

    suspend fun videoProviderMeta(): JSONObject = request("/video-providers/meta")

    // ---- Supermarket extras ----

    suspend fun supermarketSkillIcon(digest: String) {
        request("/supermarket/artifacts/icon/${Uri.encode(digest)}")
    }

    suspend fun supermarketAppRelease(registryId: String, appId: String, revision: String): JSONObject =
        request("/supermarket/registries/${Uri.encode(registryId)}/apps/${Uri.encode(appId)}/releases/${Uri.encode(revision)}")

    suspend fun supermarketRegistrySkill(registryId: String, appId: String, skillId: String): JSONObject =
        request("/supermarket/registries/${Uri.encode(registryId)}/apps/${Uri.encode(appId)}/skills/${Uri.encode(skillId)}")

    // ---- Users / channel admin ----

    suspend fun listUsers(): JSONObject = request("/users")

    suspend fun createUser(body: JSONObject): JSONObject = request("/users", body)

    suspend fun issueChannelLinkCode(body: JSONObject): JSONObject =
        request("/users/me/channel-links", body)

    suspend fun channelUserConfig(platform: String): JSONObject =
        request("/users/me/channels/${Uri.encode(platform)}")

    suspend fun updateChannelUserConfig(platform: String, body: JSONObject): JSONObject =
        request("/users/me/channels/${Uri.encode(platform)}", body, method = "PUT")

    suspend fun channelCapabilities(platform: String): JSONObject =
        request("/channels/${Uri.encode(platform)}")

    suspend fun webhookTunnelStatus(): JSONObject = request("/webhook-tunnel/status")

    // ---- Remote runtime workspace targets ----

    suspend fun upsertRemoteWorkspaceTarget(botId: String, runtimeId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/workspace-targets/remotes/${Uri.encode(runtimeId)}",
            body, method = "PUT")

    // ---- External agents ----

    suspend fun codexUsage(botId: String, agentId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/codex/usage")

    suspend fun agentRuntimeControls(botId: String, agentId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/runtime-controls")

    suspend fun claimAgentAuthorization(botId: String, agentId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/credential/claim",
            body, method = "POST")

    // ---- Container browser proxy & workspace display management ----

    suspend fun createBrowserSession(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/browser/sessions", JSONObject(), method = "POST")

    suspend fun deleteBrowserSession(botId: String, sessionId: String) {
        request("/bots/${Uri.encode(botId)}/container/browser/sessions/${Uri.encode(sessionId)}",
            method = "DELETE")
    }

    suspend fun keepBrowserSessionAlive(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/browser/sessions/${Uri.encode(sessionId)}/keepalive",
            JSONObject(), method = "POST")

    suspend fun displayAvailability(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/display")

    suspend fun prepareDisplay(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/display/prepare", JSONObject(), method = "POST")

    suspend fun displaySessions(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/display/sessions")

    suspend fun closeDisplaySession(botId: String, sessionId: String) {
        request("/bots/${Uri.encode(botId)}/container/display/sessions/${Uri.encode(sessionId)}",
            method = "DELETE")
    }

    suspend fun displayWebRtcOffer(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/display/webrtc/offer", body)

    // ---- Skill source management ----

    suspend fun applySkillSourceAction(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/container/skills/actions", body)

    suspend fun workspaceDependencyIcon(digest: String) {
        request("/workspace-dependencies/icons/${Uri.encode(digest)}")
    }

    // ---- App connectors ----

    suspend fun connectAppApiKey(botId: String, installationId: String, connectorType: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/apps/${Uri.encode(installationId)}/connectors/${Uri.encode(connectorType)}/api-key",
            body)

    suspend fun authorizeAppConnector(botId: String, installationId: String, connectorType: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/apps/${Uri.encode(installationId)}/connectors/${Uri.encode(connectorType)}/oauth",
            body)

    // ---- User access candidates for bot creation ----

    suspend fun userAccessCandidatesGlobal(query: String): JSONObject =
        request("/bots/user-access/candidates?q=${Uri.encode(query)}")

    // ---- Self-hosted token refresh ----

    /** Exchange a refresh token for a new access token (self-hosted bearer deployments). */
    suspend fun refreshAuthToken(): JSONObject =
        request("/auth/refresh", JSONObject(), method = "POST")

    suspend fun transcriptionProviderModels(providerId: String): JSONObject =
        request("/transcription-providers/${Uri.encode(providerId)}/models")
}
