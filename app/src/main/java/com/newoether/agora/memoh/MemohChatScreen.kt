package com.newoether.agora.memoh

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.newoether.agora.util.gradientBlur
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.ui.chat.ChatDrawerHost
import com.newoether.agora.ui.chat.ChatTopBar
import com.newoether.agora.ui.chat.MessageList
import com.newoether.agora.ui.chat.rememberChatScrollCoordinator
import com.newoether.agora.ui.chat.streamingTailAvailability
import com.newoether.agora.ui.chat.rememberConversationInteractionState
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

/**
 * Memoh rendered inside Agora's own chat shell.
 *
 * The top bar, drawer gesture, message list and composer are the shared Agora
 * components; Memoh supplies the conversation titles, messages and every action.
 * Nothing here re-implements the visual language.
 */
@Composable
internal fun MemohConversationScreen(vm: MemohViewModel) {
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
    var memoriesOpen by remember { mutableStateOf(false) }
    var schedulesOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var runtimeControlsOpen by remember { mutableStateOf(false) }
    var compactConfirm by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var botDetailsOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var backupStatus by remember { mutableStateOf("") }
    var pendingBackupSections by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBackupPassphrase by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var sendQueueMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    // Team-level catalog: load once per signed-in scope, no Agent selection required.
    LaunchedEffect(vm.signedIn, vm.bot?.id) {
        if (vm.signedIn && vm.bot != null) vm.agentController.loadModels("", "")
    }
    var selectedAttachments by remember(vm.attachmentDraftOwner) {
        mutableStateOf<List<MemohAttachment>>(emptyList())
    }
    var expanded by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableStateOf(0f) }
    // Shared-list state caches the MessageList writes back into (search geometry, thinking
    // expansion). Both reset per conversation like ChatApp does, keeping scroll anchors stable.
    val thoughtExpandedStates = remember(vm.session?.id) { androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>() }
    var mediaViewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var filePreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val field = remember(vm.bot?.id, vm.session?.id) { TextFieldState() }
    val focus = remember { FocusRequester() }
    var autoScrolledSessionId by remember { mutableStateOf<String?>(null) }
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
    val pendingAttachments = vm.pendingUserAttachments + vm.turns.flatMap { it.attachments }
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
                        vm.api.downloadAttachmentBytes(botId!!, att.path, att.cachedTarget(cacheDir))
                    }
                }
            }
            attachmentCacheRevision++
        }
    }
    val messages = remember(vm.turns, vm.pendingUserText, vm.pendingUserAttachments, vm.running, attachmentCacheRevision) {
        memohMessages(
            turns = vm.turns,
            pendingText = vm.pendingUserText,
            pendingAttachments = vm.pendingUserAttachments,
            liveBlocks = emptyList(),
            running = false,
            cacheDir = context.cacheDir,
        ) + if (vm.liveBlocks.isNotEmpty() || vm.running) listOf(memohLiveStub()) else emptyList()
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
    // Agora's own conversation search: matches within the visible transcript, shared
    // top bar search UI, scroll-to-match. Memoh history is the message source.
    val messagesState = remember { mutableStateOf(messages) }
    messagesState.value = messages
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
    )
    val streamingFollowAvailability = streamingTailAvailability(
        generationActive = vm.running,
        blocked = conversationInteraction.searchActive || !motion.allowProgrammaticScrollMotion,
        programmaticHandoff = scrollCoordinator.imeBottomAnchorState.active,
    )

    LaunchedEffect(vm.session?.id, messages.lastOrNull()?.id, stickToBottom) {
        if (messages.isEmpty()) return@LaunchedEffect
        val sessionId = vm.session?.id ?: return@LaunchedEffect
        if (autoScrolledSessionId != sessionId || stickToBottom) {
            if (motion.allowProgrammaticScrollMotion) {
                listState.animateScrollToItem(messages.lastIndex)
            } else {
                listState.scrollToItem(messages.lastIndex)
            }
            autoScrolledSessionId = sessionId
        }
    }

    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val selectedBot = vm.bot
        if (uri != null && selectedBot != null) {
            backupScope.launch {
                backupStatus = "Saving backup…"
                runCatching {
                    api.exportBotBackup(
                        selectedBot.id, uri, context.contentResolver,
                        pendingBackupSections, pendingBackupPassphrase,
                    )
                }.onSuccess { backupStatus = "Backup saved." }
                    .onFailure { backupStatus = "Export failed: ${it.message ?: "Unknown error"}" }
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
        drawerContent = { _, closeFromContent ->
            MemohDrawerContent(
                vm = vm,
                selectedId = vm.session?.id,
                onNewChat = {
                    vm.newChat()
                    scope.launch { closeFromContent() }
                },
                onSelect = { session ->
                    vm.selectSession(session)
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
                onOpenSettings = { settingsOpen = true },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
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
                            MemohConnectionState.Connected -> "Connected"
                            MemohConnectionState.Connecting -> "Connecting…"
                            MemohConnectionState.Retrying -> "Reconnecting…"
                            MemohConnectionState.Blocked -> "Connection failed"
                            MemohConnectionState.Idle -> "Disconnected"
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
                            MemohAccountAction.BotDetails -> botDetailsOpen = true
                            MemohAccountAction.Backup -> { backupStatus = ""; backupOpen = true }
                            MemohAccountAction.Files -> filesOpen = true
                            MemohAccountAction.Memory -> memoriesOpen = true
                            MemohAccountAction.Schedules -> schedulesOpen = true
                            MemohAccountAction.Terminal -> terminalOpen = true
                            MemohAccountAction.Queue -> { queueOpen = true; vm.refreshQueue() }
                            MemohAccountAction.RuntimeControls -> runtimeControlsOpen = true
                            MemohAccountAction.Compact -> compactConfirm = true
                            MemohAccountAction.Rename -> {
                                newTitle = vm.session?.title.orEmpty(); renameDialog = true
                            }
                            MemohAccountAction.Delete -> deleteDialog = true
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
                        vm.bot == null -> MemohNotice("No accessible bots yet")
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
                            Text(if (vm.loadingOlderMessages) "Loading…" else "Load earlier messages")
                        }
                    }
                }
            }

            ChatComposerSurface(
                contentMaxWidth = 840.dp,
                isExpanded = expanded,
                onBarHeightChanged = { barHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChatComposerLayout(
                    textFieldState = field,
                    focusRequester = focus,
                    onInputFocusChanged = scrollCoordinator::setComposerInputFocused,
                    isExpanded = expanded,
                    isExpandAnimating = false,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                    placeholder = "向 ${vm.bot?.title ?: "Memoh"} 提问...",
                    statusContent = {
                        if (vm.error.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.error, color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f).padding(12.dp), maxLines = 3)
                                TextButton(onClick = vm::dismissError) { Text("Dismiss") }
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
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                                        }
                                        runCatching { voiceInput.launch(intent) }
                                            .onFailure { vm.error = "Speech recognition is unavailable on this device." }
                                    },
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                                }
                                if (vm.running && vm.runId.isNotBlank()) {
                                    Box {
                                        TextButton(onClick = { sendQueueMenu = true },
                                            enabled = field.text.isNotBlank() && !vm.queueBusy) {
                                            Text("Queue")
                                        }
                                        AgoraDropdownMenu(
                                            expanded = sendQueueMenu,
                                            onDismissRequest = { sendQueueMenu = false },
                                        ) {
                                            AgoraDropdownMenuItem(
                                                text = { Text("Add Follow-Up") },
                                                onClick = {
                                                    if (vm.queueMessage(field.text.toString(), "follow_up")) {
                                                        field.clearText()
                                                    }
                                                    sendQueueMenu = false
                                                },
                                            )
                                            AgoraDropdownMenuItem(
                                                text = { Text("Steer Current Run") },
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
                                val currentId = controller.preferredModelId.ifBlank { controller.settledModelId }
                                val displayName = modelArray?.let { models ->
                                    (0 until models.length())
                                        .mapNotNull { models.optJSONObject(it) }
                                        .firstOrNull { it.optString("id") == currentId }
                                        ?.optString("name").orEmpty()
                                }.orEmpty()
                                ComposerModelSelector(
                                    displayText = when {
                                        controller.modelsLoading -> "…"
                                        displayName.isNotBlank() -> displayName
                                        // An id the catalog does not know is a server default:
                                        // never surface a raw hash as the chip label.
                                        modelArray == null || modelArray.length() == 0 -> "模型"
                                        else -> "选择模型"
                                    },
                                    isModelValid = currentId.isNotBlank(),
                                    expanded = modelMenu,
                                    onClick = { modelMenu = !modelMenu },
                                    onDismissRequest = { modelMenu = false },
                                    enabled = vm.bot != null,
                                ) {
                                    if (modelArray == null || modelArray.length() == 0) {
                                        AgoraDropdownMenuItem(
                                            text = { Text(if (controller.modelsLoading) "模型加载中…" else "没有可用模型") },
                                            enabled = false,
                                            onClick = {},
                                        )
                                    } else {
                                        ComposerModelMenuItem(
                                            displayText = "跟随 Bot 默认",
                                            selected = controller.preferredModelId.isBlank(),
                                            onClick = {
                                                controller.setModelPreference("", "")
                                                modelMenu = false
                                            },
                                        )
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
                                                val label = model.optString("name").ifBlank { id }
                                                ComposerModelMenuItem(
                                                    displayText = label,
                                                    selected = id == currentId,
                                                    onClick = {
                                                        controller.setModelPreference(
                                                            id, model.optString("default_reasoning_effort"))
                                                        modelMenu = false
                                                    },
                                                )
                                            }
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
                TextButton(onClick = { filePreview = null }) { Text("Close") }
            },
        )
    }
    if (settingsOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { settingsOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.background,
            ) {
                MemohSettingsHost(vm) { settingsOpen = false }
            }
        }
    }

    MemohDialogs(
        vm = vm, api = api, context = context,
        renameDialog = renameDialog, newTitle = newTitle,
        deleteDialog = deleteDialog,
        botDetailsOpen = botDetailsOpen, memoriesOpen = memoriesOpen,
        filesOpen = filesOpen, schedulesOpen = schedulesOpen, queueOpen = queueOpen,
        runtimeControlsOpen = runtimeControlsOpen, compactConfirm = compactConfirm,
        backupOpen = backupOpen, importUri = importUri, backupStatus = backupStatus,
        onRenameDismiss = { renameDialog = false },
        onDeleteDismiss = { deleteDialog = false },
        onBotDetailsDismiss = { botDetailsOpen = false },
        onMemoriesDismiss = { memoriesOpen = false },
        onFilesDismiss = { filesOpen = false },
        onSchedulesDismiss = { schedulesOpen = false },
        onQueueDismiss = { queueOpen = false },
        onRuntimeControlsDismiss = { runtimeControlsOpen = false },
        onCompactDismiss = { compactConfirm = false },
        onBackupDismiss = { backupOpen = false },
        onBackupStatusClear = { backupStatus = "" },
        onImportConsumed = { importUri = null },
        onExport = { sections, passphrase ->
            pendingBackupSections = sections
            pendingBackupPassphrase = passphrase
            createBackupDocument.launch(
                "${vm.bot?.title?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "memoh"}-backup.zip",
            )
        },
        onChooseImport = {
            backupOpen = false
            openBackupDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onOpenMemoryFromDetails = { memoriesOpen = true; botDetailsOpen = false },
        onOpenWorkspaceFromDetails = { filesOpen = true; botDetailsOpen = false },
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
                    text = "Welcome to Memoh.",
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
                    "Start a conversation — your messages stay on the Memoh server.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
