package com.newoether.agora.memoh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * An identity owns its cookies/client. Every request also captures a team generation.
 *
 * [cookiePrefs] is where the session cookie is sealed for the next process; it is assigned once at
 * startup because the transport is constructed before the app's preferences are reachable.
 */
internal open class MemohTransport(private val parent: MemohTransport? = null) {
    companion object {
        @Volatile internal var cookiePrefs: android.content.SharedPreferences? = null
    }

    internal class Identity(val endpoints: MemohEndpoints, var bearer: String) {
        val cookies = MemohCookieJar(cookiePrefs)
        @Volatile var cookieAuthenticated = false
        lateinit var client: OkHttpClient
    }
    internal class Scope(val identity: Identity, val team: String)
    private val pinned = parent?.scope
    @Volatile private var owned = if (parent == null) newScope(MemohEndpoints.fromInput(MemohEndpoints.CLOUD_ORIGIN), "") else null
    private var active: Scope
        get() = parent?.active ?: checkNotNull(owned)
        set(value) { check(parent == null) { "Cannot reconfigure a bound Memoh client" }; owned = value }
    internal val scope: Scope get() = pinned ?: active
    val baseUrl: String get() = scope.identity.endpoints.api
    val authUrl: String get() = scope.identity.endpoints.auth
    val token: String get() = scope.identity.bearer
    val teamId: String get() = scope.team
    val usesSessionCookie: Boolean get() = token.isEmpty() && scope.identity.cookieAuthenticated
    internal val http: OkHttpClient get() = scope.identity.client

    private fun newScope(endpoints: MemohEndpoints, bearer: String): Scope {
        val identity = Identity(endpoints, bearer)
        identity.client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS).cookieJar(identity.cookies)
            .addInterceptor { chain ->
                val captured = chain.request().tag(Scope::class.java)
                    ?: throw IllegalStateException("Memoh requests must capture their scope")
                if (active !== captured) throw java.io.IOException("Memoh scope changed")
                val response = chain.proceed(chain.request())
                if (active !== captured) { response.close(); throw java.io.IOException("Memoh scope changed") }
                response
            }.build()
        return Scope(identity, "")
    }

    fun configure(server: String, bearer: String = "") = configure(MemohEndpoints.fromInput(server), bearer)
    @Synchronized fun configure(endpoints: MemohEndpoints, bearer: String = "") {
        val previous = active
        active = newScope(endpoints, bearer)
        previous.identity.client.dispatcher.cancelAll()
    }
    @Synchronized fun selectTeam(id: String) {
        val value = id.trim()
        if (value == active.team) return
        active = Scope(active.identity, value)
        active.identity.client.dispatcher.cancelAll()
    }
    internal fun requireCurrent(captured: Scope) {
        if (active !== captured) throw CancellationException("Memoh identity or team changed")
    }
    internal fun acceptBearer(captured: Scope, value: String) {
        synchronized(this) { requireCurrent(captured); captured.identity.bearer = value }
    }
    internal fun acceptCookieSession(captured: Scope) {
        synchronized(this) { requireCurrent(captured); captured.identity.cookieAuthenticated = true }
    }
    internal fun hasSession(captured: Scope): Boolean {
        requireCurrent(captured)
        return captured.identity.cookies.hasSession()
    }
    /** Invalidate immediately; an old logout is allowed to touch only its old cookie jar. */
    fun clearIdentity() = configure(active.identity.endpoints)

    internal fun businessRequest(path: String, captured: Scope = scope): Request.Builder = scopedRequest(captured, path, platform = false, team = true)
    internal suspend fun <T> withBusinessScope(block: (Scope) -> T): T {
        val captured = scope
        return withContext(Dispatchers.IO) {
            requireCurrent(captured)
            block(captured).also { requireCurrent(captured) }
        }
    }
    internal fun scopedRequest(captured: Scope, path: String, platform: Boolean, team: Boolean): Request.Builder {
        requireCurrent(captured)
        val identity = captured.identity
        if (team && identity.bearer.isEmpty() && identity.cookieAuthenticated) {
            check(captured.team.isNotBlank()) { "Select a team before loading Memoh resources" }
        }
        return Request.Builder().url((if (platform) identity.endpoints.auth else identity.endpoints.api) + path)
            .tag(Scope::class.java, captured).header("Accept", "application/json").apply {
                if (identity.bearer.isNotEmpty()) header("Authorization", "Bearer ${identity.bearer}")
                if (team && captured.team.isNotEmpty()) header("X-Team-ID", captured.team)
            }
    }
    suspend fun request(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST"): JSONObject =
        send(scope, path, body, method, platform = false, team = true)
    internal suspend fun authRequest(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST"): JSONObject =
        send(scope, path, body, method, platform = true, team = false)
    internal suspend fun teamPlatformRequest(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST"): JSONObject =
        send(scope, path, body, method, platform = true, team = true)

    private suspend fun send(captured: Scope, path: String, body: JSONObject?, method: String, platform: Boolean, team: Boolean): JSONObject {
        val builder = scopedRequest(captured, path, platform, team)
        if (method != "GET") builder.method(method,
            if (method == "DELETE" && body == null) null
            else (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
        return withContext(Dispatchers.IO) {
            captured.identity.client.newCall(builder.build()).execute().use { response ->
                requireCurrent(captured)
                if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
                val raw = response.body?.string().orEmpty()
                requireCurrent(captured)
                if (raw.isBlank()) JSONObject() else if (raw.trim().startsWith("["))
                    JSONObject().put("items", org.json.JSONArray(raw)) else JSONObject(raw)
            }
        }
    }
    internal fun errorFor(response: Response): String {
        val parsed = runCatching { JSONObject(response.body?.string()?.take(2048).orEmpty()) }.getOrNull()
        return parsed?.optString("message")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("detail")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("reason")?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
    }
}
