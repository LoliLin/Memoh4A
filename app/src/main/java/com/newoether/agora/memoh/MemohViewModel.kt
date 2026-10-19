package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class MemohViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("memoh_native", 0)
    private var authRevision = 0
    private var botRevision = 0
    private var historyRevision = 0
    private var epoch = ""
    private var seq = -1L
    private var awaitingSnapshot = true
    private val api = MemohApi()
    private var socket: WebSocket? = null
    private var socketGeneration = 0
    private var reconnectJob: Job? = null
    private val awaitingAdmission = linkedMapOf<String, JSONObject>()
    private var liveJson = JSONArray()
    private var currentRunStatus = ""
    private var settlingRunId = ""

    var server by mutableStateOf(prefs.getString("server", "https://app.memoh.net/api").orEmpty())
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var signedIn by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var bots by mutableStateOf<List<MemohBot>>(emptyList())
        private set
    var bot by mutableStateOf<MemohBot?>(null)
        private set
    var sessions by mutableStateOf<List<MemohSession>>(emptyList())
        private set
    var session by mutableStateOf<MemohSession?>(null)
        private set
    var turns by mutableStateOf<List<MemohTurn>>(emptyList())
        private set
    var liveBlocks by mutableStateOf<List<MemohBlock>>(emptyList())
        private set
    var pendingUserText by mutableStateOf<String?>(null)
        private set
    var restoreDraft by mutableStateOf<String?>(null)
        private set
    var connected by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set
    var runId by mutableStateOf("")
        private set

    init {
        val saved = runCatching { MemohTokenStore.read(prefs.getString("jwt", "").orEmpty()) }
            .getOrDefault("")
        if (saved.isNotEmpty()) {
            runCatching { api.configure(server, saved) }
                .onSuccess { signedIn = true; refreshBots() }
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun login() {
        if (loading) return
        val loginServer = server.trim()
        val user = username.trim()
        val pwd = password
        if (user.isEmpty() || pwd.isEmpty()) { error = "Enter your username and password"; return }
        loading = true
        error = ""
        val revision = ++authRevision
        viewModelScope.launch {
            try {
                api.configure(loginServer)
                val jwt = api.login(user, pwd)
                if (revision != authRevision) return@launch
                withContext(Dispatchers.IO) {
                    prefs.edit().putString("server", loginServer)
                        .putString("jwt", MemohTokenStore.seal(jwt)).apply()
                }
                password = ""
                signedIn = true
                loadBots()
            } catch (t: Exception) {
                if (revision == authRevision) error = "Connection failed: ${t.message ?: "Unknown error"}"
            } finally { if (revision == authRevision) loading = false }
        }
    }

    fun logout() {
        authRevision++
        botRevision++
        historyRevision++
        closeSocket()
        api.configure(api.baseUrl)
        prefs.edit().remove("jwt").apply()
        signedIn = false
        loading = false
        password = ""
        bots = emptyList()
        bot = null
        sessions = emptyList()
        session = null
        turns = emptyList()
        pendingUserText = null
        liveBlocks = emptyList()
        error = ""
    }

    fun refreshBots() {
        viewModelScope.launch { loadBots() }
    }

    private suspend fun loadBots() {
        val revision = authRevision
        try {
            val results = api.bots()
            if (!signedIn || revision != authRevision) return
            bots = results
            error = ""
            if (bot == null || results.none { it.id == bot?.id }) {
                results.firstOrNull()?.let(::selectBot)
            }
        } catch (e: MemohHttpException) {
            if (revision == authRevision) {
                if (e.status == 401) {
                    logout()
                    error = "Session expired. Sign in again."
                } else error = e.message.orEmpty()
            }
        } catch (e: Exception) {
            if (revision == authRevision) error = e.message ?: "Failed to load bots"
        }
    }

    fun selectBot(value: MemohBot) {
        if (awaitingAdmission.isNotEmpty()) {
            error = "Wait until your message is accepted before switching bots."
            return
        }
        val revision = ++botRevision
        historyRevision++
        closeSocket()
        bot = value
        session = null
        turns = emptyList()
        sessions = emptyList()
        clearRun()
        pendingUserText = null
        viewModelScope.launch {
            try {
                val loaded = api.sessions(value.id)
                if (revision == botRevision && bot?.id == value.id) sessions = loaded
            } catch (e: Exception) {
                if (revision == botRevision && bot?.id == value.id)
                    error = e.message ?: "Failed to load sessions"
            }
        }
        connectSocket(value.id)
    }

    fun selectSession(value: MemohSession?) {
        if (awaitingAdmission.isNotEmpty() && value?.id != session?.id) {
            error = "Wait until your message is accepted before switching chats."
            return
        }
        historyRevision++
        resetCursor()
        val previous = session?.id
        session = value
        turns = emptyList()
        pendingUserText = null
        clearRun()
        if (!previous.isNullOrEmpty()) sendNow(JSONObject()
            .put("type", "runtime_unsubscribe").put("session_id", previous))
        value?.let {
            sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", it.id))
            reloadHistory(it.id)
        }
    }

    fun newChat() = selectSession(null)

    fun consumeRestoredDraft() { restoreDraft = null }

    fun send(text: String): Boolean {
        val value = text.trim()
        val selectedBot = bot ?: return false
        if (value.isEmpty() || pendingUserText != null || running || awaitingAdmission.isNotEmpty()) return false
        error = ""
        restoreDraft = null
        val invocation = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("type", "message")
            .put("invocation_id", invocation)
            .put("text", value)
        session?.id?.let { payload.put("session_id", it) }
        awaitingAdmission[invocation] = payload
        pendingUserText = value
        if (connected) sendNow(payload)
        else if (socket == null) connectSocket(selectedBot.id)
        return true
    }

    fun abort() {
        val sid = session?.id.orEmpty()
        if (!connected || runId.isEmpty() || sid.isEmpty()) return
        sendNow(JSONObject().put("type", "abort")
            .put("run_id", runId).put("session_id", sid)
            .put("control_id", UUID.randomUUID().toString()))
    }

    fun decide(approval: MemohBlock, choice: MemohApprovalOption) {
        val sid = session?.id.orEmpty()
        if (!connected || runId.isEmpty() || sid.isEmpty() || approval.approvalId.isEmpty()) return
        val decision = if (choice.kind.startsWith("reject")) "reject" else "approve"
        sendNow(JSONObject().put("type", "tool_approval_response")
            .put("run_id", runId).put("session_id", sid)
            .put("control_id", UUID.randomUUID().toString())
            .put("decision_id", approval.approvalId)
            .put("decision", decision).put("option_id", choice.id))
    }

    private fun reloadHistory(sid: String) {
        val b = bot?.id ?: return
        val revision = historyRevision
        viewModelScope.launch {
            try {
                val result = api.history(b, sid)
                if (bot?.id == b && session?.id == sid && revision == historyRevision) {
                    turns = result
                    if (!running && awaitingAdmission.isEmpty()) pendingUserText = null
                }
            } catch (e: Exception) {
                if (session?.id == sid && revision == historyRevision)
                    error = e.message ?: "Failed to load history"
            }
        }
    }

    private fun sendNow(value: JSONObject) { if (connected) socket?.send(value.toString()) }

    private fun connectSocket(botId: String) {
        val generation = ++socketGeneration
        reconnectJob?.cancel()
        socket?.cancel()
        socket = api.socket(botId, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                viewModelScope.launch {
                    if (generation != socketGeneration || bot?.id != botId) return@launch
                    connected = true
                    session?.id?.let { id ->
                        webSocket.send(JSONObject().put("type", "runtime_subscribe")
                            .put("session_id", id).toString())
                    }
                    awaitingAdmission.values.forEach { webSocket.send(it.toString()) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                viewModelScope.launch {
                    if (generation == socketGeneration && bot?.id == botId) receive(json)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response?.code == 401) {
                    viewModelScope.launch {
                        if (generation == socketGeneration && bot?.id == botId) {
                            logout()
                            error = "Session expired. Sign in again."
                        }
                    }
                } else disconnected(generation, botId)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnected(generation, botId)
            }
        })
    }

    private fun disconnected(generation: Int, botId: String) {
        viewModelScope.launch {
            if (generation != socketGeneration || bot?.id != botId) return@launch
            connected = false
            resetCursor()
            // Do not infer a run failed from a lost observation connection.
            reconnectJob?.cancel()
            reconnectJob = launch {
                delay(2000)
                if (generation == socketGeneration && bot?.id == botId) connectSocket(botId)
            }
        }
    }

    private fun receive(event: JSONObject) {
        val kind = event.optString("type")
        val target = event.optString("session_id")
        val invocation = event.optString("invocation_id")
        when (kind) {
            "session_created" -> {
                if (target.isNotBlank() && invocation.isNotEmpty() && awaitingAdmission.containsKey(invocation)) {
                    historyRevision++
                    resetCursor()
                    val sid = target
                    awaitingAdmission[invocation]?.put("session_id", sid)
                    val created = MemohSession(sid, "New conversation")
                    sessions = listOf(created) + sessions.filterNot { it.id == sid }
                    session = created
                    sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", sid))
                }
            }
            "run_accepted" -> {
                awaitingAdmission.remove(invocation)
                if (session?.id == target) {
                    settlingRunId = ""
                    runId = event.optString("run_id")
                    running = true
                }
            }
            "run_rejected", "error" -> {
                if (invocation.isNotBlank() && awaitingAdmission.remove(invocation) != null) {
                    restoreDraft = pendingUserText
                    pendingUserText = null
                }
                error = event.optString("message").ifBlank { event.optString("code").ifBlank { "Run rejected" } }
                if (session?.id == target && runId == event.optString("run_id") && runId.isNotEmpty()) {
                    running = false
                }
            }
            "runtime_snapshot" -> if (session?.id == target) {
                val nextEpoch = event.optString("epoch")
                val nextSeq = event.optLong("seq", -1L)
                if (nextEpoch.isBlank() || nextSeq < 0L) return
                if (!awaitingSnapshot && nextEpoch == epoch && nextSeq <= seq) return
                epoch = nextEpoch
                seq = nextSeq
                awaitingSnapshot = false
                val snapshot = event.optJSONObject("snapshot")
                val view = snapshot?.optJSONObject("current_run_view")
                if (view == null) {
                    clearRun()
                    reloadHistory(target)
                } else applyRunView(view)
            }
            "runtime_delta" -> if (session?.id == target) {
                if (awaitingSnapshot) return
                val nextEpoch = event.optString("epoch")
                val nextSeq = event.optLong("seq", -1L)
                if (nextEpoch != epoch || nextSeq != seq + 1L) {
                    if (nextSeq > seq || nextEpoch != epoch) requestFreshSnapshot(target)
                    return
                }
                seq = nextSeq
                applyDelta(event.optJSONObject("delta") ?: JSONObject(), target)
            }
            "runtime_dropped" -> if (session?.id == target) {
                error = "Live updates were dropped. Refreshing server state."
                requestFreshSnapshot(target)
                reloadHistory(target)
            }
            "control_ack" -> if (!event.optBoolean("applied", false)) {
                error = "Action not applied: ${event.optString("code")}"
            }
        }
    }

    private fun resetCursor() {
        epoch = ""
        seq = -1L
        awaitingSnapshot = true
    }

    private fun requestFreshSnapshot(sid: String) {
        if (awaitingSnapshot) return
        awaitingSnapshot = true
        sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", sid))
    }

    private fun applyRunView(view: JSONObject) {
        runId = view.optString("run_id")
        currentRunStatus = view.optString("status")
        liveJson = view.optJSONArray("messages") ?: JSONArray()
        liveBlocks = decodeBlocks(liveJson)
        settleIfTerminal()
    }

    private fun applyDelta(delta: JSONObject, sid: String) {
        val replacement = delta.optJSONObject("current_run_view")
        if (replacement != null) { applyRunView(replacement); return }
        if (delta.optBoolean("reset_messages", false)) liveJson = JSONArray()
        val upserts = delta.optJSONArray("message_upserts")
        if (upserts != null) for (i in 0 until upserts.length()) {
            upserts.optJSONObject(i)?.let(::upsertBlock)
        }
        val appends = delta.optJSONArray("message_appends")
        if (appends != null) for (i in 0 until appends.length()) {
            val update = appends.optJSONObject(i) ?: continue
            val id = update.optInt("id", -1)
            if (id < 0) continue
            val existing = (0 until liveJson.length()).mapNotNull { liveJson.optJSONObject(it) }
                .firstOrNull { it.optInt("id", -1) == id }
            if (existing != null) {
                existing.put("content", existing.optString("content") + update.optString("content"))
            } else liveJson.put(JSONObject(update.toString()))
        }
        val patch = delta.optJSONObject("run")
        if (patch != null && patch.optString("run_id") == runId) {
            if (patch.has("status")) currentRunStatus = patch.optString("status")
        }
        liveBlocks = decodeBlocks(liveJson)
        settleIfTerminal(sid)
    }

    private fun upsertBlock(block: JSONObject) {
        val id = block.optInt("id", -1)
        val existing = (0 until liveJson.length()).firstOrNull { liveJson.optJSONObject(it)?.optInt("id", -1) == id }
        if (existing != null) liveJson.put(existing, block)
        else liveJson.put(block)
    }

    private fun settleIfTerminal(sid: String = session?.id.orEmpty()) {
        running = currentRunStatus in setOf("admitting", "running", "waiting_decision", "aborting", "finishing")
        if (!running && currentRunStatus.isNotEmpty() && sid.isNotBlank() && settlingRunId != runId) {
            val finishedRun = runId
            val botId = bot?.id ?: return
            settlingRunId = finishedRun
            viewModelScope.launch {
                try {
                    val settled = api.history(botId, sid)
                    if (bot?.id == botId && session?.id == sid && runId == finishedRun && !running) {
                        turns = settled
                        pendingUserText = null
                        clearRun()
                        sessions = api.sessions(botId)
                    }
                } catch (e: Exception) {
                    if (session?.id == sid) error = e.message ?: "Failed to load settled messages"
                }
            }
        }
    }

    private fun clearRun() {
        liveJson = JSONArray()
        liveBlocks = emptyList()
        running = false
        runId = ""
        currentRunStatus = ""
        settlingRunId = ""
    }

    private fun closeSocket() {
        socketGeneration++
        reconnectJob?.cancel()
        socket?.cancel()
        socket = null
        connected = false
        resetCursor()
        awaitingAdmission.clear()
    }

    override fun onCleared() {
        closeSocket()
        super.onCleared()
    }
}

