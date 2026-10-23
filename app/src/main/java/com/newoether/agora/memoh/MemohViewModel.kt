package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
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
    internal val navigationScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + kotlinx.coroutines.SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val prefs = application.getSharedPreferences("memoh_native", 0)
    private var authRevision = 0
    private var botRevision = 0
    internal var attachmentDraftRevision by mutableStateOf(0); private set
    internal var historyRevision = 0; private set
    internal var navigationRevision = 0
    private var epoch = ""
    private var seq = -1L
    private var awaitingSnapshot = true
    private val api = MemohApi()
    internal val workspaceApi: MemohApi get() = api
    internal var agentController = MemohAgentController(api); private set
    private var socket: WebSocket? = null
    private var socketGeneration = 0
    private var reconnectJob: Job? = null
    private val awaitingAdmission = linkedMapOf<String, JSONObject>()
    private val pendingControls = mutableMapOf<String, String>()
    var waitingForInteraction by mutableStateOf<Set<String>>(emptySet())
        private set
    val controlsReady: Boolean get() = connected && !awaitingSnapshot && runId.isNotBlank()
    private var liveJson = JSONArray()
    private var currentRunStatus = ""
    private var settlingRunId = ""

    val login = MemohLoginForm(prefs.getString("server", MemohEndpoints.CLOUD_ORIGIN).orEmpty())

    internal val teams = MemohTeamController(api, prefs, viewModelScope,
        onChanged = { resetNavigation(); if (api.teamId.isNotEmpty()) refreshBots() }, onError = { error = it })
    private val signIn = MemohSignIn(
        api = api,
        prefs = prefs,
        scope = viewModelScope,
        form = login,
        onError = { error = it },
        onSignedIn = { resetNavigation(); signedIn = true; error = ""; login.code = ""; if (api.usesSessionCookie) teams.load() else refreshBots() },
    )

    var signedIn by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var bots by mutableStateOf<List<MemohBot>>(emptyList()); private set
    var bot by mutableStateOf<MemohBot?>(null); private set
    var sessions by mutableStateOf<List<MemohSession>>(emptyList()); private set
    var olderSessionsAvailable by mutableStateOf(false); private set
    var loadingSessions by mutableStateOf(false); private set
    private var nextSessionCursor: String? = null
    var queueState by mutableStateOf<MemohQueueState?>(null); private set
    var queueBusy by mutableStateOf(false); private set
    private var queueRevision = 0
    var olderMessagesAvailable by mutableStateOf(false); private set
    var loadingOlderMessages by mutableStateOf(false); private set
    private var firstHistoryMessageId: String? = null
    var session by mutableStateOf<MemohSession?>(null); private set
    var turns by mutableStateOf<List<MemohTurn>>(emptyList()); private set
    var liveBlocks by mutableStateOf<List<MemohBlock>>(emptyList()); private set
    var pendingUserText by mutableStateOf<String?>(null); private set
    var pendingUserAttachments by mutableStateOf<List<MemohAttachment>>(emptyList()); private set
    var restoreDraft by mutableStateOf<String?>(null); private set
    var connected by mutableStateOf(false); private set
    var running by mutableStateOf(false); private set
    var runId by mutableStateOf(""); private set

    init {
        val saved = runCatching { MemohTokenStore.read(prefs.getString("jwt", "").orEmpty()) }
            .getOrDefault("")
        if (saved.isNotEmpty()) {
            runCatching { api.configure(login.server, saved) }
                .onSuccess { signedIn = true; refreshBots() }
                .onFailure { error = it.message.orEmpty() }
        } else {
            // Cookie sessions cannot survive the process, so a fresh start asks the server which
            // form to show instead of guessing from the last deployment.
            probeLoginMethod()
        }
    }

    fun probeLoginMethod() = signIn.probe(login.server)

    fun sendLoginCode() {
        error = ""
        signIn.sendCode(login.email)
    }

    fun loginWithCode() = signIn.signInWithCode(login.code)

    fun login() = signIn.signInWithPassword(login.username.trim(), login.password)

    fun logout() {
        resetNavigation(); teams.reset(); signIn.signOut(); signedIn = false
    }
    private fun resetNavigation() {
        navigationScope.coroutineContext[Job]?.cancelChildren()
        authRevision++; botRevision++; navigationRevision++
        attachmentDraftRevision++; historyRevision++; queueRevision++
        closeSocket(); clearRun()
        loading = false; loadingSessions = false; loadingOlderMessages = false; queueBusy = false
        queueState = null; firstHistoryMessageId = null; olderMessagesAvailable = false; restoreDraft = null
        bots = emptyList(); agentController.clear(); agentController = MemohAgentController(MemohApi(api)); bot = null
        sessions = emptyList(); nextSessionCursor = null; olderSessionsAvailable = false
        session = null; turns = emptyList(); pendingUserText = null; pendingUserAttachments = emptyList()
        error = ""
    }

    fun refreshBots() {
        if (api.usesSessionCookie && api.teamId.isEmpty()) return
        navigationScope.launch { loadBots() }
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

    /** Applies edited bot metadata without switching bots (see [selectBot]). */
    fun updateBotMetadata(value: MemohBot) {
        if (bot?.id != value.id) return
        bot = value
        bots = bots.map { if (it.id == value.id) value else it }
    }

    fun selectBot(value: MemohBot) {
        if (awaitingAdmission.isNotEmpty()) {
            error = "Wait until your message is accepted before switching bots."
            return
        }
        val revision = ++botRevision
        attachmentDraftRevision++; historyRevision++
        queueRevision++
        closeSocket()
        bot = value
        agentController.select("")
        session = null
        turns = emptyList()
        sessions = emptyList()
        nextSessionCursor = null
        olderSessionsAvailable = false
        clearRun()
        pendingUserText = null; pendingUserAttachments = emptyList()
        navigationScope.launch {
            runCatching { agentController.refresh(value.id) }
        }
        navigationScope.launch {
            loadingSessions = true
            try {
                val page = api.sessionsPage(value.id)
                if (revision == botRevision && bot?.id == value.id) {
                    sessions = page.items
                    nextSessionCursor = page.nextCursor
                    olderSessionsAvailable = page.nextCursor != null
                }
            } catch (e: Exception) {
                if (revision == botRevision && bot?.id == value.id)
                    error = e.message ?: "Failed to load sessions"
            } finally {
                if (revision == botRevision) loadingSessions = false
            }
        }
        connectSocket(value.id)
    }

    fun selectSession(value: MemohSession?) {
        if (awaitingAdmission.isNotEmpty() && value?.id != session?.id) {
            error = "Wait until your message is accepted before switching chats."
            return
        }
        attachmentDraftRevision++; historyRevision++
        queueRevision++
        resetCursor()
        if (value != null) agentController.selectFromSession(value.botAgentId)
        val previous = session?.id
        session = value
        turns = emptyList()
        queueState = null
        firstHistoryMessageId = null
        olderMessagesAvailable = false
        pendingUserText = null; pendingUserAttachments = emptyList()
        clearRun()
        if (!previous.isNullOrEmpty()) sendNow(JSONObject()
            .put("type", "runtime_unsubscribe").put("session_id", previous))
        value?.let {
            sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", it.id))
            reloadHistory(it.id)
            refreshQueue()
        }
    }

    fun newChat() = selectSession(null)

    fun selectAgentForNewChat(agentId: String) {
        if (running || pendingUserText != null || awaitingAdmission.isNotEmpty()) {
            error = "Finish or stop the current run before starting a chat with another Agent."
            return
        }
        agentController.select(agentId)
        selectSession(null)
    }

    fun refreshQueue() {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val revision = queueRevision
        navigationScope.launch {
            try {
                val snapshot = api.sessionQueues(b, sid)
                if (queueRevision == revision && bot?.id == b && session?.id == sid)
                    queueState = snapshot
            } catch (e: Exception) {
                if (queueRevision == revision) error = e.message ?: "Failed to load message queues"
            }
        }
    }

    fun queueMessage(text: String, type: String): Boolean {
        val b = bot?.id ?: return false
        val sid = session?.id ?: return false
        val value = text.trim()
        if (value.isBlank() || !running || queueBusy || type !in setOf("steer", "follow_up")) return false
        if (type == "steer" && queueState?.steerSupported != true) {
            error = "Steering is not supported by this session."
            return false
        }
        val revision = queueRevision
        queueBusy = true
        error = ""
        navigationScope.launch {
            try {
                // Fixed invocation ID per admission, no automatic network retries.
                api.enqueueQueue(b, sid, type, value, UUID.randomUUID().toString())
                if (queueRevision == revision) refreshQueue()
            } catch (e: Exception) {
                if (queueRevision == revision) {
                    restoreDraft = value
                    error = "Queue delivery is uncertain: ${e.message ?: "Request failed"}. Check the queue before resending."
                }
            } finally {
                if (queueRevision == revision) queueBusy = false
            }
        }
        return true
    }

    fun cancelQueued(item: MemohQueueItem) {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        if (queueBusy || queueState?.items?.none { it.id == item.id && it.type == item.type } != false) return
        val revision = queueRevision
        queueBusy = true
        navigationScope.launch {
            try {
                api.cancelQueueItem(b, sid, item)
                if (revision == queueRevision) refreshQueue()
            } catch (e: Exception) {
                if (revision == queueRevision) error = e.message ?: "Could not remove queued message"
            } finally {
                if (revision == queueRevision) queueBusy = false
            }
        }
    }

    fun loadOlderSessions() {
        val b = bot?.id ?: return
        val cursor = nextSessionCursor ?: return
        if (loadingSessions) return
        val revision = botRevision
        loadingSessions = true
        navigationScope.launch {
            try {
                val page = api.sessionsPage(b, cursor)
                if (revision == botRevision && bot?.id == b && cursor == nextSessionCursor) {
                    sessions = (sessions + page.items).distinctBy { it.id }
                    nextSessionCursor = page.nextCursor?.takeIf { it != cursor }
                    olderSessionsAvailable = nextSessionCursor != null
                }
            } catch (e: Exception) {
                if (revision == botRevision) error = e.message ?: "Failed to load sessions"
            } finally {
                if (revision == botRevision) loadingSessions = false
            }
        }
    }

    fun renameSelectedSession(title: String) {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val name = title.trim()
        if (name.isBlank()) return
        val revision = historyRevision
        navigationScope.launch {
            try {
                api.renameSession(b, sid, name)
                if (bot?.id == b && session?.id == sid && revision == historyRevision) {
                    sessions = sessions.map { if (it.id == sid) it.copy(title = name) else it }
                    session = session?.copy(title = name)
                }
            } catch (e: Exception) {
                if (revision == historyRevision) error = e.message ?: "Unable to rename conversation"
            }
        }
    }

    fun deleteSelectedSession() {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val revision = historyRevision
        if (running || awaitingAdmission.isNotEmpty()) {
            error = "Stop or finish the running Agent before deleting this conversation."
            return
        }
        navigationScope.launch {
            try {
                api.deleteSession(b, sid)
                if (bot?.id == b) {
                    sessions = sessions.filterNot { it.id == sid }
                    if (session?.id == sid && revision == historyRevision) selectSession(null)
                }
            } catch (e: Exception) {
                if (revision == historyRevision) error = e.message ?: "Unable to delete conversation"
            }
        }
    }

    fun loadOlderMessages() {
        val sid = session?.id ?: return
        val b = bot?.id ?: return
        val firstId = firstHistoryMessageId ?: return
        if (loadingOlderMessages || !olderMessagesAvailable) return
        val revision = historyRevision
        loadingOlderMessages = true
        navigationScope.launch {
            try {
                val page = api.history(b, sid, firstId)
                if (bot?.id == b && session?.id == sid && revision == historyRevision) {
                    turns = (page + turns).distinctBy { it.id }
                    firstHistoryMessageId = page.firstOrNull()?.id ?: firstId
                    olderMessagesAvailable = page.isNotEmpty() && firstHistoryMessageId != firstId
                }
            } catch (e: Exception) {
                if (revision == historyRevision)
                    error = e.message ?: "Failed to load earlier messages"
            } finally {
                if (revision == historyRevision) loadingOlderMessages = false
            }
        }
    }

    fun consumeRestoredDraft() { restoreDraft = null }

    /** Surface a rejected attachment in the send-error banner instead of dropping it. */
    fun reportAttachmentError(message: String) {
        if (message.isNotBlank()) error = message
    }
    fun send(text: String, attachments: List<MemohAttachment> = emptyList()): Boolean {
        val value = text.trim()
        val selectedBot = bot ?: return false
        if ((value.isEmpty() && attachments.isEmpty()) || pendingUserText != null || running || awaitingAdmission.isNotEmpty()) return false
        attachmentDraftError(attachments)?.let { error = it; return false }
        error = ""
        restoreDraft = null
        val invocation = UUID.randomUUID().toString()
        val payload = JSONObject().put("type", "message")
            .put("invocation_id", invocation).put("text", value)
        if (attachments.isNotEmpty()) {
            val arr = JSONArray()
            attachments.forEach { att ->
                val o = JSONObject().put("id", att.id.ifBlank { UUID.randomUUID().toString() })
                    .put("name", att.name).put("mime", att.mime).put("type", att.type).put("size", att.size)
                if (att.base64.isNotBlank()) o.put("base64", att.base64)
                if (att.url.isNotBlank()) o.put("url", att.url)
                arr.put(o)
            }
            payload.put("attachments", arr)
        }
        val existingSession = session
        existingSession?.id?.let { payload.put("session_id", it) }
        awaitingAdmission[invocation] = payload
        pendingUserText = value
        pendingUserAttachments = attachments
        attachmentDraftRevision++
        if (existingSession != null) {
            if (connected) sendNow(payload)
            else if (socket == null) connectSocket(selectedBot.id)
        } else {
            val authRevisionAtSend = authRevision
            val botRevisionAtSend = botRevision
            navigationScope.launch {
                try {
                    val created = api.createSession(selectedBot.id, agentController.selectedAgentId)
                    if (authRevisionAtSend != authRevision || botRevisionAtSend != botRevision ||
                        !awaitingAdmission.containsKey(invocation)) return@launch
                    historyRevision++
                    resetCursor()
                    session = created
                    sessions = listOf(created) + sessions.filterNot { it.id == created.id }
                    payload.put("session_id", created.id)
                    if (connected) {
                        sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", created.id))
                        sendNow(payload)
                    } else if (socket == null) connectSocket(selectedBot.id)
                } catch (e: Exception) {
                    if (authRevisionAtSend == authRevision && botRevisionAtSend == botRevision &&
                        awaitingAdmission.remove(invocation) != null) {
                        pendingUserText = null; pendingUserAttachments = emptyList()
                        restoreDraft = value
                        error = e.message ?: "Failed to create conversation"
                    }
                }
            }
        }
        return true
    }

    fun abort() {
        val sid = session?.id.orEmpty()
        if (!controlsReady || sid.isEmpty()) return
        val controlId = UUID.randomUUID().toString()
        sendNow(JSONObject().put("type", "abort")
            .put("run_id", runId).put("session_id", sid)
            .put("control_id", controlId))
    }

    private fun submitInteraction(decisionId: String, payload: JSONObject) {
        if (decisionId.isBlank() || !controlsReady || session == null ||
            decisionId in waitingForInteraction) return
        val controlId = UUID.randomUUID().toString()
        payload.put("run_id", runId).put("session_id", session!!.id)
            .put("control_id", controlId).put("decision_id", decisionId)
        pendingControls[controlId] = decisionId
        waitingForInteraction = waitingForInteraction + decisionId
        sendNow(payload)
    }

    fun decide(approval: MemohBlock, choice: MemohApprovalOption) {
        if (!approval.canApprove || approval.approvalId.isBlank()) return
        val decision = if (choice.kind.startsWith("reject")) "reject" else "approve"
        submitInteraction(approval.approvalId, JSONObject().put("type", "tool_approval_response")
            .put("decision", decision).put("option_id", choice.id))
    }

    fun answerUserInput(
        block: MemohBlock,
        options: Map<String, Set<String>>,
        texts: Map<String, String>,
        canceled: Boolean = false,
    ) {
        if (!block.canRespond || block.userInputId.isBlank()) return
        val payload = JSONObject().put("type", "user_input_response")
        if (canceled) {
            payload.put("canceled", true)
        } else {
            val answers = JSONArray()
            for (question in block.questions) {
                val answer = JSONObject().put("question_id", question.id)
                if (question.kind == "text") {
                    answer.put("text", texts[question.id].orEmpty())
                } else {
                    val selected = options[question.id].orEmpty()
                    answer.put("option_ids", JSONArray(selected.toList()))
                    if (question.allowCustom && texts[question.id].orEmpty().isNotBlank())
                        answer.put("custom_text", texts[question.id].orEmpty())
                }
                answers.put(answer)
            }
            payload.put("answers", answers)
        }
        submitInteraction(block.userInputId, payload)
    }

    private fun reloadHistory(sid: String) {
        val b = bot?.id ?: return
        val revision = historyRevision
        navigationScope.launch {
            try {
                val result = api.history(b, sid)
                if (bot?.id == b && session?.id == sid && revision == historyRevision) {
                    val older = turns
                    val updatedIds = result.mapTo(HashSet()) { it.id }
                    turns = (older.filterNot { it.id in updatedIds } + result).distinctBy { it.id }
                    if (firstHistoryMessageId == null) {
                        firstHistoryMessageId = result.firstOrNull()?.id
                        olderMessagesAvailable = result.isNotEmpty()
                    }
                    if (!running && awaitingAdmission.isEmpty()) pendingUserText = null; pendingUserAttachments = emptyList()
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
        socket = null
        // The handshake may need a ticket round-trip, so the socket opens from a coroutine.
        navigationScope.launch {
            val opened = try {
                api.socket(botId, socketListener(botId, generation))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (generation == socketGeneration && bot?.id == botId) {
                    error = "Could not open the live connection: ${e.message ?: "unknown error"}"
                }
                return@launch
            }
            if (generation != socketGeneration || bot?.id != botId) opened.cancel() else socket = opened
        }
    }

    private fun socketListener(botId: String, generation: Int): WebSocketListener {
        fun current() = generation == socketGeneration && bot?.id == botId
        return memohSocketListener(
            scope = navigationScope,
            generation = generation,
            isCurrent = ::current,
            onOpen = { webSocket ->
                connected = true
                session?.id?.let { id ->
                    webSocket.send(JSONObject().put("type", "runtime_subscribe")
                        .put("session_id", id).toString())
                }
                awaitingAdmission.values.forEach { webSocket.send(it.toString()) }
            },
            onEvent = ::receive,
            onUnauthorized = {
                logout()
                error = "Session expired. Sign in again."
            },
            onDisconnected = { disconnected(generation, botId) },
        )
    }

    private fun disconnected(generation: Int, botId: String) {
        navigationScope.launch {
            if (generation != socketGeneration || bot?.id != botId) return@launch
            connected = false
            resetCursor()
            if (pendingControls.isNotEmpty()) {
                error = "Connection lost. A tool decision may have been applied; check the run before retrying."
                pendingControls.clear()
                waitingForInteraction = emptySet()
            }
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
                    val created = MemohSession(sid, "New conversation", agentController.selectedAgentId)
                    sessions = listOf(created) + sessions.filterNot { it.id == sid }
                    session = created
                    sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", sid))
                }
            }
            "run_accepted" -> {
                awaitingAdmission.remove(invocation)
                if (session?.id == target) {
                    refreshQueue()
                    settlingRunId = ""
                    runId = event.optString("run_id")
                    running = true
                }
            }
            "run_rejected", "error" -> {
                if (invocation.isNotBlank() && awaitingAdmission.remove(invocation) != null) {
                    restoreDraft = pendingUserText
                    pendingUserText = null; pendingUserAttachments = emptyList()
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
            "control_ack" -> {
                val controlId = event.optString("control_id")
                val decisionId = pendingControls.remove(controlId)
                if (decisionId != null && !event.optBoolean("applied", false))
                    waitingForInteraction = waitingForInteraction - decisionId
                if (!event.optBoolean("applied", false))
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
        pruneResolvedDecisions()
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
        pruneResolvedDecisions()
        settleIfTerminal(sid)
    }

    private fun pruneResolvedDecisions() {
        val unresolved = liveBlocks.flatMap { block ->
            buildList {
                if (block.canApprove) add(block.approvalId)
                if (block.canRespond) add(block.userInputId)
            }
        }.filter { it.isNotBlank() }.toSet()
        waitingForInteraction = waitingForInteraction.intersect(unresolved)
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
            navigationScope.launch {
                try {
                    val settled = api.history(botId, sid)
                    if (bot?.id == botId && session?.id == sid && runId == finishedRun && !running) {
                        val updatedIds = settled.mapTo(HashSet()) { it.id }
                        turns = (turns.filterNot { it.id in updatedIds } + settled).distinctBy { it.id }
                        pendingUserText = null; pendingUserAttachments = emptyList()
                        clearRun()
                        val refreshed = api.sessionsPage(botId)
                        if (bot?.id == botId && session?.id == sid) {
                            sessions = (refreshed.items + sessions).distinctBy { it.id }
                            nextSessionCursor = refreshed.nextCursor
                            olderSessionsAvailable = nextSessionCursor != null
                        }
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
        waitingForInteraction = emptySet()
        pendingControls.clear()
    }

    private fun closeSocket() {
        socketGeneration++
        reconnectJob?.cancel()
        socket?.cancel()
        socket = null
        connected = false
        resetCursor()
        pendingControls.clear()
        waitingForInteraction = emptySet()
        awaitingAdmission.clear()
    }

    override fun onCleared() {
        closeSocket()
        super.onCleared()
    }
}

