package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** How long a sent message may wait for run_accepted/run_rejected before being restored. */
internal const val ADMISSION_TIMEOUT_MS = 30_000L

internal class MemohViewModel(application: Application) : AndroidViewModel(application) {
    internal val navigationScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + kotlinx.coroutines.SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val prefs = application.getSharedPreferences("memoh_native", 0).also {
        // Hand the transport its cookie store before any Identity is built.
        MemohTransport.cookiePrefs = it
    }
    private var authRevision = 0
    private var botRevision = 0
    private val modelPreferenceMutex = Mutex()
    internal var attachmentDraftRevision by mutableStateOf(0); private set
    internal var historyRevision = 0; private set
    internal var navigationRevision = 0
    private var epoch = ""
    private var seq = -1L
    private var awaitingSnapshot = true

    internal val api = MemohApi()
    internal val workspaceApi: MemohApi get() = api
    internal val settings by lazy { SettingsRepository(SettingsManager(application), viewModelScope) }
    internal var agentController = MemohAgentController(api); private set
    private val botConnections = mutableMapOf<String, MemohConnection>()
    private val idleConnection by lazy { createConnection("") }
    internal val connection: MemohConnection
        get() = bot?.id?.let(::connectionFor) ?: idleConnection

    private fun connectionFor(botId: String): MemohConnection =
        botConnections.getOrPut(botId) { createConnection(botId) }

    private fun createConnection(ownerBotId: String) = MemohConnection(
        scope = navigationScope,
        open = api::socket,
        onConnected = {
            if (bot?.id == ownerBotId) session?.id?.let { id ->
                sendToBot(ownerBotId, JSONObject().put("type", "runtime_subscribe").put("session_id", id))
            }
            awaitingAdmission.filter { (id, payload) ->
                (sendStates[id]?.botId ?: bot?.id) == ownerBotId && payload.optString("session_id").isNotBlank()
            }.values.toList().forEach { sendToBot(ownerBotId, it) }
        },
        onEvent = { receiveFromBot(ownerBotId, it) },
        onDisconnected = {
            if (bot?.id == ownerBotId) {
                resetCursor()
                if (pendingControls.isNotEmpty()) {
                    error = "Connection lost. A tool decision may have been applied; check the run before retrying."
                    pendingControls.clear()
                    waitingForInteraction = emptySet()
                }
            }
        },
        onUnauthorized = { logout(); error = "Session expired. Sign in again." },
    )
    private val awaitingAdmission = linkedMapOf<String, JSONObject>()
    internal val admissionInvocations: MutableMap<String, JSONObject> get() = awaitingAdmission
    internal val sendStates = linkedMapOf<String, MemohSendState>()
    internal val admissionTimeoutJobs = mutableMapOf<String, Job>()
    internal val hasCurrentAdmission: Boolean
        get() = awaitingAdmission.any { (id, payload) ->
            sendStates[id]?.belongsTo(bot?.id, session?.id)
                ?: (payload.optString("session_id") == session?.id.orEmpty())
        }
    internal var pendingUserTurnId: String? = null
    private val pendingControls = mutableMapOf<String, String>()
    var waitingForInteraction by mutableStateOf<Set<String>>(emptySet())
        private set
    val controlsReady: Boolean get() = connected && !awaitingSnapshot && runId.isNotBlank()
    // Transcript state shared with the MemohLiveTranscript.kt extension.
    internal var liveJson = JSONArray()
    internal var currentRunStatus = ""
    internal var liveBlocks by mutableStateOf<List<MemohBlock>>(emptyList())
    internal var settlingRunId = ""
    /** Rows removed for an in-flight retry/edit, so a rejection can restore the transcript. */
    private var historyMutationSnapshot: HistoryMutationSnapshot? = null
    private var pendingHistoryMutationInvocation: String? = null
    /** True while an optimistic history trim is on screen; the terminal refresh must keep it. */
    internal val historyMutationPending: Boolean get() = historyMutationSnapshot != null

    val login = MemohLoginForm(prefs.getString("server", MemohEndpoints.CLOUD_ORIGIN).orEmpty())

    internal val teams = MemohTeamController(api, prefs, viewModelScope,
        onChanged = { resetNavigation(); if (api.teamId.isNotEmpty()) refreshBots() }, onError = { error = it })
    private val signIn = MemohSignIn(
        api = api,
        prefs = prefs,
        scope = viewModelScope,
        form = login,
        onError = { error = it },
        onSignedIn = { resetNavigation(); signedIn = true; error = ""; login.code = ""; loadPlatformProfile(); if (api.usesSessionCookie) teams.load() else refreshBots() },
    )

