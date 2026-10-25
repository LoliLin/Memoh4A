package com.newoether.agora.memoh

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okio.BufferedSink
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Memoh is a remote runtime, not an OpenAI-compatible model provider.
 * The HTTP client never caches server-owned history as an Agora conversation.
 */
internal class MemohApi(parent: MemohTransport? = null) : MemohTransport(parent) {
    suspend fun account(): MemohAccount = decodeAccount(request("/users/me"))

    suspend fun updateAccount(displayName: String, avatarUrl: String, timezone: String, titleModelId: String): MemohAccount =
        decodeAccount(request("/users/me", JSONObject()
            .put("display_name", displayName.trim()).put("avatar_url", avatarUrl.trim())
            .put("timezone", timezone.trim().ifBlank { "UTC" }).put("title_model_id", titleModelId.trim()),
            method = "PUT"))

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        require(newPassword.length >= 8) { "New password must contain at least 8 characters." }
        request("/users/me/password", JSONObject().put("current_password", currentPassword)
            .put("new_password", newPassword), method = "PUT")
    }

    private fun decodeBot(item: JSONObject): MemohBot? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        val displayName = item.optString("display_name")
        val name = item.optString("name")
        val title = displayName.ifBlank { name.ifBlank { "Bot" } }
        val perms = item.optJSONArray("current_user_permissions")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        return MemohBot(
            id = id,
            title = title,
            name = name,
            displayName = displayName,
            avatarUrl = item.optString("avatar_url"),
            timezone = item.optString("timezone"),
            status = item.optString("status"),
            isActive = item.optBoolean("is_active", true),
            createdAt = item.optString("created_at"),
            currentUserPermissions = perms,
        )
    }

    suspend fun bots(): List<MemohBot> {
        val array = request("/bots").optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::decodeBot)
        }
    }

    suspend fun botContainer(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/container")

    suspend fun getBot(botId: String): MemohBot {
        val item = request("/bots/${Uri.encode(botId)}")
        return decodeBot(item) ?: throw IOException("Invalid bot response")
    }

    suspend fun updateBot(
        botId: String,
        displayName: String? = null,
        name: String? = null,
        avatarUrl: String? = null,
        timezone: String? = null,
        isActive: Boolean? = null,
    ): MemohBot {
        val body = JSONObject()
        displayName?.let { body.put("display_name", it.trim()) }
        name?.let { body.put("name", it.trim()) }
        avatarUrl?.let { body.put("avatar_url", it.trim()) }
        timezone?.let { body.put("timezone", it.trim()) }
        isActive?.let { body.put("is_active", it) }
        val item = request("/bots/${Uri.encode(botId)}", body, method = "PUT")
        return decodeBot(item) ?: throw IOException("Invalid bot response")
    }

    suspend fun deleteBot(botId: String) {
        request("/bots/${Uri.encode(botId)}", method = "DELETE")
    }

    suspend fun botUserGrants(botId: String): List<MemohUserGrant> {
        val array = request("/bots/${Uri.encode(botId)}/user-access").optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val perms = obj.optJSONArray("permissions")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
            } ?: emptyList()
            MemohUserGrant(
                id = id,
                botId = obj.optString("bot_id"),
                userId = obj.optString("user_id"),
                userDisplayName = obj.optString("user_display_name"),
                userUsername = obj.optString("user_username"),
                userAvatarUrl = obj.optString("user_avatar_url"),
                isOwner = obj.optBoolean("is_owner"),
                permissions = perms,
            )
        }
    }

    suspend fun botUserCandidates(botId: String, query: String = ""): List<MemohUserCandidate> {
        val path = "/bots/${Uri.encode(botId)}/user-access/candidates" +
            (if (query.isNotBlank()) "?q=${Uri.encode(query.trim())}" else "")
        val array = request(path).optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isBlank()) null else MemohUserCandidate(
                id = id,
                username = obj.optString("username"),
                displayName = obj.optString("display_name"),
                avatarUrl = obj.optString("avatar_url"),
            )
        }
    }

    suspend fun createBotUserGrant(botId: String, userId: String, permissions: List<String>): JSONObject {
        require(userId.isNotBlank()) { "User ID is required" }
        require(permissions.isNotEmpty()) { "At least one permission is required" }
        val body = JSONObject()
            .put("user_id", userId.trim())
            .put("permissions", JSONArray(permissions))
            .put("subject_type", "user")
        return request("/bots/${Uri.encode(botId)}/user-access", body)
    }

    suspend fun updateBotUserGrant(botId: String, grantId: String, permissions: List<String>): JSONObject {
        require(permissions.isNotEmpty()) { "At least one permission is required" }
        val body = JSONObject().put("permissions", JSONArray(permissions))
        return request("/bots/${Uri.encode(botId)}/user-access/${Uri.encode(grantId)}", body, method = "PUT")
    }

    suspend fun deleteBotUserGrant(botId: String, grantId: String) {
        request("/bots/${Uri.encode(botId)}/user-access/${Uri.encode(grantId)}", method = "DELETE")
    }

    suspend fun botChannels(botId: String): List<JSONObject> {
        val array = request("/bots/${Uri.encode(botId)}/channel").optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    suspend fun updateBotChannelStatus(botId: String, platform: String, enabled: Boolean): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}/status",
            JSONObject().put("enabled", enabled), method = "PATCH")

    suspend fun deleteBotChannel(botId: String, platform: String) {
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}", method = "DELETE")
    }

    suspend fun upsertBotChannel(botId: String, platform: String, config: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}", config, method = "PUT")

    suspend fun agents(botId: String): List<MemohAgent> {
        val items = request("/bots/${Uri.encode(botId)}/agents").optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            items.optJSONObject(i)?.let(::decodeAgent)?.takeIf { it.id.isNotBlank() }
        }
    }

    suspend fun agentProfiles(): List<MemohAgentProfile> {
        val items = request("/acp/profiles").optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MemohAgentProfile(id, item.optString("display_name").ifBlank { id },
                item.optString("description"))
        }
    }

    suspend fun createAgent(botId: String, name: String, option: MemohAgentOption): MemohAgent {
        require(name.isNotBlank()) { "Agent name is required" }
        val metadata = JSONObject().put("provider", option.id)
        val body = JSONObject().put("name", name.trim()).put("runtime", option.runtime).put("metadata", metadata)
        if (option.runtime != "acp") {
            metadata.put("auth", when (option.runtime) {
                "codex" -> "chatgpt"
                "claude-code" -> "workspace"
                "grok-build" -> "grok_account"
                else -> "api_key"
            })
            body.put("enabled", false)
        }
        return decodeAgent(request("/bots/${Uri.encode(botId)}/agents", body))
    }

    suspend fun updateAgent(botId: String, agentId: String, name: String? = null,
        enabled: Boolean? = null, metadata: JSONObject? = null): MemohAgent {
        val body = JSONObject()
        name?.let { body.put("name", it.trim()) }
        enabled?.let { body.put("enabled", it) }
        metadata?.let { body.put("metadata", JSONObject(it.toString())) }
        require(body.length() > 0) { "No Agent changes supplied" }
        return decodeAgent(request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}", body, method = "PATCH"))
    }

    suspend fun deleteAgent(botId: String, agentId: String) {
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}", method = "DELETE")
    }

    suspend fun saveAgentCredential(botId: String, agent: MemohAgent, secret: String) {
        val auth = agent.metadata.optString("auth")
        val (authKind, secretKey) = when {
            auth == "oauth_token" -> "claude_code_oauth" to "oauth_token"
            agent.runtime == "codex" -> "openai_api_key" to "api_key"
            agent.runtime == "claude-code" -> "anthropic_api_key" to "api_key"
            agent.runtime == "grok-build" -> "xai_api_key" to "api_key"
            else -> throw IOException("This Agent runtime does not accept a manual credential")
        }
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agent.id)}/credential",
            JSONObject().put("auth_kind", authKind).put("secret", JSONObject().put(secretKey, secret)), method = "PUT")
    }

    suspend fun deleteAgentCredential(botId: String, agentId: String) {
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/credential", method = "DELETE")
    }

    /** Device attempts must cancel their in-flight HTTP call when the dialog is closed. */
    private suspend fun deviceLoginRequest(path: String, body: JSONObject): JSONObject = suspendCancellableCoroutine { continuation ->
        val captured = scope
        val builder = businessRequest(path, captured)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val call = captured.identity.client.newCall(builder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        requireCurrent(captured)
                        if (!it.isSuccessful) throw MemohHttpException(it.code, errorFor(it))
                        val raw = it.body?.string().orEmpty()
                        requireCurrent(captured)
                        if (raw.isBlank()) JSONObject() else JSONObject(raw)
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    suspend fun startDeviceLogin(botId: String, agentId: String): MemohDeviceLogin {
        val data = deviceLoginRequest("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/login/device/authorize", JSONObject())
        val loginId = data.optString("login_id")
        if (loginId.isBlank()) throw IOException("Memoh did not return a device login ID")
        return MemohDeviceLogin(loginId, data.optString("user_code"), data.optString("verification_url"),
            data.optString("expires_at"))
    }

    suspend fun pollDeviceLogin(botId: String, agentId: String, loginId: String): MemohDeviceLoginResult {
        val data = deviceLoginRequest("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/login/device/poll",
            JSONObject().put("login_id", loginId))
        return MemohDeviceLoginResult(data.optString("status"), data.optString("message"))
    }

    suspend fun cancelDeviceLogin(botId: String, agentId: String, loginId: String) {
        deviceLoginRequest("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/login/device/cancel",
            JSONObject().put("login_id", loginId))
    }

    suspend fun sessionsPage(botId: String, cursor: String = ""): MemohSessionPage {
        val path = "/bots/${Uri.encode(botId)}/sessions?types=chat,discuss,acp_agent,schedule&hide_schedule_runs=true&limit=50" +
            if (cursor.isBlank()) "" else "&cursor=${Uri.encode(cursor)}"
        val response = request(path)
        val array = response.optJSONArray("items") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else decodeSession(item)
        }
        return MemohSessionPage(items, response.optString("next_cursor").takeIf { it.isNotBlank() })
    }

    suspend fun sessions(botId: String): List<MemohSession> = sessionsPage(botId).items

    suspend fun getSession(botId: String, sessionId: String): MemohSession {
        val result = decodeSession(request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}"))
        require(result.id == sessionId) { "Memoh returned a different session" }
        return result
    }

    suspend fun createSession(botId: String, botAgentId: String = "", preferredModelId: String = "", preferredEffort: String = ""): MemohSession {
        val body = JSONObject().put("title", "").put("channel_type", "local")
        if (botAgentId.isNotBlank()) body.put("bot_agent_id", botAgentId)
        // Omit an unchosen Bot default so this conversation continues following that default.
        if (preferredModelId.isNotBlank()) body.put("preferred_chat_model_id", preferredModelId)
            .put("preferred_reasoning_effort", preferredEffort)
        val created = decodeSession(request("/bots/${Uri.encode(botId)}/sessions", body))
        if (created.id.isBlank()) throw IOException("Memoh did not return a session ID")
        return created
    }

    suspend fun updateSessionModel(botId: String, sessionId: String, modelId: String, effort: String, expectedRevision: String): MemohSession {
        val result = decodeSession(request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}",
            JSONObject().put("preferred_chat_model_id", modelId).put("preferred_reasoning_effort", effort)
                .put("expected_model_preference_revision", expectedRevision), method = "PATCH"))
        require(result.id == sessionId) { "Memoh returned a different session" }
        return result
    }

    suspend fun renameSession(botId: String, sessionId: String, title: String) {
        require(title.isNotBlank()) { "Name must not be blank" }
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}",
            JSONObject().put("title", title.trim()), method = "PATCH")
    }

    suspend fun deleteSession(botId: String, sessionId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}", method = "DELETE")
    }

    suspend fun history(botId: String, sessionId: String, beforeMessageId: String = ""): List<MemohTurn> {
        val path = "/bots/${Uri.encode(botId)}/messages?session_id=${Uri.encode(sessionId)}&limit=$HISTORY_PAGE_SIZE" +
            if (beforeMessageId.isBlank()) "" else "&before_message_id=${Uri.encode(beforeMessageId)}"
        val array = request(path).optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::decodeTurn)
        }
    }


    suspend fun schedules(botId: String): List<MemohSchedule> {
        val entries = request("/bots/${Uri.encode(botId)}/schedule")
            .optJSONArray("items") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { i ->
            entries.optJSONObject(i)?.let(::decodeSchedule)
        }
    }

    suspend fun schedule(botId: String, scheduleId: String): MemohSchedule? =
        decodeSchedule(request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}"))

    /**
     * Creates a schedule.
     *
     * `runTarget` is "new_session" or "existing_session"; the latter reuses
     * [targetSessionId] and must not carry runtime/workdir overrides, the server
     * inherits them from that session.
     */
    suspend fun createSchedule(
        botId: String,
        name: String,
        pattern: String,
        command: String,
        description: String = "",
        runTarget: String = "new_session",
        targetSessionId: String = "",
        modelId: String = "",
        botAgentId: String = "",
        maxRunSeconds: Int = 0,
        enabled: Boolean = true,
    ) {
        val body = scheduleBody(
            name, pattern, command, description, runTarget, targetSessionId,
            modelId, botAgentId, maxRunSeconds
        )
        body.put("enabled", enabled)
        request("/bots/${Uri.encode(botId)}/schedule", body)
    }

    /**
     * Updates a schedule.
     *
     * The server replaces the whole execution block when it is present and
     * deliberately offers no field-level patching (run target, runtime and
     * workdir constrain each other), so callers send the full desired state.
     */
    suspend fun updateSchedule(
        botId: String,
        scheduleId: String,
        name: String,
        pattern: String,
        command: String,
        description: String,
        runTarget: String,
        targetSessionId: String,
        modelId: String,
        botAgentId: String,
        maxRunSeconds: Int,
        enabled: Boolean,
    ) {
        val body = scheduleBody(
            name, pattern, command, description, runTarget, targetSessionId,
            modelId, botAgentId, maxRunSeconds
        )
        body.put("enabled", enabled)
        request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}",
            body, method = "PUT")
    }

    private fun scheduleBody(
        name: String, pattern: String, command: String, description: String,
        runTarget: String, targetSessionId: String, modelId: String,
        botAgentId: String, maxRunSeconds: Int,
    ): JSONObject {
        require(name.isNotBlank() && pattern.isNotBlank() && command.isNotBlank()) {
            "Schedule name, cron pattern and instruction are required"
        }
        require(runTarget == "new_session" || runTarget == "existing_session") {
            "Unknown schedule run target"
        }
        if (runTarget == "existing_session") {
            require(targetSessionId.isNotBlank()) {
                "Choose the session this schedule continues"
            }
            // The server rejects runtime overrides here; they come from the session.
            require(modelId.isBlank() && botAgentId.isBlank()) {
                "A schedule continuing an existing session inherits its runtime"
            }
        } else {
            require(targetSessionId.isBlank()) {
                "A new-session schedule cannot target an existing session"
            }
        }
        require(maxRunSeconds == 0 || maxRunSeconds in 300..86400) {
            "Run budget must be between 300 and 86400 seconds, or 0 for the default"
        }
        return JSONObject()
            .put("name", name.trim())
            .put("pattern", pattern.trim())
            .put("command", command.trim())
            .put("description", description.trim())
            .put("run_target", runTarget)
            .apply {
                if (targetSessionId.isNotBlank()) put("target_session_id", targetSessionId)
                if (modelId.isNotBlank()) put("model_id", modelId)
                if (botAgentId.isNotBlank()) put("bot_agent_id", botAgentId)
                if (maxRunSeconds > 0) put("max_run_seconds", maxRunSeconds)
            }
    }

    private fun decodeSchedule(item: JSONObject?): MemohSchedule? {
        if (item == null) return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        return MemohSchedule(
            id = id,
            name = item.optString("name"),
            pattern = item.optString("pattern"),
            command = item.optString("command"),
            description = item.optString("description"),
            enabled = item.optBoolean("enabled"),
            runTarget = item.optString("run_target").ifBlank { "new_session" },
            targetSessionId = item.optString("target_session_id"),
            modelId = item.optString("model_id"),
            botAgentId = item.optString("bot_agent_id"),
            maxRunSeconds = item.optInt("max_run_seconds"),
            currentCalls = item.optInt("current_calls"),
            maxCalls = item.optInt("max_calls"),
            createdAt = item.optString("created_at"),
            updatedAt = item.optString("updated_at"),
        )
    }

    /** Runs of one schedule, newest first as returned by the server. */
    suspend fun scheduleLogs(botId: String, scheduleId: String, limit: Int = 20, offset: Int = 0): List<MemohScheduleLog> {
        val entries = request(
            "/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}/logs?limit=$limit&offset=$offset"
        ).optJSONArray("items") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { i ->
            entries.optJSONObject(i)?.let(::decodeScheduleLog)
        }
    }

    /** Sessions this schedule's runs created (the bot's sessions, filtered by schedule). */
    suspend fun scheduleSessions(botId: String, scheduleId: String, cursor: String = ""): MemohSessionPage {
        val response = request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}/sessions" +
            "?limit=20" + if (cursor.isBlank()) "" else "&cursor=${Uri.encode(cursor)}")
        return decodeScheduleSessions(response)
    }

    suspend fun setScheduleEnabled(botId: String, scheduleId: String, enabled: Boolean) {
        request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}",
            JSONObject().put("enabled", enabled), method = "PUT")
    }

    suspend fun deleteSchedule(botId: String, scheduleId: String) {
        request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}", method = "DELETE")
    }

    /** Every schedule run recorded for this bot, newest first. */
    suspend fun allScheduleLogs(botId: String, limit: Int = 50, offset: Int = 0): List<MemohScheduleLog> {
        val entries = request("/bots/${Uri.encode(botId)}/schedule/logs?limit=$limit&offset=$offset")
            .optJSONArray("items") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { i ->
            entries.optJSONObject(i)?.let(::decodeScheduleLog)
        }
    }

    private fun decodeScheduleLog(item: JSONObject): MemohScheduleLog = MemohScheduleLog(
        id = item.optString("id"),
        scheduleId = item.optString("schedule_id"),
        sessionId = if (item.isNull("session_id")) "" else item.optString("session_id"),
        status = item.optString("status"),
        startedAt = item.optString("started_at"),
        completedAt = if (item.isNull("completed_at")) "" else item.optString("completed_at"),
        errorCode = item.optString("error_code"),
        errorMessage = item.optString("error_message"),
        resultText = item.optString("result_text"),
    )

    /** Clears the run log for a bot. Destructive: callers must confirm first. */
    suspend fun deleteScheduleLogs(botId: String) {
        request("/bots/${Uri.encode(botId)}/schedule/logs", method = "DELETE")
    }

    suspend fun sessionQueues(botId: String, sessionId: String): MemohQueueState {
        val data = request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/queue")
        val items = mutableListOf<MemohQueueItem>()
        for ((type, key) in listOf("steer" to "steer", "follow_up" to "follow_up")) {
            val array = data.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("item_id")
                if (id.isNotBlank()) items.add(MemohQueueItem(
                    id, type, item.optString("text"), item.optString("status")))
            }
        }
        return MemohQueueState(data.optBoolean("steer_supported"), items)
    }

    /** Per-queue listing variants (the combined /queue endpoint covers both in one call). */
    suspend fun followUpQueue(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue")

    suspend fun steerQueue(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/steer-queue")

    suspend fun enqueueQueue(botId: String, sessionId: String, type: String,
                             text: String, invocationId: String) {
        require(type == "steer" || type == "follow_up") { "Invalid queue type" }
        val path = if (type == "steer") "steer-queue" else "follow-up-queue"
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/$path",
            JSONObject().put("invocation_id", invocationId).put("text", text))
    }

    suspend fun cancelQueueItem(botId: String, sessionId: String, item: MemohQueueItem) {
        val path = if (item.type == "steer") "steer-queue" else "follow-up-queue"
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/$path/${Uri.encode(item.id)}",
            method = "DELETE")
    }

    /** Edit an accepted queue item in place (PATCH {text} on the steer or follow-up queue). */
    suspend fun editQueueItem(botId: String, sessionId: String, item: MemohQueueItem, text: String) {
        val path = if (item.type == "steer") "steer-queue" else "follow-up-queue"
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/$path/${Uri.encode(item.id)}",
            JSONObject().put("text", text.trim()), method = "PATCH")
    }

    /** Per-queue edit variants with an explicit type, matching the documented paths. */
    suspend fun editFollowUpQueueItem(botId: String, sessionId: String, itemId: String, text: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue/${Uri.encode(itemId)}",
            JSONObject().put("text", text.trim()), method = "PATCH")
    }

    suspend fun cancelFollowUpQueueItem(botId: String, sessionId: String, itemId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue/${Uri.encode(itemId)}",
            method = "DELETE")
    }

    suspend fun enqueueFollowUp(botId: String, sessionId: String, text: String, invocationId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue",
            JSONObject().put("invocation_id", invocationId).put("text", text))
    }

    suspend fun reorderFollowUpQueue(botId: String, sessionId: String, itemId: String, beforeId: String?) {
        val body = JSONObject().put("item", JSONObject().put("item_id", itemId))
        if (!beforeId.isNullOrBlank()) body.put("before", JSONObject().put("item_id", beforeId))
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue/reorder",
            body, method = "PUT")
    }

    suspend fun enqueueSteer(botId: String, sessionId: String, text: String, invocationId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/steer-queue",
            JSONObject().put("invocation_id", invocationId).put("text", text))
    }

    suspend fun cancelSteerQueueItem(botId: String, sessionId: String, itemId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/steer-queue/${Uri.encode(itemId)}",
            method = "DELETE")
    }

    suspend fun editSteerQueueItem(botId: String, sessionId: String, itemId: String, text: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/steer-queue/${Uri.encode(itemId)}",
            JSONObject().put("text", text.trim()), method = "PATCH")
    }

    suspend fun reorderSteerQueue(botId: String, sessionId: String, itemId: String, beforeId: String?) {
        val body = JSONObject().put("item", JSONObject().put("item_id", itemId))
        if (!beforeId.isNullOrBlank()) body.put("before", JSONObject().put("item_id", beforeId))
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/steer-queue/reorder",
            body, method = "PUT")
    }

    /** Move [item] so it lands before [beforeId]; a null target moves it to the end. */
    suspend fun reorderQueue(botId: String, sessionId: String, type: String, item: MemohQueueItem, beforeId: String?) {
        require(type == "steer" || type == "follow_up") { "Invalid queue type" }
        val path = if (type == "steer") "steer-queue/reorder" else "follow-up-queue/reorder"
        val body = JSONObject().put("item", JSONObject().put("item_id", item.id))
        if (!beforeId.isNullOrBlank()) body.put("before", JSONObject().put("item_id", beforeId))
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/$path", body, method = "PUT")
    }

    /** Promote an accepted follow-up to steer the active run. */
    suspend fun promoteQueueItem(botId: String, sessionId: String, item: MemohQueueItem) {
        require(item.type == "follow_up") { "Only follow-up items can be promoted" }
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/follow-up-queue/${Uri.encode(item.id)}/steer",
            method = "POST")
    }

    // ---- Session runtime controls (permission/plan modes, goal, runtime commands, model seed) ----

    suspend fun runtimeControls(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls")

    suspend fun setRuntimeMode(botId: String, sessionId: String, modeId: String, plan: Boolean): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls/mode",
            JSONObject().put("mode_id", modeId).put("mode_kind", if (plan) "plan" else "permission"),
            method = "PATCH")

    suspend fun runtimeGoal(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls/goal")

    suspend fun pauseRuntimeGoal(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls/goal",
            JSONObject().put("action", "pause"), method = "POST")

    suspend fun clearRuntimeGoal(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls/goal",
            JSONObject().put("action", "clear"), method = "POST")

    suspend fun runRuntimeCommand(botId: String, sessionId: String, command: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/runtime-controls/commands",
            JSONObject().put("command", command), method = "POST")

    /** Welcome-composer model seed shown before the first send of a new conversation. */
    suspend fun modelPreferenceSeed(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/model-preference-seed")

    /** Model catalog a bot Agent can use (GET agents/{id}/models → external.ModelCatalog). */
    suspend fun agentModels(botId: String, agentId: String, modelId: String = ""): JSONObject =
        request("/bots/${Uri.encode(botId)}/agents/${Uri.encode(agentId)}/models" +
            if (modelId.isNotBlank()) "?model_id=${Uri.encode(modelId)}" else "")

    /**
     * Team-level chat-model catalog in the composer selector's own shape. Homem loads
     * /models and /providers together, groups by provider_id, and shows the provider name
     * as the group header; the selector menu mirrors that grouping.
     */
    suspend fun teamChatModels(): JSONObject {
        val modelResponse = request("/models")
        val providerNames = runCatching {
            val providers = request("/providers").optJSONArray("items") ?: JSONArray()
            (0 until providers.length()).mapNotNull { i ->
                val p = providers.optJSONObject(i) ?: return@mapNotNull null
                p.optString("id") to (p.optString("name").ifBlank { p.optString("id") })
            }.toMap()
        }.getOrDefault(emptyMap())
        val source = modelResponse.optJSONArray("items") ?: JSONArray()
        val models = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            if (item.optString("type") != "chat") continue
            if (item.optBoolean("enable", true) == false) continue
            if (item.optJSONObject("config")?.optBoolean("catalog_available", true) == false) continue
            val providerId = item.optString("provider_id")
            models.put(JSONObject()
                .put("id", item.optString("id").ifBlank { item.optString("model_id") })
                .put("model_id", item.optString("model_id"))
                .put("name", item.optString("name"))
                .put("description", item.optString("description"))
                .put("provider_id", providerId)
                .put("provider_name", providerNames[providerId] ?: "")
                .put("default_reasoning_effort", item.optString("default_reasoning_effort"))
                .put("configured_model_id", modelResponse.optString("configured_model_id")))
        }
        return JSONObject().put("models", models)
    }

    /** Delete the bot's whole message history (server-side, irreversible). */
    suspend fun deleteAllMessages(botId: String) {
        request("/bots/${Uri.encode(botId)}/messages", method = "DELETE")
    }

    /** Locate a history message by its external id and load surrounding context. */
    suspend fun locateMessage(
        botId: String, sessionId: String, externalMessageId: String,
        before: Int = 20, after: Int = 20,
    ): JSONObject = request(
        "/bots/${Uri.encode(botId)}/messages/locate?session_id=${Uri.encode(sessionId)}" +
            "&external_message_id=${Uri.encode(externalMessageId)}" +
            "&before=$before&after=$after")

    // ---- Session extras: status, fork, context lifecycle, invocation lookup ----

    suspend fun sessionStatus(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/status")

    suspend fun forkSession(botId: String, sessionId: String, turnId: String): MemohSession? {
        val created = request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/fork",
            JSONObject().put("turn_id", turnId), method = "POST")
        return created.optString("id").takeIf { it.isNotBlank() }?.let { decodeSession(created) }
    }

    suspend fun contextLifecycle(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/context-lifecycle")

    suspend fun invocation(botId: String, sessionId: String, invocationId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/invocations/${Uri.encode(invocationId)}")

    // ---- ACP session runtime (bound variant of the runtime controls) ----

    suspend fun sessionAcpRuntime(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/acp-runtime")

    suspend fun ensureSessionAcpRuntime(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/acp-runtime",
            JSONObject(), method = "POST")

    suspend fun setSessionAcpRuntimeMode(botId: String, sessionId: String, modeId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/acp-runtime/mode",
            JSONObject().put("mode_id", modeId), method = "PATCH")

    suspend fun setSessionAcpRuntimeModel(botId: String, sessionId: String, modelId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/acp-runtime/model",
            JSONObject().put("model_id", modelId), method = "PATCH")

    suspend fun setSessionAcpRuntimeReasoning(botId: String, sessionId: String, effort: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/acp-runtime/reasoning",
            JSONObject().put("reasoning_effort", effort), method = "PATCH")

    // ---- Session-level tool approvals (HTTP variant of the WS control flow) ----

    suspend fun approveToolCall(botId: String, approvalId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/tool-approvals/${Uri.encode(approvalId)}/approve",
            JSONObject(), method = "POST")

    suspend fun rejectToolCall(botId: String, approvalId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/tool-approvals/${Uri.encode(approvalId)}/reject",
            JSONObject(), method = "POST")

    // ---- Bot channel message sending (server-side channel delivery) ----

    suspend fun sendChannelMessage(botId: String, platform: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}/send", body)

    suspend fun sendChannelChatMessage(botId: String, platform: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/channel/${Uri.encode(platform)}/send_chat", body)

    // ---- Bot user settings (per-bot key/value) ----

    suspend fun botSettings(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/settings")

    suspend fun updateBotSettings(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/settings", body, method = "PUT")

    suspend fun deleteBotSettings(botId: String) {
        request("/bots/${Uri.encode(botId)}/settings", method = "DELETE")
    }

    // ---- Workdirs ----

    suspend fun workdirs(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs")

    suspend fun createWorkdir(botId: String, path: String, name: String = ""): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs",
            JSONObject().put("path", path.trim()).put("name", name.trim()), method = "POST")

    suspend fun renameWorkdir(botId: String, workdirId: String, name: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs/${Uri.encode(workdirId)}",
            JSONObject().put("name", name.trim()), method = "PATCH")

    suspend fun archiveWorkdir(botId: String, workdirId: String) {
        request("/bots/${Uri.encode(botId)}/workdirs/${Uri.encode(workdirId)}", method = "DELETE")
    }

    suspend fun workdirGitBranch(botId: String, workdirId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs/${Uri.encode(workdirId)}/git-branch")

    suspend fun switchWorkdirGitBranch(botId: String, workdirId: String, branch: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs/${Uri.encode(workdirId)}/git-branch",
            JSONObject().put("branch", branch.trim()), method = "POST")

    suspend fun workdirDirectories(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workdirs/directories")

    // ---- Workspace targets ----

    suspend fun workspaceTargets(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workspace-targets")

    suspend fun setPrimaryWorkspaceTarget(botId: String, targetId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/workspace-targets/primary",
            JSONObject().put("target_id", targetId), method = "PUT")

    suspend fun setWorkspaceTargetToolApproval(botId: String, targetId: String, approval: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/workspace-targets/${Uri.encode(targetId)}/tool-approval",
            approval, method = "PUT")

    suspend fun deleteWorkspaceTarget(botId: String, targetId: String) {
        request("/bots/${Uri.encode(botId)}/workspace-targets/${Uri.encode(targetId)}", method = "DELETE")
    }

    // ---- Token usage ----

    suspend fun tokenUsage(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/token-usage")

    suspend fun tokenUsageRecords(botId: String, limit: Int = 50, offset: Int = 0): JSONObject =
        request("/bots/${Uri.encode(botId)}/token-usage/records?limit=$limit&offset=$offset")

    // ---- Skills catalog (chat-time skill selection) ----

    suspend fun skillsCatalog(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/skills/catalog")

    // ---- Bot TTS ----

    suspend fun ttsSynthesize(botId: String, body: JSONObject): JSONObject =
        request("/bots/${Uri.encode(botId)}/tts/synthesize", body, method = "POST")

    // ---- Compaction ----

    suspend fun compactSession(botId: String, sessionId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}/compact", method = "POST")

    suspend fun memoryConfig(): JSONObject =
        request("/memory/config")

    suspend fun updateMemoryConfig(embeddingModelId: String): JSONObject =
        request("/memory/config", JSONObject().put("embedding_model_id", embeddingModelId.trim()), method = "PUT")

    suspend fun listMemories(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory?no_stats=true")

    suspend fun addMemory(botId: String, memory: String): JSONObject {
        require(memory.isNotBlank()) { "Memory content is required" }
        return request("/bots/${Uri.encode(botId)}/memory", JSONObject().put("message", memory.trim()))
    }

    suspend fun updateMemory(botId: String, id: String, memory: String): JSONObject {
        require(memory.isNotBlank()) { "Memory content is required" }
        return request("/bots/${Uri.encode(botId)}/memory/${Uri.encode(id)}",
            JSONObject().put("memory", memory.trim()), method = "PUT")
    }

    suspend fun deleteMemory(botId: String, id: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/${Uri.encode(id)}", method = "DELETE")

    suspend fun deleteMemories(botId: String, ids: List<String>): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory",
            JSONObject().put("memory_ids", JSONArray(ids)), method = "DELETE")

    suspend fun searchMemories(botId: String, query: String): JSONObject {
        require(query.isNotBlank()) { "Search text is required" }
        return request("/bots/${Uri.encode(botId)}/memory/search", JSONObject().put("query", query.trim()))
    }

    suspend fun compactMemories(botId: String, ratio: Double, decayDays: Int?): JSONObject {
        require(ratio > 0.0 && ratio <= 1.0) { "Ratio must be greater than 0 and at most 1" }
        val body = JSONObject().put("ratio", ratio)
        decayDays?.let { body.put("decay_days", it) }
        return request("/bots/${Uri.encode(botId)}/memory/compact", body)
    }

    suspend fun memoryGraph(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/graph")

    suspend fun ingestMemories(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/ingest", JSONObject())

    suspend fun rebuildMemories(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/rebuild", JSONObject())

    suspend fun memoryStatus(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/status")

    suspend fun memoryUsage(botId: String): JSONObject =
        request("/bots/${Uri.encode(botId)}/memory/usage")

    suspend fun terminalSocket(
        botId: String,
        cols: Int,
        rows: Int,
        listener: WebSocketListener,
    ): WebSocket {
        val captured = scope
        val path = "/bots/${Uri.encode(botId)}/container/terminal/ws"
        val builder = businessRequest(path, captured)
        var url = (captured.identity.endpoints.api + path).toHttpUrl().newBuilder().apply {
            if (captured.team.isNotEmpty()) addQueryParameter("team_id", captured.team)
            addQueryParameter("cols", cols.coerceIn(1, 500).toString())
            addQueryParameter("rows", rows.coerceIn(1, 500).toString())
        }.build()
        val websocketToken = if (captured.identity.bearer.isNotEmpty()) {
            captured.identity.bearer
        } else {
            teamPlatformRequest("/ws-tickets", JSONObject(), method = "POST")
                .optString("ticket")
                .also { require(it.isNotBlank()) { "Memoh did not return a WebSocket ticket" } }
        }
        // The terminal endpoint documents `token` as its WebSocket query parameter. The runtime
        // socket uses `ticket`; using that name here completes the handshake but is ignored by the
        // terminal handler.
        url = url.newBuilder().addQueryParameter("token", websocketToken).build()
        requireCurrent(captured)
        return captured.identity.client.newWebSocket(builder.url(url).build(), listener)
    }

    /**
     * Opens the runtime socket.
     *
     * A bearer server accepts the header directly. A cookie session cannot: the browser client
     * first trades its session for a short-lived ticket at `/api/v1/ws-tickets` and passes that as
     * `?ticket=`, so do the same rather than sending a header the socket endpoint ignores.
     */
    suspend fun socket(botId: String, listener: WebSocketListener): WebSocket {
        val captured = scope
        val path = "/bots/${Uri.encode(botId)}/web/ws"
        val builder = businessRequest(path, captured)
        var url = (captured.identity.endpoints.api + path).toHttpUrl().newBuilder().apply {
            if (captured.team.isNotEmpty()) addQueryParameter("team_id", captured.team)
        }.build()
        if (captured.identity.bearer.isEmpty()) {
            val ticket = teamPlatformRequest("/ws-tickets", JSONObject(), method = "POST").optString("ticket")
            if (ticket.isBlank()) throw IOException("Memoh did not return a WebSocket ticket")
            url = url.newBuilder().addQueryParameter("ticket", ticket).build()
        }
        requireCurrent(captured)
        return captured.identity.client.newWebSocket(builder.url(url).build(), listener)
    }
}

