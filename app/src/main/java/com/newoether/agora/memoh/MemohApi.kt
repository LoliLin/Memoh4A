package com.newoether.agora.memoh

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private suspend fun request(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST"): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
            .header("Accept", "application/json")
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        if (method != "GET") {
            builder.method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
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

    suspend fun sessionsPage(botId: String, cursor: String = ""): MemohSessionPage {
        val path = "/bots/${Uri.encode(botId)}/sessions?types=chat,discuss,acp_agent,schedule&hide_schedule_runs=true&limit=50" +
            if (cursor.isBlank()) "" else "&cursor=${Uri.encode(cursor)}"
        val response = request(path)
        val array = response.optJSONArray("items") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MemohSession(id, item.optString("title").ifBlank { "Untitled" })
        }
        return MemohSessionPage(items, response.optString("next_cursor").takeIf { it.isNotBlank() })
    }

    suspend fun sessions(botId: String): List<MemohSession> = sessionsPage(botId).items

    suspend fun renameSession(botId: String, sessionId: String, title: String) {
        require(title.isNotBlank()) { "Name must not be blank" }
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}",
            JSONObject().put("title", title.trim()), method = "PATCH")
    }

    suspend fun deleteSession(botId: String, sessionId: String) {
        request("/bots/${Uri.encode(botId)}/sessions/${Uri.encode(sessionId)}", method = "DELETE")
    }

    suspend fun history(botId: String, sessionId: String, beforeMessageId: String = ""): List<MemohTurn> {
        val path = "/bots/${Uri.encode(botId)}/messages?session_id=${Uri.encode(sessionId)}&limit=50" +
            if (beforeMessageId.isBlank()) "" else "&before_message_id=${Uri.encode(beforeMessageId)}"
        val array = request(path).optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::decodeTurn)
        }
    }

    suspend fun listFiles(botId: String, directory: String): List<MemohFile> {
        val data = request("/bots/${Uri.encode(botId)}/container/fs/list?path=${Uri.encode(directory)}")
        val array = data.optJSONArray("entries") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val path = item.optString("path")
            if (path.isBlank()) null else MemohFile(
                name = item.optString("name").ifBlank { path.substringAfterLast('/') },
                path = path,
                isDirectory = item.optBoolean("isDir"),
            )
        }
    }

    suspend fun readFile(botId: String, path: String): MemohFileContent {
        val data = request("/bots/${Uri.encode(botId)}/container/fs/read?path=${Uri.encode(path)}")
        return MemohFileContent(data.optString("content"), data.optString("revision"))
    }

    suspend fun writeFile(botId: String, path: String, content: String, revision: String): String {
        // The remote file may be changed by the Agent while the editor is open.
        require(revision.isNotBlank()) { "This file has no revision; safe overwrite is unavailable." }
        val response = request("/bots/${Uri.encode(botId)}/container/fs/write",
            JSONObject().put("path", path).put("content", content)
                .put("expectedRevision", revision))
        return response.optString("revision")
    }

    suspend fun uploadFile(
        botId: String,
        path: String,
        uri: Uri,
        resolver: ContentResolver,
        filename: String,
    ) = withContext(Dispatchers.IO) {
        val source = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot read selected file")
                stream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) sink.write(buffer, 0, count)
                    }
                }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("path", path)
            .addFormDataPart("file", filename, source)
            .build()
        val response = http.newCall(Request.Builder()
            .url(baseUrl + "/bots/${Uri.encode(botId)}/container/fs/upload")
            .header("Authorization", "Bearer $token")
            .post(body).build()).execute()
        response.use {
            if (!it.isSuccessful) throw MemohHttpException(it.code, errorFor(it))
        }
    }

    suspend fun schedules(botId: String): List<MemohSchedule> {
        val entries = request("/bots/${Uri.encode(botId)}/schedule")
            .optJSONArray("items") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { i ->
            val item = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MemohSchedule(
                id = id,
                name = item.optString("name"),
                pattern = item.optString("pattern"),
                command = item.optString("command"),
                description = item.optString("description"),
                enabled = item.optBoolean("enabled"),
            )
        }
    }

    suspend fun createSchedule(botId: String, name: String, pattern: String, command: String) {
        require(name.isNotBlank() && pattern.isNotBlank() && command.isNotBlank()) {
            "Schedule name, cron pattern and instruction are required"
        }
        request("/bots/${Uri.encode(botId)}/schedule",
            JSONObject().put("name", name.trim()).put("pattern", pattern.trim())
                .put("command", command.trim()).put("description", "")
                .put("run_target", "new_session"))
    }

    suspend fun setScheduleEnabled(botId: String, scheduleId: String, enabled: Boolean) {
        request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}",
            JSONObject().put("enabled", enabled), method = "PUT")
    }

    suspend fun deleteSchedule(botId: String, scheduleId: String) {
        request("/bots/${Uri.encode(botId)}/schedule/${Uri.encode(scheduleId)}", method = "DELETE")
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

    /** Authenticate the native socket with a header, not a URL parameter. */
    fun socket(botId: String, listener: WebSocketListener): WebSocket {
        val url = (baseUrl + "/bots/${Uri.encode(botId)}/web/ws").toHttpUrl()
        val wsUrl = url.toString().replaceFirst(if (url.isHttps) "https://" else "http://", if (url.isHttps) "wss://" else "ws://")
        return http.newWebSocket(Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build(), listener)
    }
}