    var signedIn by mutableStateOf(false)
        internal set
    var profileDisplayName by mutableStateOf("")
        private set
    var profileAvatarUrl by mutableStateOf("")
        private set

    private fun loadPlatformProfile() {
        navigationScope.launch {
            runCatching { api.platformProfile() }.onSuccess { profile ->
                profileDisplayName = platformDisplayName(profile)
                profileAvatarUrl = platformAvatarUrl(profile)
            }
        }
    }
    internal var restoringSession by mutableStateOf(false)
    internal var sessionRestoreGeneration = 0
    internal var sessionRestoreFailed by mutableStateOf(false)
    var loading by mutableStateOf(false); private set
    internal var error by mutableStateOf("")
    var bots by mutableStateOf<List<MemohBot>>(emptyList()); private set
    var bot by mutableStateOf<MemohBot?>(null); private set
    var sessions by mutableStateOf<List<MemohSession>>(emptyList()); private set
    var olderSessionsAvailable by mutableStateOf(false); private set
    var loadingSessions by mutableStateOf(false); private set
    private var nextSessionCursor: String? = null
    var queueState by mutableStateOf<MemohQueueState?>(null); private set
    var queueBusy by mutableStateOf(false); private set
    private var queueRevision = 0
    // Session runtime controls (permission/plan modes, goal, runtime commands).
    var runtimeControlsJson by mutableStateOf<JSONObject?>(null); private set
    var runtimeControlsBusy by mutableStateOf(false); private set
    var runtimeGoalJson by mutableStateOf<JSONObject?>(null); private set
    private var runtimeControlsRevision = 0
    var olderMessagesAvailable by mutableStateOf(false); private set
    var loadingOlderMessages by mutableStateOf(false); private set
    private var firstHistoryMessageId: String? = null
    var session by mutableStateOf<MemohSession?>(null); private set
    var turns by mutableStateOf<List<MemohTurn>>(emptyList()); private set
    internal var loadedHistorySessionId by mutableStateOf<String?>(null)
        private set
    internal var pendingUserText by mutableStateOf<String?>(null)
    internal var pendingUserAttachments by mutableStateOf<List<MemohAttachment>>(emptyList())
    internal var restoreDraft by mutableStateOf<String?>(null)
    internal var restoreAttachments by mutableStateOf<List<MemohAttachment>>(emptyList())
    val connected: Boolean get() = connection.state == MemohConnectionState.Connected
    var running by mutableStateOf(false); private set
    var runId by mutableStateOf(""); private set

    init {
        val saved = runCatching { MemohTokenStore.read(prefs.getString("jwt", "").orEmpty()) }
            .getOrDefault("")
        if (saved.isNotEmpty()) {
            runCatching { api.configure(login.server, saved) }
                .onSuccess { signedIn = true; loadPlatformProfile(); refreshBots() }
                .onFailure { error = it.message.orEmpty() }
        } else {
            // A sealed session cookie may already be on disk from the previous process. Ask the
            // server whether it still belongs to someone before showing the sign-in form; an
            // expired cookie answers 401 and falls through to the form.
            runCatching { api.configure(login.server) }
            viewModelScope.launch { resumeCookieSession() }
        }
    }

    fun probeLoginMethod() = signIn.probe(login.server)

    fun retrySessionRestore() {
        if (restoringSession || signedIn || login.signingIn || login.sendingCode) return
        try {
            val endpoints = MemohEndpoints.fromInput(login.server)
            if (api.scope.identity.endpoints != endpoints) api.configure(endpoints)
            navigationScope.launch { resumeCookieSession() }
        } catch (failure: IllegalArgumentException) {
            error = failure.message ?: "Enter a valid Memoh server address"
        }
    }

    fun sendLoginCode() {
        error = ""
        signIn.sendCode(login.email)
    }

    fun loginWithCode() = signIn.signInWithCode(login.code)

    fun login() = signIn.signInWithPassword(login.username.trim(), login.password)

