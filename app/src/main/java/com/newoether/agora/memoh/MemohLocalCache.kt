package com.newoether.agora.memoh

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last server answer for the two lists a cold start needs before the network does.
 *
 * Only shape-stable, non-secret rows are stored: a bot's id/title/avatar and a session's
 * id/title/agent. Nothing here is authoritative — a successful refresh replaces it — so a
 * stale entry can only make the first frame look like the last one, never change what the
 * server is asked to do.
 */
internal class MemohLocalCache(private val prefs: SharedPreferences) {

    // A cache is opt-in only after the current account has been verified. Never trust the
    // previous process's last-selected account, including legacy server/team-only keys.
    private var identity: String? = null

    private fun key(scope: String): String? = identity?.let { "$scope:v2:$it" }

    fun rememberIdentity(scopeKey: String) {
        identity = scopeKey.takeIf { it.isNotBlank() }
    }

    fun forgetIdentity() {
        identity = null
    }

    fun bots(): List<MemohBot> = read(KEY_BOTS).mapNotNull { row ->
        val id = row.optString("id")
        if (id.isBlank()) return@mapNotNull null
        MemohBot(
            id = id,
            title = row.optString("title"),
            name = row.optString("name"),
            displayName = row.optString("display_name"),
            avatarUrl = row.optString("avatar_url"),
            timezone = row.optString("timezone"),
            status = row.optString("status"),
            isActive = row.optBoolean("is_active", true),
            currentUserPermissions = row.optJSONArray("permissions")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty(),
        )
    }

    fun storeBots(values: List<MemohBot>) {
        val rows = JSONArray()
        values.take(MAX_BOTS).forEach { bot ->
            rows.put(
                JSONObject()
                    .put("id", bot.id)
                    .put("title", bot.title)
                    .put("name", bot.name)
                    .put("display_name", bot.displayName)
                    .put("avatar_url", bot.avatarUrl)
                    .put("timezone", bot.timezone)
                    .put("status", bot.status)
                    .put("is_active", bot.isActive)
                    .put("permissions", JSONArray(bot.currentUserPermissions)),
            )
        }
        write(KEY_BOTS, rows)
    }

    fun sessions(botId: String): List<MemohSession> {
        if (botId.isBlank()) return emptyList()
        return read("$KEY_SESSIONS:$botId").mapNotNull { row ->
            val id = row.optString("id")
            if (id.isBlank()) return@mapNotNull null
            MemohSession(
                id = id,
                title = row.optString("title"),
                botAgentId = row.optString("bot_agent_id"),
                runtimeType = row.optString("runtime_type"),
                type = row.optString("type").ifBlank { "chat" },
            )
        }
    }

    fun storeSessions(botId: String, values: List<MemohSession>) {
        if (botId.isBlank()) return
        val rows = JSONArray()
        values.take(MAX_SESSIONS).forEach { session ->
            rows.put(
                JSONObject()
                    .put("id", session.id)
                    .put("title", session.title)
                    .put("bot_agent_id", session.botAgentId)
                    .put("runtime_type", session.runtimeType)
                    .put("type", session.type),
            )
        }
        write("$KEY_SESSIONS:$botId", rows)
    }

    /**
     * The composer's model catalog is the slowest thing a cold start waits on and the least
     * volatile, so it is kept as one blob. A parse failure reads as "nothing cached".
     */
    fun models(): JSONObject? {
        val raw = readValue(KEY_MODELS) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun storeModels(catalog: JSONObject) {
        writeValue(KEY_MODELS, catalog.toString())
    }

    private fun read(scope: String): List<JSONObject> {
        val raw = readValue(scope) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun readValue(scope: String): String? {
        val cacheKey = key(scope) ?: return null
        return try { prefs.getString(cacheKey, null) } catch (_: RuntimeException) { null }
    }

    private fun writeValue(scope: String, value: String) {
        val cacheKey = key(scope) ?: return
        try { prefs.edit().putString(cacheKey, value).apply() } catch (_: RuntimeException) { }
    }

    private fun write(scope: String, rows: JSONArray) = writeValue(scope, rows.toString())

    private companion object {
        const val KEY_BOTS = "cache_bots"
        const val KEY_SESSIONS = "cache_sessions"
        const val KEY_MODELS = "cache_models"
        // A cache only serves the first frame; keeping megabytes of history here would cost
        // more than it saves, so it is bounded to one screenful plus a little.
        const val MAX_BOTS = 50
        const val MAX_SESSIONS = 40
    }
}
