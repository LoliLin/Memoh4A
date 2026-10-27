package com.newoether.agora.memoh

import com.newoether.agora.SettingsOverlayHost
import com.newoether.agora.model.Participant

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.newoether.agora.util.gradientBlur
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.ui.chat.ChatBottomScrollButton
import com.newoether.agora.ui.chat.ChatDrawerHost
import com.newoether.agora.ui.chat.ChatNavigationEffects
import com.newoether.agora.ui.chat.rememberAbsoluteBottomButtonVisible
import com.newoether.agora.ui.chat.rememberComposerSpacerAnimation
import com.newoether.agora.ui.components.clearFocusOnTap
import com.newoether.agora.ui.chat.ChatTopBar
import com.newoether.agora.ui.chat.MessageList
import com.newoether.agora.ui.chat.rememberChatScrollCoordinator
import com.newoether.agora.ui.chat.streamingTailAvailability
import com.newoether.agora.ui.chat.rememberConversationInteractionState
import com.newoether.agora.ui.chat.ConversationSearchMatch
import com.newoether.agora.ui.chat.conversationSearchMessageIds
import com.newoether.agora.ui.chat.scanConversationSearchMatches
import com.newoether.agora.ui.chat.bottombar.ChatComposerLayout
import com.newoether.agora.ui.chat.bottombar.ChatComposerSurface
import com.newoether.agora.ui.chat.bottombar.ComposerControlGroup
import com.newoether.agora.ui.chat.bottombar.ComposerModelMenuItem
import com.newoether.agora.ui.chat.bottombar.ComposerModelSelector
import com.newoether.agora.ui.chat.bottombar.ComposerSendButton
import com.newoether.agora.ui.chat.rememberChatDrawerState
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Memoh rendered inside Agora's own chat shell.
 *
 * The top bar, drawer gesture, message list and composer are the shared Agora
 * components; Memoh supplies the conversation titles, messages and every action.
 * Nothing here re-implements the visual language.
 */