    fun logout() {
        sessionRestoreGeneration++
        resetNavigation(); teams.reset(); signIn.signOut(); signedIn = false
        restoringSession = false
        sessionRestoreFailed = false
    }
    private fun resetNavigation() {
        navigationScope.coroutineContext[Job]?.cancelChildren()
        authRevision++; botRevision++; navigationRevision++
        attachmentDraftRevision++; historyRevision++; queueRevision++
        runtimeControlsRevision++
        closeSocket(); clearRun()
        loading = false; loadingSessions = false; loadingOlderMessages = false; queueBusy = false
        runtimeControlsJson = null; runtimeGoalJson = null; runtimeControlsBusy = false
        queueState = null; firstHistoryMessageId = null; olderMessagesAvailable = false
        consumeRestoredDraft()
        bots = emptyList(); agentController.clear(); agentController = MemohAgentController(MemohApi(api)); bot = null
        sessions = emptyList(); nextSessionCursor = null; olderSessionsAvailable = false
        session = null; turns = emptyList(); loadedHistorySessionId = null
        pendingUserText = null; pendingUserAttachments = emptyList()
        pendingUserTurnId = null
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        if (bot?.id == value.id) return
        bot?.id?.let { previousBot ->
            // An unacknowledged send keeps its own socket until admission settles. Switching the
            // visible bot never aborts a server run or discards its original invocation ID.
            if (awaitingAdmission.keys.none { sendStates[it]?.botId == previousBot }) {
                botConnections.remove(previousBot)?.close()
            }
        }
        val revision = ++botRevision
        attachmentDraftRevision++; historyRevision++
        loadingOlderMessages = false
        firstHistoryMessageId = null
        olderMessagesAvailable = false
        queueRevision++; runtimeControlsRevision++
        resetCursor()
        historyMutationSnapshot = null; pendingHistoryMutationInvocation = null
        pendingUserTurnId = null
        restoreDraft = null; restoreAttachments = emptyList()
        bot = value
        agentController.clear()
        session = null
        turns = emptyList()
        loadedHistorySessionId = null
        sessions = emptyList()
        nextSessionCursor = null
        olderSessionsAvailable = false
        clearRun()
        pendingUserText = null; pendingUserAttachments = emptyList()
        restoreConversationSendState()
        navigationScope.launch {
            runCatching { agentController.refresh(value.id) }
        }
        navigationScope.launch { agentController.loadModels() }
        navigationScope.launch {
            runCatching { api.modelPreferenceSeed(value.id) }.onSuccess { seed ->
                if (revision == botRevision && bot?.id == value.id)
                    agentController.setBotDefault(seed.optString("model_id"))
            }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == botRevision && bot?.id == value.id)
                    error = e.message ?: "Failed to load sessions"
            } finally {
                if (revision == botRevision) loadingSessions = false
            }
        }
        connectSocket(value.id)
    }

    fun selectModel(modelId: String, effort: String) {
        agentController.setModelPreference(modelId, effort)
        val owner = bot ?: return
        val selected = session ?: return
        val identity = api.scope
        val choiceRevision = agentController.preferenceRevision
        navigationScope.launch {
            modelPreferenceMutex.withLock {
                fun current() = api.scope === identity && bot?.id == owner.id && session?.id == selected.id &&
                    agentController.preferenceRevision == choiceRevision
                if (!current()) return@withLock
                try {
                    val detail = api.getSession(owner.id, selected.id)
                    if (!current()) return@withLock
                    val updated = api.updateSessionModel(owner.id, selected.id, modelId, effort, detail.modelPreferenceRevision)
                    if (current()) {
                        session = updated
                        sessions = sessions.map { if (it.id == updated.id) updated else it }
                        agentController.beginConversation(updated)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (current()) {
                        agentController.beginConversation(session)
                        error = failure.message ?: getApplication<Application>().getString(com.newoether.agora.R.string.memoh_ui_save_failed_reload_to_resolve_the_conflict)
                    }
                }
            }
        }
    }

    fun selectSession(value: MemohSession?) {
        attachmentDraftRevision++; historyRevision++
        queueRevision++
        runtimeControlsRevision++
        resetCursor()
        agentController.beginConversation(value)
        if (value != null) agentController.selectFromSession(value.botAgentId)
        // A pending retry/edit belongs to the conversation being left; never carry its undo state
        // (or its optimistic trim) into another session's transcript.
        historyMutationSnapshot = null
        pendingHistoryMutationInvocation = null
        val previous = session?.id
        session = value
        loadedHistorySessionId = null
        loadingOlderMessages = false
        turns = emptyList()
        queueState = null
        runtimeControlsJson = null; runtimeGoalJson = null
        firstHistoryMessageId = null
        olderMessagesAvailable = false
        pendingUserText = null; pendingUserAttachments = emptyList()
        pendingUserTurnId = null
        restoreDraft = null; restoreAttachments = emptyList()
        clearRun()
        restoreConversationSendState()
        if (!previous.isNullOrEmpty()) sendNow(JSONObject()
            .put("type", "runtime_unsubscribe").put("session_id", previous))
        value?.let {
            val ownerBot = bot?.id
            val revision = historyRevision
            val preferenceRevision = agentController.preferenceRevision
            if (ownerBot != null) navigationScope.launch {
                runCatching { api.getSession(ownerBot, it.id) }.onSuccess { detail ->
                    if (historyRevision == revision && session?.id == detail.id && bot?.id == ownerBot &&
                        agentController.preferenceRevision == preferenceRevision) {
                        session = detail
                        sessions = sessions.map { row -> if (row.id == detail.id) detail else row }
                        agentController.beginConversation(detail)
                    }
                }
            }
            sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", it.id))
            reloadHistory(it.id)
            refreshQueue()
        }
    }

    // ---- Session runtime controls: modes, goal, runtime commands, compaction ----

    fun refreshRuntimeControls() {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val revision = runtimeControlsRevision
        navigationScope.launch {
            try {
                val controls = api.runtimeControls(b, sid)
                val goal = if (controls.optJSONObject("capabilities")?.optBoolean("goal") == true)
                    runCatching { api.runtimeGoal(b, sid) }.getOrNull() else null
                if (runtimeControlsRevision == revision && bot?.id == b && session?.id == sid) {
                    runtimeControlsJson = controls
                    runtimeGoalJson = goal
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (runtimeControlsRevision == revision)
                    error = e.message ?: "Failed to load runtime controls"
            }
        }
    }

    fun setRuntimeMode(modeId: String, plan: Boolean) {
        mutateRuntimeControls("set the runtime mode") { b, sid -> api.setRuntimeMode(b, sid, modeId, plan) }
    }

    fun pauseRuntimeGoal() {
        mutateRuntimeControls("pause the goal") { b, sid -> api.pauseRuntimeGoal(b, sid) }
    }

    fun clearRuntimeGoal() {
        mutateRuntimeControls("clear the goal") { b, sid -> api.clearRuntimeGoal(b, sid) }
    }

    fun runRuntimeCommand(command: String) {
        mutateRuntimeControls("run the command") { b, sid -> api.runRuntimeCommand(b, sid, command) }
    }

    fun compactSession() {
        if (running) { error = "Stop the run before compacting context."; return }
        mutateRuntimeControls("compact the context") { b, sid -> api.compactSession(b, sid) }
    }

    private inline fun mutateRuntimeControls(
        action: String,
        noinline block: suspend MemohApi.(botId: String, sessionId: String) -> JSONObject?,
    ) {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        if (runtimeControlsBusy) return
        val revision = runtimeControlsRevision
        runtimeControlsBusy = true
        navigationScope.launch {
            try {
                api.block(b, sid)
                if (runtimeControlsRevision == revision) refreshRuntimeControls()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (runtimeControlsRevision == revision) error = e.message ?: "Could not $action"
            } finally {
                if (runtimeControlsRevision == revision) runtimeControlsBusy = false
            }
        }
    }

    fun newChat() = selectSession(null)

    fun selectAgentForNewChat(agentId: String) {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        mutateQueue("remove the queued message") { b, sid -> api.cancelQueueItem(b, sid, item) }
    }

    fun editQueued(item: MemohQueueItem, text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        mutateQueue("edit the queued message") { b, sid -> api.editQueueItem(b, sid, item, value) }
    }

    /** Move [item] above [beforeId] within its queue; null moves it to the end. */
    fun reorderQueued(item: MemohQueueItem, beforeId: String?) {
        mutateQueue("reorder the queue") { b, sid -> api.reorderQueue(b, sid, item.type, item, beforeId) }
    }

    /** Promote an accepted follow-up to steer the active run. */
    fun promoteQueued(item: MemohQueueItem) {
        if (item.type != "follow_up") return
        mutateQueue("promote the message to steering") { b, sid -> api.promoteQueueItem(b, sid, item) }
    }

    private inline fun mutateQueue(
        action: String,
        noinline block: suspend MemohApi.(botId: String, sessionId: String) -> Unit,
    ) {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        if (queueBusy) return
        val revision = queueRevision
        queueBusy = true
        navigationScope.launch {
            try {
                api.block(b, sid)
                if (revision == queueRevision) refreshQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == queueRevision) error = e.message ?: "Could not $action"
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == historyRevision) error = e.message ?: "Unable to rename conversation"
            }
        }
    }

    fun deleteSelectedSession() {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val revision = historyRevision
        if (running || hasCurrentAdmission) {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                    olderMessagesAvailable = page.size >= HISTORY_PAGE_SIZE && firstHistoryMessageId != firstId
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == historyRevision)
                    error = e.message ?: "Failed to load earlier messages"
            } finally {
                if (revision == historyRevision) loadingOlderMessages = false
            }
        }
    }

    fun consumeRestoredDraft() {
        restoreDraft = null; restoreAttachments = emptyList()
        sendStates.values.filter { it.belongsTo(bot?.id, session?.id) && it.failure.isNotBlank() }
            .forEach { it.draftDelivered = true }
    }

    private fun restoreConversationSendState() {
        val state = sendStates.values.lastOrNull { it.belongsTo(bot?.id, session?.id) } ?: return
        if (state.failure.isNotBlank()) {
            if (!state.draftDelivered) {
                restoreDraft = state.text
                restoreAttachments = state.attachments
                error = state.failure
            }
        } else if (state.text != null) {
            pendingUserText = state.text
            pendingUserAttachments = state.attachments
        }
    }

    internal fun failAdmission(invocation: String, message: String) {
        val payload = awaitingAdmission.remove(invocation) ?: return
        cancelAdmissionTimeout(invocation)
        val state = sendStates[invocation]
        state?.failure = message
        val visible = state?.belongsTo(bot?.id, session?.id)
            ?: (payload.optString("session_id") == session?.id.orEmpty())
        if (visible) {
            if (state?.text != null) {
                restoreDraft = state.text
                restoreAttachments = state.attachments
                pendingUserText = null; pendingUserAttachments = emptyList()
            } else if (state == null && pendingUserText != null) restorePendingDraft()
            restoreHistoryMutation(invocation)
            error = message
        }
        releaseBackgroundConnection(state?.botId)
    }

    private fun releaseBackgroundConnection(ownerBotId: String?) {
        if (ownerBotId != null && ownerBotId != bot?.id &&
            awaitingAdmission.keys.none { sendStates[it]?.botId == ownerBotId }) {
            botConnections.remove(ownerBotId)?.close()
        }
    }

    internal fun restorePendingDraft() {
        restoreDraft = pendingUserText.orEmpty()
        restoreAttachments = pendingUserAttachments
        pendingUserText = null
        pendingUserAttachments = emptyList()
    }

    fun dismissError() { error = "" }

    // ---- Settings surface ----------------------------------------------------------------
    // Settings pages read and change state here rather than reaching into internal fields, so a
    // page never has to know how a preference is persisted.

    /** True when the interface language changed and the activity must be recreated to apply it. */
    internal var languageRestartPending by mutableStateOf(false)
        private set

    fun requestAppLanguageRestart() { languageRestartPending = true }

    fun consumeAppLanguageRestart() { languageRestartPending = false }

    /** Opens the assistant picker from Settings without leaving the drawer's selection path. */
    fun switchBotFromSettings(value: MemohBot) {
        if (value.id == bot?.id) return
        selectBot(value)
    }

    /** One-line health summary used by both the settings home rows and diagnostics. */
    internal fun connectionSummary(): String = when (connection.state) {
        MemohConnectionState.Connected -> "Connected"
        MemohConnectionState.Connecting -> "Connecting…"
        MemohConnectionState.Retrying -> "Reconnecting…"
        MemohConnectionState.Blocked -> "Connection failed"
        MemohConnectionState.Idle -> "Disconnected"
    }

    /** Surface a rejected attachment in the send-error banner instead of dropping it. */
    fun reportAttachmentError(message: String) {
        if (message.isNotBlank()) error = message
    }
    fun send(text: String, attachments: List<MemohAttachment> = emptyList()): Boolean {
        val value = text.trim()
        val selectedBot = bot ?: return false
        if ((value.isEmpty() && attachments.isEmpty()) || pendingUserText != null || running || hasCurrentAdmission) return false
        attachmentDraftError(attachments)?.let { error = it; return false }
        error = ""
        consumeRestoredDraft()
        val invocation = UUID.randomUUID().toString()
        val payload = JSONObject().put("type", "message")
            .put("invocation_id", invocation).put("text", value)
        if (attachments.isNotEmpty()) {
            val arr = JSONArray()
            attachments.forEach { att ->
                val o = JSONObject().put("id", att.id.ifBlank { UUID.randomUUID().toString() })
                    .put("name", att.name).put("mime", att.mime).put("type", att.type).put("size", att.size)
                if (att.base64.isNotBlank()) o.put("base64", att.wireBase64())
                if (att.url.isNotBlank()) o.put("url", att.url)
                arr.put(o)
            }
            payload.put("attachments", arr)
        }
        val existingSession = session
        existingSession?.id?.let { payload.put("session_id", it) }
        // Web composer parity: attach an explicit model preference only when the user chose one.
        if (agentController.preferredModelId.isNotBlank()) {
            payload.put("model_id", agentController.preferredModelId)
            payload.put("reasoning_effort", agentController.preferredReasoningEffort)
        }
        awaitingAdmission[invocation] = payload
        sendStates[invocation] = MemohSendState(selectedBot.id, session?.id, value, attachments,
            agentController.selectedAgentId, navigationRevision = historyRevision)
        pendingUserText = value
        pendingUserAttachments = attachments
        attachmentDraftRevision++
        armAdmissionTimeout(invocation, value)
        if (existingSession != null) {
            if (connected) {
                sendNow(payload)
            } else ensureConnection(selectedBot.id)
        } else {
            val authRevisionAtSend = authRevision
            val historyRevisionAtSend = historyRevision
            val pending = checkNotNull(sendStates[invocation])
            navigationScope.launch {
                try {
                    val created = api.createSession(selectedBot.id, pending.agentId,
                        payload.optString("model_id"), payload.optString("reasoning_effort"))
                    if (authRevisionAtSend != authRevision || !awaitingAdmission.containsKey(invocation)) return@launch
                    pending.sessionId = created.id
                    payload.put("session_id", created.id)
                    if (bot?.id == selectedBot.id) {
                        sessions = listOf(created) + sessions.filterNot { it.id == created.id }
                        // HTTP creation may finish after navigation. Only the original visible draft
                        // follows its newly allocated session; another chat is never pulled back.
                        if (session == null && historyRevisionAtSend == historyRevision) {
                            historyRevision++
                            resetCursor()
                            session = created
                            loadedHistorySessionId = created.id
                            sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", created.id))
                        }
                    }
                    val ownerConnection = connectionFor(selectedBot.id)
                    if (ownerConnection.state == MemohConnectionState.Connected) sendToBot(selectedBot.id, payload)
                    else ensureConnection(selectedBot.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (authRevisionAtSend == authRevision)
                        failAdmission(invocation, e.message ?: "Failed to create conversation")
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

    /**
     * Retry from a turn or edit an already-sent user turn (web `retry_message`/`edit_message`).
     * Only meaningful when no run is active; Homem's client hides both actions otherwise.
     * The server creates the follow-up run; [turnId] addresses the turn to mutate.
     */
    fun mutateTurn(turnId: String, retry: Boolean, text: String? = null): Boolean {
        val sid = session?.id?.takeIf { it.isNotBlank() } ?: return false
        if (turnId.isBlank()) return false
        if (running || hasCurrentAdmission || pendingUserText != null) {
            error = "Wait for the current run to finish before editing history."
            return false
        }
        val payload = JSONObject().put("type", if (retry) "retry_message" else "edit_message")
            .put("invocation_id", UUID.randomUUID().toString())
            .put("session_id", sid)
            .put("turn_id", turnId)
        text?.let { payload.put("text", it) }
        if (agentController.preferredModelId.isNotBlank()) payload.put("model_id", agentController.preferredModelId)
        val invocation = payload.optString("invocation_id")
        // Drop the rows the server is about to replace before its reply starts streaming. Without
        // this the stale assistant turn stays on screen and the new run appends below it, so the
        // transcript reads as two answers until the terminal refresh swaps everything out.
        if (truncateForHistoryMutation(turnId, retry, text)) {
            pendingHistoryMutationInvocation = invocation
        }
        awaitingAdmission[invocation] = payload
        sendStates[invocation] = MemohSendState(bot?.id.orEmpty(), sid, null)
        armAdmissionTimeout(invocation, text.orEmpty())
        if (connected) {
            sendNow(payload)
        } else {
            error = "Reconnecting to send your edit."
            ensureConnection(bot?.id ?: return false)
        }
        return true
    }

    /**
     * Optimistically trims history for a retry/edit, the way the server will.
     *
     * A retry keeps the user turn and drops its answer plus everything after it. An edit keeps the
     * addressed user turn too — rewritten in place when new text was supplied — and drops only the
     * replies that followed. Either way the tail the server is about to regenerate disappears
     * immediately, so the new streaming run can never render below a stale answer.
     *
     * @return true when rows were removed and [historyMutationSnapshot] holds the undo state.
     */
    private fun truncateForHistoryMutation(turnId: String, retry: Boolean, text: String?): Boolean {
        val trimmed = trimmedHistory(turns, turnId, text) ?: return false
        // A retry never rewrites the question; only an edit may replace its wording.
        historyMutationSnapshot = trimmed.undo
        turns = if (retry) trimmed.surviving else trimmed.result
        return true
    }

    /**
     * Undo a rejected history mutation. The server refused the retry/edit, so the rows that were
     * optimistically removed are still the source of truth.
     */
    internal fun restoreHistoryMutation(invocation: String? = null) {
        if (invocation != null && invocation != pendingHistoryMutationInvocation) return
        val snapshot = historyMutationSnapshot ?: return
        historyMutationSnapshot = null
        pendingHistoryMutationInvocation = null
        val current = turns
        val restored = if (snapshot.cutIndex <= current.size) {
            current.take(snapshot.cutIndex) + snapshot.removed
        } else {
            snapshot.removed
        }
        turns = restored.distinctBy { it.id }
    }

    /** Settle a successful mutation: keep the trimmed history and forget the undo state. */
    private fun commitHistoryMutation() {
        historyMutationSnapshot = null
        pendingHistoryMutationInvocation = null
    }

    /** Fork the conversation from a turn; the server returns the new session (Homem parity). */
    fun forkFromTurn(turnId: String) {
        val b = bot?.id ?: return
        val sid = session?.id?.takeIf { it.isNotBlank() } ?: return
        if (turnId.isBlank() || running) return
        navigationScope.launch {
            try {
                val forked = api.forkSession(b, sid, turnId) ?: return@launch
                val revision = ++navigationRevision
                if (revision == navigationRevision && bot?.id == b) {
                    sessions = listOf(forked) + sessions.filterNot { it.id == forked.id }
                    selectSession(forked)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: "Could not fork the conversation"
            }
        }
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
                    loadedHistorySessionId = sid
                    if (firstHistoryMessageId == null) {
                        firstHistoryMessageId = result.firstOrNull()?.id
                        // A short page is the only proof that this is the start of history.
                        olderMessagesAvailable = result.size >= HISTORY_PAGE_SIZE
                    }
                    if (!running && !hasCurrentAdmission) {
                        pendingUserText = null
                        pendingUserAttachments = emptyList()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (session?.id == sid && revision == historyRevision)
                    error = e.message ?: "Failed to load history"
            }
        }
    }

    private fun sendNow(value: JSONObject): Boolean = bot?.id?.let { sendToBot(it, value) } ?: false

    private fun sendToBot(botId: String, value: JSONObject): Boolean = connectionFor(botId).send(value)

    private fun connectSocket(botId: String) = connectionFor(botId).connect(botId)

    private fun ensureConnection(botId: String) {
        val ownerConnection = connectionFor(botId)
        if (ownerConnection.state != MemohConnectionState.Connecting && ownerConnection.state != MemohConnectionState.Retrying) {
            ownerConnection.connect(botId)
        }
    }

    fun retryConnection() = connection.retry()

    private fun receive(event: JSONObject) = receiveFromBot(bot?.id.orEmpty(), event)

    private fun receiveFromBot(ownerBotId: String, event: JSONObject) {
        val kind = event.optString("type")
        val target = event.optString("session_id")
        val invocation = event.optString("invocation_id")
        val pending = sendStates[invocation]
        val visibleBot = ownerBotId == bot?.id
        // A background connection may settle its own send. Its runtime and controls cannot mutate
        // whichever conversation the user opened in the meantime.
        if (!visibleBot && kind !in setOf("session_created", "run_accepted", "run_rejected", "error")) return
        when (kind) {
            "session_created" -> {
                if (target.isNotBlank() && invocation.isNotEmpty() && awaitingAdmission.containsKey(invocation)) {
                    val followsDraft = visibleBot && (session?.id == target ||
                        (session == null && (pending == null || pending.navigationRevision == historyRevision)))
                    awaitingAdmission[invocation]?.put("session_id", target)
                    pending?.sessionId = target
                    val created = MemohSession(target, "New conversation", pending?.agentId.orEmpty())
                    if (visibleBot) sessions = listOf(created) + sessions.filterNot { it.id == target }
                    if (followsDraft) {
                        historyRevision++
                        resetCursor()
                        session = created
                        loadedHistorySessionId = target
                        sendNow(JSONObject().put("type", "runtime_subscribe").put("session_id", target))
                    }
                }
            }
            "run_accepted" -> {
                if (awaitingAdmission.remove(invocation) != null) cancelAdmissionTimeout(invocation)
                pending?.accepted = true
                // The mutation is now the server's own history; the trimmed transcript stands.
                if (invocation.isNotBlank() && invocation == pendingHistoryMutationInvocation) {
                    commitHistoryMutation()
                }
                if (visibleBot && session?.id == target) {
                    refreshQueue()
                    settlingRunId = ""
                    runId = event.optString("run_id")
                    running = true
                }
                releaseBackgroundConnection(ownerBotId)
            }
            "model_preference_settled" -> {
                val settled = event.optString("model_id")
                if (target == session?.id && settled.isNotBlank()) agentController.onModelPreferenceSettled(settled)
            }
            "run_rejected", "error" -> {
                val message = event.optString("message").ifBlank { event.optString("code").ifBlank { "Run rejected" } }
                if (invocation.isNotBlank()) failAdmission(invocation, message)
                // A refused retry/edit never touches the server, so the optimistic trim must be
                // undone or the transcript loses rows that are still authoritative.
                if (invocation.isNotBlank() && invocation == pendingHistoryMutationInvocation) {
                    restoreHistoryMutation()
                }
                if (pending?.belongsTo(bot?.id, session?.id) ?: (visibleBot && (target.isBlank() || session?.id == target)))
                    error = message
                if (visibleBot && session?.id == target && runId == event.optString("run_id") && runId.isNotEmpty()) {
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
                if (decisionId != null && !event.optBoolean("applied", false))
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

    internal fun applyRunView(view: JSONObject) {
        runId = view.optString("run_id")
        currentRunStatus = view.optString("status")
        liveJson = view.optJSONArray("messages") ?: JSONArray()
        liveBlocks = decodeBlocks(liveJson)
        pruneResolvedDecisions()
        settleIfTerminal()
    }

    /** Delta merge is owned by MemohLiveTranscript.kt. */
    private fun applyDelta(delta: JSONObject, sid: String) = applyRuntimeDelta(delta, sid)

    internal fun pruneResolvedDecisions() {
        val unresolved = liveBlocks.flatMap { block ->
            buildList {
                if (block.canApprove) add(block.approvalId)
                if (block.canRespond) add(block.userInputId)
            }
        }.filter { it.isNotBlank() }.toSet()
        waitingForInteraction = waitingForInteraction.intersect(unresolved)
    }

    internal fun upsertBlock(block: JSONObject) {
        // A tool upsert may carry only its tool_call_id; the reference client matches on
        // either identifier and stamps the row with the surviving id (Homem RuntimeState).
        val id = block.optInt("id", -1)
        val toolCallId = block.optString("tool_call_id")
        val existing = (0 until liveJson.length()).firstOrNull { index ->
            val row = liveJson.optJSONObject(index) ?: return@firstOrNull false
            (id >= 0 && row.optInt("id", -1) == id) ||
                (toolCallId.isNotBlank() && row.optString("tool_call_id") == toolCallId)
        }
        if (existing != null) {
            if (id >= 0) block.put("id", liveJson.optJSONObject(existing)!!.optInt("id", id))
            liveJson.put(existing, block)
        } else liveJson.put(block)
    }

    internal fun settleIfTerminal(sid: String = session?.id.orEmpty()) {
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
                        // The run that carried the retry/edit completed, so the trim is final even
                        // if the acceptance frame was lost.
                        commitHistoryMutation()
                        clearRun()
                        val refreshed = api.sessionsPage(botId)
                        if (bot?.id == botId && session?.id == sid) {
                            sessions = (refreshed.items + sessions).distinctBy { it.id }
                            nextSessionCursor = refreshed.nextCursor
                            olderSessionsAvailable = nextSessionCursor != null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
        botConnections.values.toList().forEach { it.close() }
        botConnections.clear()
        sendStates.clear()
        resetCursor()
        pendingControls.clear()
        waitingForInteraction = emptySet()
        awaitingAdmission.clear()
        cancelAdmissionTimeout()
        pendingUserTurnId = null
    }

    override fun onCleared() {
        closeSocket()
        super.onCleared()
    }
}
