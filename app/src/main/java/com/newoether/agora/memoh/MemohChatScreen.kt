package com.newoether.agora.memoh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.ui.chat.ChatDrawerHost
import com.newoether.agora.ui.chat.ChatTopBar
import com.newoether.agora.ui.chat.MessageList
import com.newoether.agora.ui.chat.bottombar.ChatComposerLayout
import com.newoether.agora.ui.chat.bottombar.ChatComposerSurface
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
    val drawer = rememberChatDrawerState()
    val motion = LocalAgoraMotionPolicy.current
    val scope = rememberCoroutineScope()
    val haptics = LocalAgoraHaptics.current
    val context = LocalContext.current

    var botMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    var memoriesOpen by remember { mutableStateOf(false) }
    var schedulesOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var botDetailsOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var backupStatus by remember { mutableStateOf("") }
    var pendingBackupSections by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBackupPassphrase by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var sendQueueMenu by remember { mutableStateOf(false) }
    var selectedAttachments by remember(vm.attachmentDraftOwner) {
        mutableStateOf<List<MemohAttachment>>(emptyList())
    }
    var expanded by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableStateOf(0f) }

    val field = remember(vm.bot?.id, vm.session?.id) { TextFieldState() }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val backupScope = rememberCoroutineScope()

    // A rejected send restores the draft instead of losing it.
    LaunchedEffect(vm.restoreDraft) {
        vm.restoreDraft?.let { rejected ->
            if (field.text.isBlank()) field.edit { replace(0, length, rejected) }
            vm.consumeRestoredDraft()
        }
    }
    LaunchedEffect(vm.session?.id) { field.clearText() }

    val messages = remember(vm.turns, vm.pendingUserText, vm.liveBlocks, vm.running) {
        memohMessages(
            turns = vm.turns,
            pendingText = vm.pendingUserText,
            pendingAttachments = vm.pendingUserAttachments,
            liveBlocks = vm.liveBlocks,
            running = vm.running,
        )
    }
    val stable = remember(messages) { StableMessageList(messages) }
    val conversations = remember(vm.sessions, vm.session, vm.bots) {
        (vm.sessions + listOfNotNull(vm.session)).distinctBy { it.id }
            .map { ChatConversation(id = it.id, title = it.title) }
    }

    LaunchedEffect(vm.session?.id, messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
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
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ChatTopBar(
                    isNewChatMode = vm.session == null,
                    conversations = conversations,
                    currentConversationId = vm.session?.id,
                    currentConversationTitle = vm.session?.title,
                    totalTokens = 0,
                    contextTokenBudget = 0,
                    contextAvailable = false,
                    subtitle = buildString {
                        append(vm.bot?.title ?: "Memoh")
                        append(" · ")
                        append(if (vm.connected) "Connected" else "Reconnecting…")
                    },
                    onOpenDrawer = { scope.launch { drawer.toggle(motion) } },
                    systemPromptEnabled = false,
                    onSystemPromptClick = {},
                    moreMenuContent = { dismiss -> MemohAccountMenu(vm, dismiss) { action ->
                        accountMenu = false
                        when (action) {
                            MemohAccountAction.BotDetails -> botDetailsOpen = true
                            MemohAccountAction.Backup -> { backupStatus = ""; backupOpen = true }
                            MemohAccountAction.Files -> filesOpen = true
                            MemohAccountAction.Memory -> memoriesOpen = true
                            MemohAccountAction.Schedules -> schedulesOpen = true
                            MemohAccountAction.Queue -> { queueOpen = true; vm.refreshQueue() }
                            MemohAccountAction.Rename -> {
                                newTitle = vm.session?.title.orEmpty(); renameDialog = true
                            }
                            MemohAccountAction.Delete -> deleteDialog = true
                        }
                    } },
                    onNewChat = { haptics.selection(); vm.newChat() },
                    newChatEnabled = vm.bot != null,
                    trailingActions = {
                        Box {
                            IconButton(onClick = { botMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Choose bot")
                            }
                            AgoraDropdownMenu(
                                expanded = botMenu,
                                onDismissRequest = { botMenu = false },
                            ) {
                                vm.bots.forEach { option ->
                                    AgoraDropdownMenuItem(
                                        text = { Text(option.title) },
                                        onClick = { vm.selectBot(option); botMenu = false },
                                    )
                                }
                            }
                        }
                    },
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        vm.error.isNotBlank() -> MemohNotice(vm.error, isError = true)
                        vm.bot == null -> MemohNotice("No accessible bots yet")
                        messages.isEmpty() -> MemohNotice(
                            "Start a conversation\nYour messages will stay on the Memoh server.",
                        )
                        else -> MessageList(
                            messages = stable,
                            conversationId = vm.session?.id,
                            state = listState,
                            messageActionsEnabled = false,
                            readOnlyActions = true,
                            isLoading = vm.running,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { },
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 148.dp,
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
                isExpanded = expanded,
                onBarHeightChanged = { barHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChatComposerLayout(
                    textFieldState = field,
                    focusRequester = focus,
                    onInputFocusChanged = {},
                    isExpanded = expanded,
                    isExpandAnimating = false,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AttachmentPickerButton(
                                    enabled = vm.bot != null,
                                    currentOwner = { vm.attachmentDraftOwner },
                                    currentAttachments = { selectedAttachments },
                                    onAttachmentsPicked = { selectedAttachments = selectedAttachments + it },
                                    onAttachmentError = { vm.reportAttachmentError(it) },
                                )
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

    MemohDialogs(
        vm = vm, api = api, context = context,
        renameDialog = renameDialog, newTitle = newTitle,
        deleteDialog = deleteDialog,
        botDetailsOpen = botDetailsOpen, memoriesOpen = memoriesOpen,
        filesOpen = filesOpen, schedulesOpen = schedulesOpen, queueOpen = queueOpen,
        backupOpen = backupOpen, importUri = importUri, backupStatus = backupStatus,
        onRenameDismiss = { renameDialog = false },
        onDeleteDismiss = { deleteDialog = false },
        onBotDetailsDismiss = { botDetailsOpen = false },
        onMemoriesDismiss = { memoriesOpen = false },
        onFilesDismiss = { filesOpen = false },
        onSchedulesDismiss = { schedulesOpen = false },
        onQueueDismiss = { queueOpen = false },
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
}

@Composable
private fun MemohNotice(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isError) {
            Text(text, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(24.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                text.split('\n').forEachIndexed { index, line ->
                    Text(
                        line,
                        style = if (index == 0) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodySmall,
                        color = if (index == 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
