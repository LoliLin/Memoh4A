package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newoether.agora.R
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

/**
 * ~2 minutes of handoff retries (1+2+4+8+16+30+30s). Long enough to absorb a slow history write,
 * short enough that a server which never records the turn does not retry for the whole session.
 */
internal const val MAX_SETTLEMENT_RETRIES = 7

internal class MemohViewModel(application: Application) : AndroidViewModel(application) {
    internal val navigationScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + kotlinx.coroutines.SupervisorJob(viewModelScope.coroutineContext[Job]))

    /** User-facing text must follow the app language; a bare English literal cannot. */
    internal fun text(id: Int, vararg args: Any): String =
        if (args.isEmpty()) getApplication<Application>().getString(id)
        else getApplication<Application>().getString(id, *args)
    private val prefs = application.getSharedPreferences("memoh_native", 0).also {
        // Hand the transport its cookie store before any Identity is built.
        MemohTransport.cookiePrefs = it
    }
    private var authRevision = 0
    private var botRevision = 0
    private var welcomeSeedRevision = 0
    private val modelPreferenceMutex = Mutex()
    internal var attachmentDraftRevision by mutableStateOf(0); private set
    internal var historyRevision = 0; private set
    internal var navigationRevision = 0
    private var epoch = ""
    private var seq = -1L
    private var awaitingSnapshot = true
    private var settlementRetryDelayMillis = 1_000L
    /** Counts consecutive handoffs for one finished run so a lagging server cannot retry forever. */
    private var settlementAttempts = 0

    internal val api = MemohApi()
    internal val workspaceApi: MemohApi get() = api
    internal val settings by lazy { SettingsRepository(SettingsManager(application), viewModelScope) }
    internal var agentController = newAgentController(); private set

    private fun newAgentController() = MemohAgentController(
        api,
        agentsFallback = { text(R.string.memoh_error_load_agents) },
        profilesFallback = { text(R.string.memoh_error_acp_unavailable) },
    )
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
                    error = text(R.string.memoh_error_connection_lost_decision)
                    pendingControls.clear()
                    waitingForInteraction = emptySet()
                }
            }
        },
        onUnauthorized = { logout(); error = text(R.string.memoh_error_session_expired) },
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
    internal var pendingUserTurnId by mutableStateOf<String?>(null)
    private val pendingControls = mutableMapOf<String, String>()
    var waitingForInteraction by mutableStateOf<Set<String>>(emptySet())
        private set
    val controlsReady: Boolean get() = connected && !awaitingSnapshot && runId.isNotBlank()
    // Transcript state shared with the MemohLiveTranscript.kt extension.
    internal var liveJson = JSONArray()
    internal var currentRunStatus = ""
    internal var liveBlocks by mutableStateOf<List<MemohBlock>>(emptyList())
    internal var liveTurnId by mutableStateOf("")
    internal var liveUserTurns by mutableStateOf<List<MemohTurn>>(emptyList())
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

    /** Applying an edited profile keeps the drawer and account page in sync without a reload. */
    internal fun applyPlatformProfile(displayName: String, avatarUrl: String) {
        if (displayName.isNotBlank()) profileDisplayName = displayName
        profileAvatarUrl = avatarUrl
    }

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
            val endpoints = MemohEndpoints.fromInput(login.server, MemohServerMessages.from(::text))
            if (api.scope.identity.endpoints != endpoints) api.configure(endpoints)
            navigationScope.launch { resumeCookieSession() }
        } catch (failure: IllegalArgumentException) {
            error = failure.message ?: text(R.string.memoh_error_server_address)
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
        bots = emptyList(); agentController.clear(); agentController = newAgentController(); bot = null
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
                    error = text(R.string.memoh_error_session_expired)
                } else error = e.message.orEmpty()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (revision == authRevision) error = e.message ?: text(R.string.memoh_error_load_bots)
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
        // One request, not two: the default pair and the welcome seed feed the same picker,
        // and reading them back to back keeps the new-chat composer settled in one step.
        refreshWelcomePreferences(value.id)
        navigationScope.launch {
            loadingSessions = true
            try {
                val page = api.sessionsPage(value.id)
                if (revision == botRevision && bot?.id == value.id) {
                    // A new or forked conversation may have been added while this initial page
                    // was in flight. Keep those local rows and append only unseen server rows.
                    sessions = (sessions + page.items).distinctBy { it.id }
                    nextSessionCursor = page.nextCursor
                    olderSessionsAvailable = page.nextCursor != null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == botRevision && bot?.id == value.id)
                    error = e.message ?: text(R.string.memoh_error_load_sessions)
            } finally {
                if (revision == botRevision) loadingSessions = false
            }
        }
        connectSocket(value.id)
    }

    /**
     * The bot's own default pair plus the latest native session's pair.
     *
     * Both feed one picker: the default is what a new conversation follows, and the seed is
     * what the composer carries on send. Each is optional on its own (an older server has no
     * seed route, a restricted account may not read settings), so a failure in one must not
     * hide the other.
     */
    private fun refreshWelcomePreferences(botId: String) {
        val revision = botRevision
        val requestRevision = ++welcomeSeedRevision
        val preferenceRevision = agentController.preferenceRevision
        navigationScope.launch {
            var seed: String? = null
            var seedEffort: String? = null
            try {
                val settings = api.botSettings(botId)
                if (revision != botRevision || bot?.id != botId) return@launch
                agentController.setBotDefault(
                    settings.optString("chat_model_id"),
                    settings.optString("reasoning_effort"),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep whatever default the composer already follows.
            }
            try {
                val response = api.modelPreferenceSeed(botId)
                seed = response.optString("model_id")
                seedEffort = response.optString("reasoning_effort")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Servers before the seed route simply keep the bot default.
            }
            if (revision != botRevision || requestRevision != welcomeSeedRevision || bot?.id != botId) {
                return@launch
            }
            val apply = session == null && !hasCurrentAdmission &&
                agentController.preferenceRevision == preferenceRevision
            when {
                seed != null -> agentController.setWelcomeSeed(seed, seedEffort.orEmpty(), apply)
                // No seed route: the bot default is still what a new chat should follow.
                apply -> agentController.applyWelcomeSeed()
            }
        }
    }

    fun selectModel(modelId: String, effort: String) {
        val previousModel = agentController.currentModelId
        agentController.setModelPreference(modelId, effort)
        if (modelId != previousModel) agentController.reconcileComposerPreference()
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
                    error = e.message ?: text(R.string.memoh_error_load_runtime_controls)
            }
        }
    }

    fun setRuntimeMode(modeId: String, plan: Boolean) {
        mutateRuntimeControls(R.string.memoh_action_set_runtime_mode) { b, sid ->
            api.setRuntimeMode(b, sid, modeId, plan)
        }
    }

    fun pauseRuntimeGoal() {
        mutateRuntimeControls(R.string.memoh_action_pause_goal) { b, sid -> api.pauseRuntimeGoal(b, sid) }
    }

    fun clearRuntimeGoal() {
        mutateRuntimeControls(R.string.memoh_action_clear_goal) { b, sid -> api.clearRuntimeGoal(b, sid) }
    }

    fun runRuntimeCommand(command: String) {
        mutateRuntimeControls(R.string.memoh_action_run_command) { b, sid ->
            api.runRuntimeCommand(b, sid, command)
        }
    }

    fun compactSession() {
        if (running) { error = text(R.string.memoh_error_stop_before_compact); return }
        mutateRuntimeControls(R.string.memoh_action_compact_context) { b, sid -> api.compactSession(b, sid) }
    }

    private inline fun mutateRuntimeControls(
        action: Int,
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
                if (runtimeControlsRevision == revision) {
                    error = e.message ?: text(R.string.memoh_error_action_failed, text(action))
                }
            } finally {
                if (runtimeControlsRevision == revision) runtimeControlsBusy = false
            }
        }
    }

    fun newChat() {
        selectSession(null)
        bot?.id?.let(::refreshWelcomePreferences)
    }

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
                if (queueRevision == revision) error = e.message ?: text(R.string.memoh_error_queue_load)
            }
        }
    }

    fun queueMessage(text: String, type: String): Boolean {
        val b = bot?.id ?: return false
        val sid = session?.id ?: return false
        val value = text.trim()
        if (value.isBlank() || !running || queueBusy || type !in setOf("steer", "follow_up")) return false
        if (type == "steer" && queueState?.steerSupported != true) {
            error = text(R.string.memoh_error_steer_unsupported)
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
                    error = text(
                        R.string.memoh_error_queue_uncertain,
                        e.message ?: text(R.string.memoh_error_request_failed),
                    )
                }
            } finally {
                if (queueRevision == revision) queueBusy = false
            }
        }
        return true
    }

    fun cancelQueued(item: MemohQueueItem) {
        mutateQueue(R.string.memoh_error_remove_queued) { b, sid -> api.cancelQueueItem(b, sid, item) }
    }

    fun editQueued(item: MemohQueueItem, text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        mutateQueue(R.string.memoh_error_edit_queued) { b, sid -> api.editQueueItem(b, sid, item, value) }
    }

    /** Move [item] above [beforeId] within its queue; null moves it to the end. */
    fun reorderQueued(item: MemohQueueItem, beforeId: String?) {
        mutateQueue(R.string.memoh_error_reorder_queue) { b, sid -> api.reorderQueue(b, sid, item.type, item, beforeId) }
    }

    /** Promote an accepted follow-up to steer the active run. */
    fun promoteQueued(item: MemohQueueItem) {
        if (item.type != "follow_up") return
        mutateQueue(R.string.memoh_error_promote_queue) { b, sid -> api.promoteQueueItem(b, sid, item) }
    }

    private inline fun mutateQueue(
        action: Int,
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
                if (revision == queueRevision) error = e.message ?: text(R.string.memoh_error_action_failed, text(action))
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
                if (revision == botRevision) error = e.message ?: text(R.string.memoh_error_load_sessions)
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
                if (revision == historyRevision) error = e.message ?: text(R.string.memoh_error_rename_session)
            }
        }
    }

    fun deleteSelectedSession() {
        val b = bot?.id ?: return
        val sid = session?.id ?: return
        val revision = historyRevision
        if (running || hasCurrentAdmission) {
            error = text(R.string.memoh_error_stop_before_delete)
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
                if (revision == historyRevision) error = e.message ?: text(R.string.memoh_error_delete_session)
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
                    turns = memohOrderTurns((page + turns).distinctBy(::memohTurnIdentity))
                    firstHistoryMessageId = page.firstOrNull()?.id ?: firstId
                    olderMessagesAvailable = page.size >= HISTORY_PAGE_SIZE && firstHistoryMessageId != firstId
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (revision == historyRevision)
                    error = e.message ?: text(R.string.memoh_error_load_earlier)
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
            pendingUserTurnId = state.serverTurnId.takeIf { it.isNotBlank() }
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
                pendingUserText = null; pendingUserAttachments = emptyList(); pendingUserTurnId = null
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
        pendingUserTurnId = null
    }

    fun dismissError() { error = "" }

    // ---- Settings surface ----------------------------------------------------------------
    // Settings pages read and change state here rather than reaching into internal fields, so a
    // page never has to know how a preference is persisted.

    /** True when the interface language changed and the activity must be recreated to apply it. */
    internal var languageRestartPending by mutableStateOf(false)
        private set

    private var languageChangeInProgress = false

    fun requestAppLanguageRestart(language: String) {
        if (languageChangeInProgress || languageRestartPending) return
        languageChangeInProgress = true
        viewModelScope.launch {
            try {
                // Persist before recreation so attachBaseContext reads the new value.
                settings.setAppLanguage(language).join()
                languageRestartPending = true
            } finally {
                languageChangeInProgress = false
            }
        }
    }

    fun consumeAppLanguageRestart() { languageRestartPending = false }

    /** Opens the assistant picker from Settings without leaving the drawer's selection path. */
    fun switchBotFromSettings(value: MemohBot) {
        if (value.id == bot?.id) return
        selectBot(value)
    }

    /** One-line health summary used by both the settings home rows and diagnostics. */
    internal fun connectionSummary(): String = when (connection.state) {
        MemohConnectionState.Connected -> text(R.string.memoh_status_connected)
        MemohConnectionState.Connecting -> text(R.string.memoh_status_connecting)
        MemohConnectionState.Retrying -> text(R.string.memoh_status_reconnecting)
        MemohConnectionState.Blocked -> text(R.string.memoh_status_failed)
        MemohConnectionState.Idle -> text(R.string.memoh_status_disconnected)
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
        pendingUserTurnId = null
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
                        failAdmission(invocation, e.message ?: text(R.string.memoh_error_create_session))
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
            error = text(R.string.memoh_error_wait_before_edit)
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
            error = text(R.string.memoh_error_reconnecting_edit)
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
                error = e.message ?: text(R.string.memoh_error_fork_session)
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

    private fun reconcileAcceptedTurns(botId: String, sid: String, history: List<MemohTurn>, completedRunId: String = "") {
        val userIds = history.asSequence().filter { it.role == "user" }.map { it.turnId }
            .filter { it.isNotBlank() }.toSet()
        val current = sendStates.entries.lastOrNull {
            it.value.belongsTo(botId, sid) && it.value.text != null && it.value.failure.isBlank()
        }?.key
        // This runs from reloadHistory too, where liveTurnId may already belong to a newer run.
        // Only trust it when the handoff is settling the run that produced it.
        val liveTurnConfirmed = completedRunId.isNotBlank() && liveTurnId.isNotBlank() && liveTurnId in userIds
        val confirmed = sendStates.filter { (_, state) ->
            state.belongsTo(botId, sid) && state.accepted && (
                (state.serverTurnId.isNotBlank() && state.serverTurnId in userIds) ||
                    (state.serverTurnId.isBlank() && liveTurnConfirmed &&
                        state.serverRunId == completedRunId)
                )
        }.keys.toSet()
        if (bot?.id == botId && session?.id == sid &&
            ((current != null && current in confirmed) || pendingUserTurnId?.let { it in userIds } == true)) {
            pendingUserText = null
            pendingUserAttachments = emptyList()
            pendingUserTurnId = null
        }
        confirmed.forEach { sendStates.remove(it) }
    }

    private fun reloadHistory(sid: String) {
        val b = bot?.id ?: return
        val revision = historyRevision
        navigationScope.launch {
            try {
                val result = api.history(b, sid)
                if (bot?.id == b && session?.id == sid && revision == historyRevision) {
                    val older = turns
                    val updatedIds = result.mapTo(HashSet(), ::memohTurnIdentity)
                    turns = memohOrderTurns((older.filterNot { memohTurnIdentity(it) in updatedIds } + result)
                        .distinctBy(::memohTurnIdentity))
                    reconcileAcceptedTurns(b, sid, result)
                    loadedHistorySessionId = sid
                    if (firstHistoryMessageId == null) {
                        firstHistoryMessageId = result.firstOrNull()?.id
                        // A short page is the only proof that this is the start of history.
                        olderMessagesAvailable = result.size >= HISTORY_PAGE_SIZE
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (session?.id == sid && revision == historyRevision)
                    error = e.message ?: text(R.string.memoh_error_load_history)
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
                pending?.apply {
                    accepted = true
                    serverTurnId = event.optString("turn_id")
                    serverRunId = event.optString("run_id")
                }
                if (pending?.belongsTo(bot?.id, session?.id) == true && pending.text != null && pending.serverTurnId.isNotBlank()) {
                    pendingUserTurnId = pending.serverTurnId
                }
                // The mutation is now the server's own history; the trimmed transcript stands.
                if (invocation.isNotBlank() && invocation == pendingHistoryMutationInvocation) {
                    commitHistoryMutation()
                }
                if (visibleBot && session?.id == target) {
                    refreshQueue()
                    settlingRunId = ""
                    settlementRetryDelayMillis = 1_000L
                    settlementAttempts = 0
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
                val snapshot = event.optJSONObject("snapshot")
                val nextEpoch = snapshot?.optString("epoch").orEmpty().ifBlank { event.optString("epoch") }
                val nextSeq = if (snapshot != null && snapshot.has("seq")) snapshot.optLong("seq", -1L)
                    else event.optLong("seq", -1L)
                if (nextEpoch.isBlank() || nextSeq < 0L) return
                if (!awaitingSnapshot && nextEpoch == epoch && nextSeq <= seq) return
                epoch = nextEpoch
                seq = nextSeq
                awaitingSnapshot = false
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
                error = text(R.string.memoh_error_live_updates_dropped)
                requestFreshSnapshot(target)
                reloadHistory(target)
            }
            "control_ack" -> {
                val controlId = event.optString("control_id")
                val decisionId = pendingControls.remove(controlId)
                if (decisionId != null && !event.optBoolean("applied", false))
                    waitingForInteraction = waitingForInteraction - decisionId
                if (decisionId != null && !event.optBoolean("applied", false))
                    error = text(R.string.memoh_error_action_not_applied, event.optString("code"))
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

    internal fun applyRunView(view: JSONObject, settle: Boolean = true) {
        runId = view.optString("run_id")
        currentRunStatus = view.optString("status")
        liveTurnId = view.optString("turn_id")
        liveJson = view.optJSONArray("messages") ?: JSONArray()
        liveBlocks = decodeBlocks(liveJson)
        applyRuntimeUsers(view)
        pruneResolvedDecisions()
        if (settle) settleIfTerminal()
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
            val previousId = liveJson.optJSONObject(existing)!!.optInt("id", -1)
            if (previousId >= 0) block.put("id", previousId)
            liveJson.put(existing, block)
        } else liveJson.put(block)
    }

    internal fun settleIfTerminal(sid: String = session?.id.orEmpty()) {
        running = currentRunStatus in setOf("admitting", "running", "waiting_decision", "aborting", "finishing")
        if (!running && currentRunStatus.isNotEmpty() && sid.isNotBlank() && settlingRunId != runId) {
            val finishedRun = runId
            val botId = bot?.id ?: return
            val revision = historyRevision
            settlingRunId = finishedRun
            navigationScope.launch {
                try {
                    val settled = api.history(botId, sid)
                    if (bot?.id == botId && session?.id == sid && historyRevision == revision &&
                        runId == finishedRun && !running) {
                        val updatedIds = settled.mapTo(HashSet(), ::memohTurnIdentity)
                        turns = memohOrderTurns((turns.filterNot { memohTurnIdentity(it) in updatedIds } + settled)
                            .distinctBy(::memohTurnIdentity))
                        reconcileAcceptedTurns(botId, sid, settled, finishedRun)
                        // A successful response can still lag the runtime. Do not discard its answer
                        // until history contains the corresponding assistant turn.
                        if (liveTurnId.isNotBlank() && liveBlocks.isNotEmpty() &&
                            settled.none { it.role == "assistant" && it.turnId == liveTurnId }) {
                            retrySettlement(botId, sid, finishedRun, revision)
                            return@launch
                        }
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
                    if (bot?.id == botId && session?.id == sid && historyRevision == revision) {
                        error = e.message ?: text(R.string.memoh_error_load_settled)
                        if (runId == finishedRun && !running) {
                            retrySettlement(botId, sid, finishedRun, revision)
                        }
                    }
                }
            }
        }
    }

    private suspend fun retrySettlement(botId: String, sid: String, finishedRun: String, revision: Int) {
        // A permanently lagging server must not leave the observer retrying forever. The live
        // transcript is already on screen, so give up and let the next refresh reconcile.
        if (++settlementAttempts > MAX_SETTLEMENT_RETRIES) {
            settlingRunId = ""
            settlementAttempts = 0
            settlementRetryDelayMillis = 1_000L
            clearRun()
            // The optimistic row and its send state would otherwise keep `send()` locked forever,
            // because only a history page containing the turn can release them. Hand the text back
            // as a draft and say plainly that the server never confirmed it.
            if (bot?.id == botId && session?.id == sid) {
                val stuck = sendStates.entries.lastOrNull { it.value.belongsTo(botId, sid) }
                stuck?.let { entry ->
                    restoreDraft = entry.value.text
                    restoreAttachments = entry.value.attachments
                    sendStates.remove(entry.key)
                }
                pendingUserText = null
                pendingUserAttachments = emptyList()
                pendingUserTurnId = null
                // This supersedes the repeated fetch error: the user needs to know the message is
                // unconfirmed, not why the refresh failed.
                error = text(R.string.memoh_error_unconfirmed_reply)
            }
            reloadHistory(sid)
            return
        }
        val retryDelay = settlementRetryDelayMillis
        settlementRetryDelayMillis = (retryDelay * 2).coerceAtMost(30_000L)
        kotlinx.coroutines.delay(retryDelay)
        if (bot?.id == botId && session?.id == sid && historyRevision == revision &&
            runId == finishedRun && !running && settlingRunId == finishedRun) {
            settlingRunId = ""
            settleIfTerminal(sid)
        }
    }

    private fun clearRun() {
        liveJson = JSONArray()
        liveBlocks = emptyList()
        liveTurnId = ""
        liveUserTurns = emptyList()
        running = false
        runId = ""
        currentRunStatus = ""
        settlingRunId = ""
        settlementRetryDelayMillis = 1_000L
        settlementAttempts = 0
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