@Composable
internal fun MemohConversationScreen(vm: MemohViewModel) {
    val uiResources = LocalContext.current.resources
    val api = remember { MemohApi(vm.workspaceApi) }
    val settings = vm.settings
    val blurEffectsEnabled by settings.blurEffectsEnabled.collectAsState()
    val toolCallDisplayMode by settings.toolCallDisplayMode.collectAsState()
    val thinkingSegmentDisplayMode by settings.thinkingSegmentDisplayMode.collectAsState()
    val autoExpandActiveGroup by settings.autoExpandActiveGroup.collectAsState()
    val parseInlineDollarMath by settings.parseInlineDollarMath.collectAsState()
    val autoWrapCodeBlocks by settings.autoWrapCodeBlocks.collectAsState()
    val stickToBottom by settings.stickToBottom.collectAsState()
    val drawer = rememberChatDrawerState()
    val motion = LocalAgoraMotionPolicy.current
    val scope = rememberCoroutineScope()
    val haptics = LocalAgoraHaptics.current
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val density = LocalDensity.current
    val currentSessionId = vm.session?.id
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val imeBottomPx = with(density) { imeBottom.roundToPx() }
    val scrollCoordinator = rememberChatScrollCoordinator(currentSessionId, imeBottomPx)
    scrollCoordinator.BindLayoutObservation(
        currentConversationId = currentSessionId,
        loadedMessagesConversationId = vm.loadedHistorySessionId,
        imeBottomPx = imeBottomPx,
        density = density,
    )
    val listState = scrollCoordinator.listState
    val messageHeights = scrollCoordinator.messageHeights
    val viewportHeightPx = scrollCoordinator.viewportHeightPx

    var botMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    var schedulesOpen by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var botSettingsOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var backupStatus by remember { mutableStateOf("") }
    var pendingBackupSections by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBackupPassphrase by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var sendQueueMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var selectedAttachments by remember(vm.attachmentDraftOwner) {
        mutableStateOf<List<MemohAttachment>>(emptyList())
    }
    var expanded by remember { mutableStateOf(false) }
    val composerSpacer = rememberComposerSpacerAnimation(
        isExpanded = expanded,
        allowSpatialTransitions = motion.allowSpatialTransitions,
        expandedHeightPx = with(density) { 44.dp.toPx() },
    )
    var barHeightPx by remember { mutableStateOf(0f) }
    // Shared-list state caches the MessageList writes back into (search geometry, thinking
    // expansion). Both reset per conversation like ChatApp does, keeping scroll anchors stable.
    val thoughtExpandedStates = remember(vm.session?.id) { androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>() }
    var mediaViewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var filePreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val field = remember(vm.bot?.id, vm.session?.id) { TextFieldState() }
    val focus = remember { FocusRequester() }
    val backupScope = rememberCoroutineScope()

    // A rejected send restores the draft instead of losing it.
    LaunchedEffect(vm.restoreDraft, vm.restoreAttachments) {
        vm.restoreDraft?.let { rejected ->
            if (rejected.isNotBlank()) field.edit {
                if (length > 0) append("\n")
                append(rejected)
            }
            selectedAttachments = (selectedAttachments + vm.restoreAttachments).distinct()
            vm.consumeRestoredDraft()
        }
    }

    // Stable list: history + optimistic pending row + a payload-free live stub. Runtime deltas
    // never touch this list; they only replace the streamingMessage overlay (matched by id).
    // Attachment files are pre-materialized off the main thread; composition only reads cache hits.
    var attachmentCacheRevision by remember { mutableStateOf(0) }
    val pendingAttachments = remember(vm.turns, vm.liveUserTurns, vm.pendingUserAttachments) {
        vm.pendingUserAttachments + (vm.turns + vm.liveUserTurns).flatMap { it.attachments }
    }
    LaunchedEffect(pendingAttachments) {
        val cacheDir = context.cacheDir
        val botId = vm.bot?.id
        val toDecode = pendingAttachments.filter { it.base64.isNotBlank() && it.cachedFile(cacheDir) == null }
        // History attachments reference container paths; fetch their bytes on demand.
        val toDownload = if (botId != null) {
            pendingAttachments.filter { it.needsDownload() && it.cachedFile(cacheDir) == null }
        } else emptyList()
        if (toDecode.isNotEmpty() || toDownload.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                toDecode.forEach { it.materialize(cacheDir) }
                toDownload.forEach { att ->
                    runCatching {
                        // A server row carries content_hash far more often than a container path,
                        // so resolve whichever source it actually has instead of assuming `path`.
                        vm.api.downloadAttachment(botId!!, att, att.cachedTarget(cacheDir))
                    }
                }
            }
            attachmentCacheRevision++
        }
    }
    val hasLive = vm.liveBlocks.isNotEmpty() || vm.running
    val messages = remember(
        vm.turns, vm.liveUserTurns, vm.pendingUserText, vm.pendingUserAttachments,
        vm.pendingUserTurnId, vm.liveTurnId, hasLive, attachmentCacheRevision,
    ) {
        memohTranscriptRows(
            turns = vm.turns,
            runtimeUsers = vm.liveUserTurns,
            pendingText = vm.pendingUserText,
            pendingAttachments = vm.pendingUserAttachments,
            pendingTurnId = vm.pendingUserTurnId,
            liveTurnId = vm.liveTurnId,
            hasLive = hasLive,
            cacheDir = context.cacheDir,
        )
    }
    // The live assistant run rides Agora's streaming channel: auto-follow, in-place growth,
    // and none of the whole-list rebuilds that made streaming text jitter.
    val streamingMessage = remember(vm.liveBlocks, vm.running) {
        if (vm.liveBlocks.isEmpty() && !vm.running) null
        else memohMessages(emptyList(), null, emptyList(), vm.liveBlocks, vm.running).firstOrNull()
    }
    val stable = remember(messages) { StableMessageList(messages) }
    val conversations = remember(vm.sessions, vm.session, vm.bots) {
        (vm.sessions + listOfNotNull(vm.session)).distinctBy { it.id }
            .map { ChatConversation(id = it.id, title = it.title) }
    }
    // Agora's search controls navigate the whole Memoh server history. The cache fetches older
    // pages on the IO dispatcher, keeps them per session, and streams match counts as pages arrive.
    val messagesState = remember { mutableStateOf(messages) }
    messagesState.value = messages
    val searchMessagesById = remember(messages) { messages.associateBy { it.id } }
    val searchMessageLoader: suspend (String, List<String>) -> List<com.newoether.agora.model.ChatMessage> =
        remember(searchMessagesById) {
            { _, ids -> ids.mapNotNull(searchMessagesById::get) }
        }
    val searchBotId = vm.bot?.id
    val searchSessionId = currentSessionId
    val searchHistoryTailId = vm.turns.lastOrNull()?.id
    val searchHistoryCache = remember(searchBotId, searchSessionId, searchHistoryTailId) {
        MemohConversationSearchCache()
    }
    val searchAllMessages: ((String) -> Flow<List<ConversationSearchMatch>>)? = remember(
        api, searchBotId, searchSessionId, searchHistoryCache,
    ) {
        val botId = searchBotId
        val sessionId = searchSessionId
        if (botId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            null
        } else {
            { query: String ->
                flow {
                    if (query.isBlank()) {
                        emit(emptyList())
                        return@flow
                    }
                    delay(180L)
                    try {
                        searchHistoryCache.collectMatches(api, botId, sessionId, query) { matches ->
                            emit(matches)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep any results already found; a server paging failure must not crash chat.
                    }
                }
            }
        }
    }
    scrollCoordinator.BindImeEffects(
        currentConversationId = currentSessionId,
        messages = messagesState,
        density = density,
        bottomBarHeight = with(density) { barHeightPx.toDp() },
        shareSelectionBarSpace = 0.dp,
        imeBottomPx = imeBottomPx,
    )
    val conversationInteraction = rememberConversationInteractionState(
        currentConversationId = currentSessionId,
        messages = messagesState,
        listState = listState,
        searchMessages = searchMessageLoader,
        searchAllMessages = searchAllMessages,
    )
    val pendingSearchScrollMatchKey = remember(currentSessionId) { mutableStateOf<String?>(null) }
    val selectedSearchMatch = conversationInteraction.searchMatches
        .getOrNull(conversationInteraction.searchMatchIndex)
    LaunchedEffect(
        currentSessionId,
        selectedSearchMatch?.key,
        messages,
        vm.olderMessagesAvailable,
        vm.loadingOlderMessages,
    ) {
        val match = selectedSearchMatch ?: run {
            pendingSearchScrollMatchKey.value = null
            return@LaunchedEffect
        }
        if (messages.any { it.id == match.messageId }) {
            if (pendingSearchScrollMatchKey.value == match.key) {
                pendingSearchScrollMatchKey.value = null
                conversationInteraction.retrySearchScroll()
            } else if (pendingSearchScrollMatchKey.value != null) {
                pendingSearchScrollMatchKey.value = null
            }
        } else {
            pendingSearchScrollMatchKey.value = match.key
            if (vm.olderMessagesAvailable && !vm.loadingOlderMessages) vm.loadOlderMessages()
        }
    }
    val streamingFollowAvailability = streamingTailAvailability(
        generationActive = vm.running,
        blocked = conversationInteraction.searchActive || !motion.allowProgrammaticScrollMotion,
        programmaticHandoff = scrollCoordinator.imeBottomAnchorState.active,
    )

    MemohChatScrollEffects(
        coordinator = scrollCoordinator,
        sessionId = currentSessionId,
        loadedHistorySessionId = vm.loadedHistorySessionId,
        messages = messagesState,
        pendingUserText = vm.pendingUserText,
        running = vm.running,
        searchActive = conversationInteraction.searchActive,
        stickToBottom = stickToBottom,
        density = density,
        bottomBarHeight = with(density) { barHeightPx.toDp() },
        motion = motion,
    )
    // Match Agora's back ordering: close search/drawer before leaving the chat. Expanded input
    // also consumes Back so a large draft can return to the transcript without leaving the app.
    BackHandler(enabled = expanded && !drawer.shouldHandleBack && !conversationInteraction.searchActive) {
        expanded = false
    }
    ChatNavigationEffects(
        drawerState = drawer,
        focusManager = focusManager,
        scope = scope,
        motionPolicy = motion,
        onNavigateBack = null,
        conversationInteraction = conversationInteraction,
        onCollapseComposer = { expanded = false },
    )
    val showBottomButton by rememberAbsoluteBottomButtonVisible(
        conversationId = currentSessionId,
        loadedMessagesConversationId = vm.loadedHistorySessionId,
        isNewChatMode = currentSessionId == null,
        isSwitching = currentSessionId != null && vm.loadedHistorySessionId != currentSessionId,
        shareSelectionActive = false,
        isNearAbsoluteBottom = scrollCoordinator.isNearAbsoluteBottom,
        absoluteBottomScrollPhase = scrollCoordinator.absoluteBottomScrollPhase,
        listState = listState,
        streamingTailController = scrollCoordinator.streamingTailController,
        regenerationScrollActive = conversationInteraction.searchActive,
        imeBottomAnchorActive = scrollCoordinator.imeBottomAnchorState.active,
    )

    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val selectedBot = vm.bot
        if (uri != null && selectedBot != null) {
            backupScope.launch {
                backupStatus = uiResources.getString(R.string.memoh_ui_saving_backup)
                runCatching {
                    api.exportBotBackup(
                        selectedBot.id, uri, context.contentResolver,
                        pendingBackupSections, pendingBackupPassphrase,
                    )
                }.onSuccess { backupStatus = uiResources.getString(R.string.memoh_ui_backup_saved) }
                    .onFailure { backupStatus = uiResources.getString(R.string.memoh_ui_export_failed_1_s, it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)) }
                    .also { pendingBackupSections = emptyList(); pendingBackupPassphrase = "" }
            }
        }
    }
    val openBackupDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }
    val voiceInput = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognized.isNotEmpty()) {
                field.edit {
                    if (length > 0) append("\n")
                    append(recognized)
                }
                focus.requestFocus()
            }
        }
    }

    ChatDrawerHost(
        state = drawer,
        drawerEnabled = true,
        motionPolicy = motion,
        onDrawerProgress = {},
        drawerContent = { drawerWidth, closeFromContent ->
            MemohDrawerContent(
                vm = vm,
                drawerWidth = drawerWidth,
                selectedId = vm.session?.id,
                onNewChat = {
                    vm.newChat()
                    scope.launch { closeFromContent() }
                },
                onSelect = { session ->
                    if (session.id != vm.session?.id) vm.selectSession(session)
                    scope.launch { closeFromContent() }
                },
                onLoadOlder = vm::loadOlderSessions,
                onRename = { session ->
                    newTitle = session.title
                    renameDialog = true
                },
                onDelete = { session ->
                    vm.selectSession(session)
                    deleteDialog = true
                },
                onOpenSettings = {
                    settingsOpen = true
                    scope.launch { closeFromContent() }
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().clearFocusOnTap()) {
            // Agora's ambient background: glowing blobs on dark, flat on light.
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val (targetCa, targetQa) = if (!dark) 0.00f to 0.00f
            else if (vm.session == null) 0.20f to 0.10f else 0.02f to 0.01f
            val ca by androidx.compose.animation.core.animateFloatAsState(targetCa, androidx.compose.animation.core.tween(800))
            val qa by androidx.compose.animation.core.animateFloatAsState(targetQa, androidx.compose.animation.core.tween(800))
            com.newoether.agora.ui.components.AnimatedBlobBackground(
                centerAlpha = ca,
                quarterAlpha = qa,
                blurRadius = 40f,
                dark = dark,
                blurEnabled = blurEffectsEnabled,
                motionEnabled = motion.allowContinuousMotion,
            )
            androidx.compose.material3.Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        ChatTopBar(
                    isNewChatMode = vm.session == null,
                    conversations = conversations,
                    currentConversationId = vm.session?.id,
                    currentConversationTitle = vm.session?.title,
                    totalTokens = 0,
                    contextTokenBudget = 0,
                    contextAvailable = false,
                    subtitle = vm.connection.error.ifBlank {
                        (vm.bot?.title ?: "Memoh") + " · " + when (vm.connection.state) {
                            MemohConnectionState.Connected -> uiResources.getString(R.string.memoh_ui_connected)
                            MemohConnectionState.Connecting -> uiResources.getString(R.string.memoh_ui_connecting)
                            MemohConnectionState.Retrying -> uiResources.getString(R.string.memoh_ui_reconnecting)
                            MemohConnectionState.Blocked -> uiResources.getString(R.string.memoh_ui_connection_failed)
                            MemohConnectionState.Idle -> uiResources.getString(R.string.memoh_ui_disconnected)
                        }
                    },
                    searchActive = conversationInteraction.searchActive,
                    searchQuery = conversationInteraction.searchQuery,
                    searchMatchIndex = conversationInteraction.searchMatchIndex,
                    searchMatchCount = conversationInteraction.searchMatches.size,
                    onSearchQueryChange = conversationInteraction::updateSearchQuery,
                    onSearchPrevious = { if (conversationInteraction.previousSearchMatch()) haptics.selection() },
                    onSearchNext = { if (conversationInteraction.nextSearchMatch()) haptics.selection() },
                    onSearchDismiss = {
                        conversationInteraction.dismissSearch()
                        focusManager.clearFocus()
                    },
                    onOpenDrawer = { scope.launch { drawer.toggle(motion) } },
                    systemPromptEnabled = false,
                    onSystemPromptClick = {},
                    moreMenuContent = { dismiss -> MemohAccountMenu(vm, dismiss) { action ->
                        accountMenu = false
                        when (action) {
                            MemohAccountAction.Search -> {
                                focusManager.clearFocus()
                                conversationInteraction.activateSearch()
                            }
                            MemohAccountAction.Files -> filesOpen = true
                            MemohAccountAction.Schedules -> schedulesOpen = true
                            MemohAccountAction.Terminal -> terminalOpen = true
                            MemohAccountAction.ManageBots -> settingsOpen = true
                        }
                    } },
                    onNewChat = { haptics.selection(); vm.newChat() },
                    newChatEnabled = vm.bot != null,
                        )
                    }
                },
            ) { _ ->
                Box(
                    Modifier.fillMaxSize().onSizeChanged { scrollCoordinator.recordViewportHeight(it.height) },
                ) {
                    when {
                        vm.bot == null -> MemohNotice(uiResources.getString(R.string.memoh_ui_no_accessible_bots_yet))
                        // New-chat welcome: Agora's typewriter headline vertically centered
                        // against the composer, not a static notice block.
                        messages.isEmpty() -> MemohWelcome(
                            bottomBarHeight = with(LocalDensity.current) { barHeightPx.toDp() },
                            windowHeightDp = viewportHeightPx.let { h ->
                                if (h == 0) 0f else with(LocalDensity.current) { h.toDp().value }
                            },
                        )
                        else -> MessageList(
                            messages = stable,
                            authoritativeMessages = stable,
                            allMessages = stable,
                            conversationId = vm.session?.id,
                            state = listState,
                            messageActionsEnabled = !vm.running,
                            readOnlyActions = vm.running,
                            isLoading = vm.running,
                            isCompacting = vm.runtimeControlsBusy,
                            deleteActionsEnabled = false,
                            // Memoh history is a single linear transcript and carries no per-row
                            // model/token metadata, so both affordances would be dead ends.
                            branchSwitchingEnabled = false,
                            messageInfoEnabled = false,
                            toolCallDisplayMode = toolCallDisplayMode,
                            thinkingSegmentDisplayMode = thinkingSegmentDisplayMode,
                            autoExpandActiveGroup = autoExpandActiveGroup,
                            parseInlineDollarMath = parseInlineDollarMath,
                            autoWrapCodeBlocks = autoWrapCodeBlocks,
                            streamingMessage = streamingMessage,
                            streamingAutoFollowEnabled = streamingFollowAvailability.enabled && stickToBottom,
                            streamingAutoFollowPaused = streamingFollowAvailability.paused,
                            streamingTailWithinAttachThreshold = scrollCoordinator.isWithinAbsoluteBottomAttachThreshold,
                            streamingTailController = scrollCoordinator.streamingTailController,
                            lifecycleAppearanceRegistry = scrollCoordinator.messageLifecycleAppearanceRegistry,
                            bottomBarHeight = with(LocalDensity.current) { barHeightPx.toDp() },
                            viewportHeight = viewportHeightPx,
                            messageHeights = messageHeights,
                            thoughtExpandedStates = thoughtExpandedStates,
                            onSearchMatchDistance = conversationInteraction::recordSearchMatchDistance,
                            onSearchTurnsChanged = conversationInteraction::recordSearchTurns,
                            searchQuery = if (conversationInteraction.searchActive) {
                                conversationInteraction.searchQuery
                            } else {
                                ""
                            },
                            activeSearchMatch = conversationInteraction.searchMatches
                                .getOrNull(conversationInteraction.searchMatchIndex),
                            searchScrollRequestKey = conversationInteraction.searchScrollRequestKey,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxHeight()
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                                .gradientBlur(
                                    blurAtTopDp = if (blurEffectsEnabled) 8f else 0f,
                                    blurAtBottomDp = 0f,
                                    fadeHeightDp = 40f,
                                    bottomOverlayHeight = with(LocalDensity.current) { barHeightPx.toDp() } + 12.dp,
                                ),
                            onEditMessage = { messageId, newText ->
                                vm.turns.firstOrNull { memohMessageId(it, 0) == messageId || it.turnId == messageId }
                                    ?.takeIf { it.role == "user" && it.turnId.isNotBlank() }
                                    ?.let { vm.mutateTurn(it.turnId, retry = false, text = newText) } == true
                            },
                            onRegenerate = { messageId ->
                                vm.turns.firstOrNull { memohMessageId(it, 0) == messageId || it.turnId == messageId }
                                    ?.takeIf { it.turnId.isNotBlank() }
                                    ?.let { vm.mutateTurn(it.turnId, retry = true) } == true
                            },
                            onFork = { messageId ->
                                vm.turns.firstOrNull { memohMessageId(it, 0) == messageId || it.turnId == messageId }
                                    ?.takeIf { it.turnId.isNotBlank() }
                                    ?.let { vm.forkFromTurn(it.turnId) }
                            },
                            onShare = { messageId ->
                                val shared = messages.firstOrNull { it.id == messageId }?.let { m ->
                                    listOfNotNull(
                                        m.segments?.filter { it.type == "answer" }
                                            ?.joinToString("\n") { it.content },
                                        m.text,
                                    ).firstOrNull { it.isNotBlank() } ?: m.text
                                } ?: return@MessageList
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, shared)
                                })
                            },
                            onRecompact = { _ -> vm.compactSession() },
                            onMediaClick = { urls, index ->
                                mediaViewer = urls to index
                            },
                            onFileContentClick = { name, content ->
                                filePreview = name to content
                            },
                            messageFooter = { message ->
                                MemohInteractiveBlocksFooter(vm, message)
                            },
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 140.dp,
                                bottom = with(LocalDensity.current) { barHeightPx.toDp() } + 8.dp,
                            ),
                        )
                    }
                    if (vm.olderMessagesAvailable || vm.loadingOlderMessages) {
                        TextButton(
                            onClick = vm::loadOlderMessages,
                            enabled = !vm.loadingOlderMessages,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        ) {
                            Text(if (vm.loadingOlderMessages) uiResources.getString(R.string.memoh_ui_loading) else uiResources.getString(R.string.memoh_ui_load_earlier_messages))
                        }
                    }
                    ChatBottomScrollButton(showBottomButton && !expanded, with(density) { barHeightPx.toDp() }) {
                        scrollCoordinator.requestAbsoluteBottomScroll()
                    }
                }
            }

            ChatComposerSurface(
                contentMaxWidth = 840.dp,
                isExpanded = expanded,
                outerSpacerHeightPx = composerSpacer.outerHeightPx,
                onBarHeightChanged = { barHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChatComposerLayout(
                    textFieldState = field,
                    focusRequester = focus,
                    onInputFocusChanged = scrollCoordinator::setComposerInputFocused,
                    isExpanded = expanded,
                    isExpandAnimating = composerSpacer.isRunning,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                    placeholder = uiResources.getString(R.string.memoh_ui_ask_1_s, vm.bot?.title ?: "Memoh"),
                    statusContent = {
                        MemohLiveInteractions(vm)
                        if (vm.error.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.error, color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f).padding(12.dp), maxLines = 3)
                                TextButton(onClick = vm::dismissError) { Text(uiResources.getString(R.string.memoh_ui_dismiss)) }
                            }
                        }
                    },
                    attachmentContent = {
                        if (selectedAttachments.isNotEmpty()) {
                            AttachmentDraftStrip(
                                attachments = selectedAttachments,
                                onRemove = { selectedAttachments = selectedAttachments - it },
                            )
                        }
                    },
                    controls = {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ComposerControlGroup {
                                AttachmentPickerButton(
                                    enabled = vm.bot != null,
                                    currentOwner = { vm.attachmentDraftOwner },
                                    currentAttachments = { selectedAttachments },
                                    onAttachmentsPicked = { selectedAttachments = selectedAttachments + it },
                                    onAttachmentError = { vm.reportAttachmentError(it) },
                                )
                                androidx.compose.material3.IconButton(
                                    enabled = vm.bot != null,
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                            )
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE,
                                                java.util.Locale.getDefault().toLanguageTag(),
                                            )
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, uiResources.getString(R.string.memoh_ui_speak_your_message))
                                        }
                                        runCatching { voiceInput.launch(intent) }
                                            .onFailure { vm.error = uiResources.getString(R.string.memoh_ui_speech_recognition_is_unavailable_on_this_device) }
                                    },
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = uiResources.getString(R.string.memoh_ui_voice_input))
                                }
                                if (vm.running && vm.runId.isNotBlank()) {
                                    Box {
                                        TextButton(onClick = { sendQueueMenu = true },
                                            enabled = field.text.isNotBlank() && !vm.queueBusy) {
                                            Text(uiResources.getString(R.string.memoh_ui_queue))
                                        }
                                        AgoraDropdownMenu(
                                            expanded = sendQueueMenu,
                                            onDismissRequest = { sendQueueMenu = false },
                                        ) {
                                            AgoraDropdownMenuItem(
                                                text = { Text(uiResources.getString(R.string.memoh_ui_add_follow_up)) },
                                                onClick = {
                                                    if (vm.queueMessage(field.text.toString(), "follow_up")) {
                                                        field.clearText()
                                                    }
                                                    sendQueueMenu = false
                                                },
                                            )
                                            AgoraDropdownMenuItem(
                                                text = { Text(uiResources.getString(R.string.memoh_ui_steer_current_run)) },
                                                enabled = vm.queueState?.steerSupported == true,
                                                onClick = {
                                                    if (vm.queueMessage(field.text.toString(), "steer")) {
                                                        field.clearText()
                                                    }
                                                    sendQueueMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                // Agora's own capsule selector: width-fading label, real dropdown.
                                val controller = vm.agentController
                                val catalog = controller.models
                                val modelArray = catalog?.optJSONArray("models")
                                val currentId = controller.currentModelId
                                val displayName = controller.currentModelLabel
                                ComposerModelSelector(
                                    displayText = when {
                                        controller.modelsLoading && displayName.isBlank() -> "…"
                                        displayName.isNotBlank() -> displayName
                                        else -> uiResources.getString(R.string.memoh_ui_model_unavailable)
                                    },
                                    isModelValid = currentId.isNotBlank(),
                                    expanded = modelMenu,
                                    onClick = { modelMenu = !modelMenu },
                                    onDismissRequest = { modelMenu = false },
                                    enabled = vm.bot != null,
                                ) {
                                    if (modelArray == null || modelArray.length() == 0) {
                                        AgoraDropdownMenuItem(
                                            text = { Text(if (controller.modelsLoading) uiResources.getString(R.string.memoh_ui_loading_models) else uiResources.getString(R.string.memoh_ui_no_models_available)) },
                                            enabled = false,
                                            onClick = {},
                                        )
                                    } else {
                                        // No "follow the bot default" row: the bot's own default is
                                        // already shown as the selected model, so a blank-selection
                                        // entry would be a second, ambiguous way to say the same thing.
                                        // Group by provider like Homem's model list: the provider
                                        // name heads each section, models sorted inside.
                                        val entries = (0 until modelArray.length())
                                            .mapNotNull { modelArray.optJSONObject(it) }
                                        val grouped = entries
                                            .groupBy { it.optString("provider_name").ifBlank { it.optString("provider_id") } }
                                            .toSortedMap(compareBy(String::isBlank, { it.lowercase() }))
                                        grouped.forEach { (provider, models) ->
                                            if (provider.isNotBlank()) {
                                                AgoraDropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            provider,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    },
                                                    enabled = false,
                                                    onClick = {},
                                                )
                                            }
                                            models.sortedBy { it.optString("name").lowercase() }.forEach { model ->
                                                val id = model.optString("id")
                                                val label = model.optString("name").ifBlank { model.optString("model_id") }
                                                ComposerModelMenuItem(
                                                    displayText = label,
                                                    selected = id == currentId,
                                                    onClick = {
                                                        // Pick the new model's own default tier,
                                                        // then reconcile below against the catalog.
                                                        vm.selectModel(
                                                            id, model.reasoningDefault())
                                                        modelMenu = false
                                                    },
                                                )
                                            }
                                        }
                                        MemohReasoningEffortSelector(controller, currentId) { effort, apply ->
                                            vm.selectModel(apply, effort)
                                            modelMenu = false
                                        }
                                    }
                                }
                            }
                            val showStop = vm.running && vm.runId.isNotBlank()
                            ComposerSendButton(
                                isActionable = vm.bot != null &&
                                    (showStop || field.text.isNotBlank() || selectedAttachments.isNotEmpty()),
                                isBusy = false,
                                showStop = showStop,
                                onClick = {
                                    if (showStop) {
                                        haptics.interrupt(); vm.abort()
                                    } else if (field.text.isNotBlank() || selectedAttachments.isNotEmpty()) {
                                        if (vm.send(field.text.toString(), selectedAttachments)) {
                                            field.clearText()
                                            selectedAttachments = emptyList()
                                            expanded = false
                                            conversationInteraction.dismissSearch()
                                        } else {
                                            // A refused send keeps the draft; the refusal must be felt
                                            // instead of leaving the tap looking ignored.
                                            haptics.reject()
                                        }
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    // Agora's own full-screen media pager, wired to the shared thumbnails' click channel.
    mediaViewer?.let { (urls, index) ->
        com.newoether.agora.ui.chat.FullScreenMediaViewer(
            urls = urls,
            initialIndex = index,
            pdfPages = emptyList(),
            onClose = { mediaViewer = null },
            onNavigate = { mediaViewer = urls to it },
        )
    }
    filePreview?.let { (name, content) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { filePreview = null },
            title = { Text(name) },
            text = { Text(content, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { filePreview = null }) { Text(uiResources.getString(R.string.memoh_ui_close)) }
            },
        )
    }
    SettingsOverlayHost(
        visible = settingsOpen,
        onDismiss = { settingsOpen = false },
    ) {
        MemohSettingsHost(
            viewModel = vm,
            onDismiss = { settingsOpen = false },
            // Bot settings is its own full-screen destination, opened from the Bot row.
            onOpenBotSettings = { settingsOpen = false; backupStatus = ""; botSettingsOpen = true },
        )
    }

    // Bot settings rides the same overlay host as the app's settings so its collapsing title,
    // page transitions and back handling behave identically.
    vm.bot?.let { selectedBot ->
        SettingsOverlayHost(
            visible = botSettingsOpen,
            onDismiss = { botSettingsOpen = false },
        ) {
            MemohBotSettingsScreen(
                api = api,
                bot = selectedBot,
                agentController = vm.agentController,
                onBack = { botSettingsOpen = false },
                onBotUpdated = vm::updateBotMetadata,
                onBotDeleted = { vm.refreshBots(); botSettingsOpen = false },
                onOpenWorkspace = { filesOpen = true; botSettingsOpen = false },
                onBackupExport = { sections, passphrase ->
                    pendingBackupSections = sections
                    pendingBackupPassphrase = passphrase
                    createBackupDocument.launch(
                        "${selectedBot.title.replace(Regex("[^A-Za-z0-9._-]"), "_")}-backup.zip",
                    )
                },
                onBackupImport = {
                    openBackupDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                backupStatus = backupStatus,
            )
        }
    }

    MemohDialogs(
        vm = vm, api = api, context = context,
        renameDialog = renameDialog, newTitle = newTitle,
        deleteDialog = deleteDialog,
        botSettingsOpen = botSettingsOpen,
        filesOpen = filesOpen, schedulesOpen = schedulesOpen,
        importUri = importUri, backupStatus = backupStatus,
        onRenameDismiss = { renameDialog = false },
        onDeleteDismiss = { deleteDialog = false },
        onBotSettingsDismiss = { botSettingsOpen = false },
        onFilesDismiss = { filesOpen = false },
        onSchedulesDismiss = { schedulesOpen = false },
        onImportConsumed = { importUri = null },
        onBackupExport = { sections, passphrase ->
            pendingBackupSections = sections
            pendingBackupPassphrase = passphrase
            createBackupDocument.launch(
                "${vm.bot?.title?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "memoh"}-backup.zip",
            )
        },
        onBackupImport = {
            openBackupDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onOpenWorkspaceFromSettings = { filesOpen = true; botSettingsOpen = false },
    )

    vm.bot?.takeIf { terminalOpen }?.let { selectedBot ->
        MemohTerminalDialog(api = api, botId = selectedBot.id, onDismiss = { terminalOpen = false })
    }
}

@Composable
private fun MemohNotice(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * Agora's ChatWelcomeContent equivalent: a typewriter headline vertically centered in the
 * space between the top bar and the composer, over the ambient blob background.
 */
@Composable
private fun MemohWelcome(bottomBarHeight: androidx.compose.ui.unit.Dp, windowHeightDp: Float) {
    val uiResources = LocalContext.current.resources
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val availableHeight = windowHeightDp - bottomBarHeight.value
            val topPadding = (availableHeight / 2f).coerceAtLeast(0f).dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = topPadding).padding(horizontal = 24.dp),
            ) {
                com.newoether.agora.ui.components.TypewriterText(
                    text = uiResources.getString(R.string.memoh_ui_welcome_to_memoh),
                    animationKey = "memoh-welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    typeSpeedMs = 100,
                    animate = true,
                    mode = com.newoether.agora.ui.components.TypewriterMode.TEXT_GRADIENT,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    uiResources.getString(R.string.memoh_ui_start_a_conversation_your_messages_stay_on_the_memoh_server),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Incrementally caches one session's server pages so later queries stay local and responsive. */
private class MemohConversationSearchCache {
    private val mutex = Mutex()
    // API pages arrive newest first; each individual page is already in transcript order.
    private val pages = mutableListOf<List<com.newoether.agora.model.ChatMessage>>()
    private var beforeMessageId = ""
    private var complete = false

    suspend fun collectMatches(
        api: MemohApi,
        botId: String,
        sessionId: String,
        query: String,
        publish: suspend (List<ConversationSearchMatch>) -> Unit,
    ) {
        if (query.isBlank()) {
            publish(emptyList())
            return
        }
        mutex.withLock {
            var matches = emptyList<ConversationSearchMatch>()
            pages.asReversed().forEach { page ->
                currentCoroutineContext().ensureActive()
                matches = findPageMatches(page, query) + matches
                publish(matches)
            }
            while (!complete) {
                currentCoroutineContext().ensureActive()
                val page = withContext(Dispatchers.IO) {
                    api.history(botId, sessionId, beforeMessageId)
                }
                if (page.isEmpty()) {
                    complete = true
                    break
                }
                val cursor = page.firstOrNull()?.id.orEmpty()
                if (beforeMessageId.isNotBlank() && cursor == beforeMessageId) {
                    complete = true
                    break
                }
                val (searchablePage, pageMatches) = projectPage(page, query)
                pages += searchablePage
                beforeMessageId = cursor
                if (page.size < HISTORY_PAGE_SIZE || cursor.isBlank()) complete = true
                matches = pageMatches + matches
                publish(matches)
            }
        }
    }

    private suspend fun projectPage(
        turns: List<MemohTurn>,
        query: String,
    ): Pair<List<com.newoether.agora.model.ChatMessage>, List<ConversationSearchMatch>> =
        withContext(Dispatchers.Default) {
            val messages = memohMessages(
                turns = turns,
                pendingText = null,
                pendingAttachments = emptyList(),
                liveBlocks = emptyList(),
                running = false,
            ).map { message ->
                com.newoether.agora.model.ChatMessage(
                    id = message.id,
                    text = message.text,
                    participant = message.participant,
                )
            }
            val messagesById = messages.associateBy { it.id }
            val matches = scanConversationSearchMatches(conversationSearchMessageIds(messages), query) { ids ->
                ids.mapNotNull(messagesById::get)
            }
            messages to matches
        }

    private suspend fun findPageMatches(
        messages: List<com.newoether.agora.model.ChatMessage>,
        query: String,
    ): List<ConversationSearchMatch> = withContext(Dispatchers.Default) {
        val messagesById = messages.associateBy { it.id }
        scanConversationSearchMatches(conversationSearchMessageIds(messages), query) { ids ->
            ids.mapNotNull(messagesById::get)
        }
    }
}
