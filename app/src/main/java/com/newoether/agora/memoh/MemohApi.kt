package com.newoether.agora.memoh

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
internal class MemohApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    var baseUrl: String = "https://app.memoh.net/api"
        private set
    var token: String = ""
        private set

    fun configure(base: String, bearer: String = "") {
        val trimmed = base.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrl()
        require(parsed.scheme == "https" || (parsed.scheme == "http" && isLocalhost(parsed.host))) {
            "Use HTTPS for remote servers. HTTP is supported only for localhost."
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "Credentials must not be part of the URL" }
        require(parsed.query == null && parsed.fragment == null) { "Use an API base URL without a query or fragment" }
        baseUrl = trimmed
        token = bearer
    }

    private fun isLocalhost(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"

    private suspend fun request(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
            .header("Accept", "application/json")
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        if (body != null) {
            builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        val call = http.newCall(builder.build())
        call.execute().use { response ->
            if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun errorFor(response: Response): String {
        val raw = response.body?.string()?.take(2048).orEmpty()
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        return parsed?.optString("message")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("detail")?.takeIf { it.isNotBlank() }
            ?: "HTTP ${response.code}"
    }

    suspend fun login(username: String, password: String): String {
        val response = request("/auth/login", JSONObject()
            .put("username", username.trim())
            .put("password", password))
        val result = response.optString("access_token")
        if (result.isBlank()) throw IOException("Memoh did not return an access token")
        token = result
        return result
    }

    suspend fun bots(): List<MemohBot> {
        val array = request("/bots").optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MemohBot(id, item.optString("display_name").ifBlank {
                item.optString("name").ifBlank { "Bot" }
            })
        }
    }

    suspend fun sessions(botId: String): List<MemohSession> {
        val array = request("/bots/${Uri.encode(botId)}/sessions?limit=100")
            .optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MemohSession(id, item.optString("title").ifBlank { "Untitled" })
        }
    }

    suspend fun history(botId: String, sessionId: String): List<MemohTurn> {
        val array = request("/bots/${Uri.encode(botId)}/messages?session_id=${Uri.encode(sessionId)}&limit=100")
            .optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::decodeTurn)
        }
    }

    /** Authenticate the native socket with a header, not a URL parameter. */
    fun socket(botId: String, listener: WebSocketListener): WebSocket {
        val url = (baseUrl + "/bots/${Uri.encode(botId)}/web/ws").toHttpUrl()
        val wsUrl = url.toString().replaceFirst(if (url.isHttps) "https://" else "http://", if (url.isHttps) "wss://" else "ws://")
        return http.newWebSocket(Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build(), listener)
    }
}

