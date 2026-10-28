package com.newoether.agora.memoh

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class MemohTeam(val id: String, val name: String, val slug: String, val role: String) {
    val title: String get() = name.ifBlank { slug.ifBlank { id } }
}

/** Platform discovery is deliberately unscoped; business and ticket calls require a team. */
internal suspend fun MemohApi.platformProfile(): JSONObject = authRequest("/users/me")

/**
 * `/users/me` answers the account row itself, so `display_name` and `avatar_url` are top-level
 * fields. Older deployments nested a `user_profile` (and a `user`); both are still read as a
 * fallback, but reading only the nested shape left a signed-in account with no name and no
 * avatar at all.
 */
internal fun platformDisplayName(profile: JSONObject): String =
    profile.optString("display_name").orEmpty()
        .ifBlank { profile.optJSONObject("user_profile")?.optString("display_name").orEmpty() }
        .ifBlank { profile.optString("username").orEmpty() }
        .ifBlank { profile.optJSONObject("user")?.optString("username").orEmpty() }

internal fun platformAvatarUrl(profile: JSONObject): String =
    profile.optString("avatar_url").orEmpty()
        .ifBlank { profile.optJSONObject("user_profile")?.optString("avatar_url").orEmpty() }

internal fun platformUserId(profile: JSONObject): String {
    val user = profile.optJSONObject("user")
    return profile.optString("id").ifBlank { profile.optString("user_id") }
        .ifBlank { user?.optString("id").orEmpty() }.ifBlank { user?.optString("user_id").orEmpty() }
}
internal suspend fun MemohApi.teams(): List<MemohTeam> {
    val items = authRequest("/teams").optJSONArray("teams") ?: return emptyList()
    return (0 until items.length()).mapNotNull { i ->
        val membership = items.optJSONObject(i) ?: return@mapNotNull null
        val team = membership.optJSONObject("team") ?: return@mapNotNull null
        val id = team.optString("team_id")
        if (id.isBlank()) null else MemohTeam(id, team.optString("name"), team.optString("slug"), membership.optString("role"))
    }
}
internal suspend fun MemohApi.createTeam(name: String, slug: String): MemohTeam {
    require(name.trim().isNotEmpty()) { "Enter a team name" }
    val team = authRequest("/teams", JSONObject().put("name", name.trim()).put("slug", slug.trim()))
        .getJSONObject("team")
    val id = team.getString("team_id")
    require(id.isNotBlank()) { "Memoh returned an invalid team" }
    return MemohTeam(id, team.optString("name"), team.optString("slug"), "TEAM_ROLE_OWNER")
}

internal class MemohTeamController(
    private val api: MemohApi,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onError: (String) -> Unit,
) {
    var items by mutableStateOf<List<MemohTeam>>(emptyList()); private set
    var selectedId by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var creationAllowed by mutableStateOf(false); private set
    /** True once a membership page has arrived, so an empty list is a real answer. */
    var loaded by mutableStateOf(false); private set
    val current: MemohTeam? get() = items.firstOrNull { it.id == selectedId }
    val enabled: Boolean get() = api.usesSessionCookie
    private var revision = 0
    private var preferenceKey = ""

    fun reset() {
        revision++; items = emptyList(); selectedId = ""; busy = false
        creationAllowed = false; preferenceKey = ""; loaded = false
    }
    fun load() {
        if (busy || !enabled) return
        val request = ++revision
        val identity = api.scope
        busy = true
        scope.launch {
            try {
                val profile = api.platformProfile()
                val teams = api.teams()
                api.requireCurrent(identity)
                if (request != revision) return@launch
                val id = platformUserId(profile)
                check(id.isNotBlank()) { "Memoh returned no platform user identity" }
                preferenceKey = "team:${api.authUrl}:$id"
                creationAllowed = profile.optBoolean("team_creation_allowed", true)
                items = teams
                val saved = prefs.getString(preferenceKey, "").orEmpty()
                val selected = teams.firstOrNull { it.id == selectedId }
                    ?: teams.firstOrNull { it.id == saved } ?: teams.firstOrNull()
                applySelection(selected?.id.orEmpty())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (request == revision) onError(e.message ?: "Unable to load teams") }
            finally {
                if (request == revision) {
                    busy = false
                    // A failed page still ends the wait: the caller must not sit on a blank
                    // screen because a membership request never came back.
                    loaded = true
                }
            }
        }
    }
    fun select(id: String) {
        if (busy || id == selectedId || items.none { it.id == id }) return
        applySelection(id)
    }
    private fun applySelection(id: String) {
        selectedId = id
        api.selectTeam(id)
        if (preferenceKey.isNotBlank()) prefs.edit().putString(preferenceKey, id).apply()
        onChanged()
    }
    fun create(name: String, slug: String) {
        if (busy || !creationAllowed || name.isBlank()) return
        val request = ++revision
        val identity = api.scope
        busy = true
        scope.launch {
            try {
                val team = api.createTeam(name, slug)
                api.requireCurrent(identity)
                if (request != revision) return@launch
                items = (items + team).distinctBy { it.id }
                applySelection(team.id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (request == revision) onError(e.message ?: "Unable to create team") }
            finally { if (request == revision) busy = false }
        }
    }
}
