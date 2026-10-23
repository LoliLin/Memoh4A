package com.newoether.agora.memoh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import com.newoether.agora.ui.common.LocalAgoraHaptics

@Composable
internal fun MemohScreen(vm: MemohViewModel) {
    if (!vm.signedIn) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MemohLogin(vm)
        }
        return
    }
    if (vm.teams.enabled && vm.teams.selectedId.isEmpty()) {
        MemohTeamSetup(vm)
        return
    }
    key(vm.teams.selectedId) { MemohConversationScreen(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemohConversationScreen(vm: MemohViewModel) {
    val api = remember { MemohApi(vm.workspaceApi) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
    val context = LocalContext.current
    val backupScope = rememberCoroutineScope()
    val haptics = LocalAgoraHaptics.current
    val createBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selectedBot = vm.bot
        if (uri != null && selectedBot != null) backupScope.launch {
            backupStatus = "Saving backup…"
            runCatching { api.exportBotBackup(selectedBot.id, uri, context.contentResolver, pendingBackupSections, pendingBackupPassphrase) }
                .onSuccess { backupStatus = "Backup saved." }
                .onFailure { backupStatus = "Export failed: ${it.message ?: "Unknown error"}" }
                .also { pendingBackupSections = emptyList(); pendingBackupPassphrase = "" }
        }
    }
    val openBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri
    }
    var sendQueueMenu by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var composer by remember(vm.bot?.id, vm.session?.id) { mutableStateOf("") }
    var selectedAttachments by remember(vm.attachmentDraftOwner) { mutableStateOf<List<MemohAttachment>>(emptyList()) }
    LaunchedEffect(vm.restoreDraft) {
        vm.restoreDraft?.let { rejected ->
            if (composer.isBlank()) composer = rejected
            vm.consumeRestoredDraft()
        }
    }
    val listState = rememberLazyListState()
    var initialScrollSession by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.session?.id, vm.turns.isNotEmpty()) {
        val id = vm.session?.id
        if (id != null && vm.turns.isNotEmpty() && initialScrollSession != id) {
            initialScrollSession = id
            listState.scrollToItem(vm.turns.lastIndex)
        }
    }
    val count = vm.turns.size + (if (vm.pendingUserText != null) 1 else 0) + (if (vm.liveBlocks.isNotEmpty() || vm.running) 1 else 0)
    LaunchedEffect(count, vm.liveBlocks.lastOrNull()?.text) {
        if (count > 0) {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (last >= count - 3) listState.animateScrollToItem(count - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                if (vm.teams.enabled) MemohTeamPicker(vm.teams)
                Text("Conversations", modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(
                    label = { Text("New chat") },
                    selected = vm.session == null,
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = { vm.newChat(); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 10.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn {
                    items((vm.sessions + listOfNotNull(vm.session)).distinctBy { it.id }, key = { it.id }) { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title, maxLines = 2) },
                            selected = vm.session?.id == item.id,
                            onClick = { vm.selectSession(item); scope.launch { drawer.close() } },
                            modifier = Modifier.padding(horizontal = 10.dp))
                    }
                    if (vm.olderSessionsAvailable || vm.loadingSessions) {
                        item {
                            TextButton(onClick = vm::loadOlderSessions, enabled = !vm.loadingSessions,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (vm.loadingSessions) "Loading…" else "Load older chats")
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { Box {} },
            bottomBar = { Box {} },
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        MemohErrorBanner(vm)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            MemohTranscript(vm, listState)
                        }
                    }
                    MemohComposer(
                        vm = vm,
                        composer = composer,
                        onComposerChange = { composer = it },
                        attachments = selectedAttachments,
                        onAttachmentsChange = { selectedAttachments = it },
                        sendQueueMenu = sendQueueMenu,
                        onSendQueueMenuChange = { sendQueueMenu = it },
                        haptics = haptics,
                    )
                }
                MemohTopBar(
                    vm = vm,
                    botMenu = botMenu,
                    onBotMenuChange = { botMenu = it },
                    accountMenu = accountMenu,
                    onAccountMenuChange = { accountMenu = it },
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onNewChat = { haptics.selection(); vm.newChat() },
                    onRefreshBots = vm::refreshBots,
                    onOpenBotDetails = { accountMenu = false; botDetailsOpen = true },
                    onOpenBackup = { accountMenu = false; backupStatus = ""; backupOpen = true },
                    onOpenFiles = { accountMenu = false; filesOpen = true },
                    onOpenMemory = { accountMenu = false; memoriesOpen = true },
                    onOpenSchedules = { accountMenu = false; schedulesOpen = true },
                    onOpenQueue = { accountMenu = false; queueOpen = true; vm.refreshQueue() },
                    onRename = { accountMenu = false; newTitle = vm.session?.title.orEmpty(); renameDialog = true },
                    onDelete = { accountMenu = false; deleteDialog = true },
                    onSignOut = { accountMenu = false; vm.logout() },
                )
            }
        }
    }

    if (backupOpen && vm.bot != null) {
        MemohBackupDialog(api, vm.bot!!, backupStatus,
            onExport = { sections, passphrase ->
                pendingBackupSections = sections
                pendingBackupPassphrase = passphrase
                createBackupDocument.launch("${vm.bot!!.title.replace(Regex("[^A-Za-z0-9._-]"), "_")}-backup.zip")
            },
            onChooseImport = { backupOpen = false; openBackupDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onDismiss = { backupOpen = false })
    }
    importUri?.let { uri ->
        val selectedBot = vm.bot
        if (selectedBot != null) MemohBackupImportDialog(api, selectedBot, context, uri,
            onDismiss = { importUri = null },
            onImported = { vm.refreshBots() })
    }
    if (backupStatus.isNotBlank() && !backupOpen && importUri == null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { backupStatus = "" },
            title = { Text("Bot backup") },
            text = { Text(backupStatus) },
            confirmButton = { TextButton(onClick = { backupStatus = "" }) { Text("OK") } },
        )
    }
    if (botDetailsOpen && vm.bot != null) {
        MemohBotDetailDialog(
            api = api,
            bot = vm.bot!!,
            agentController = vm.agentController,
            onDismiss = { botDetailsOpen = false },
            onBotUpdated = { updated -> vm.updateBotMetadata(updated) },
            onBotDeleted = { vm.refreshBots() },
            onOpenMemory = { memoriesOpen = true; botDetailsOpen = false },
            onOpenWorkspace = { filesOpen = true; botDetailsOpen = false },
        )
    }
    if (memoriesOpen && vm.bot != null) {
        MemohMemoryDialog(api, vm.bot!!.id, onDismiss = { memoriesOpen = false })
    }
    if (filesOpen && vm.bot != null) {
        MemohWorkspaceDialog(api, vm.bot!!.id, onDismiss = { filesOpen = false })
    }
    if (schedulesOpen && vm.bot != null) {
        MemohSchedulesDialog(api, vm.bot!!.id,
            onDismiss = { schedulesOpen = false },
            sessions = vm.sessions,
            onOpenSession = { sessionId ->
                schedulesOpen = false
                vm.openSessionById(sessionId)
            })
    }
    if (queueOpen && vm.session != null) {
        MemohQueueDialog(vm, onDismiss = { queueOpen = false })
    }
    if (renameDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it },
                    label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameSelectedSession(newTitle)
                    renameDialog = false
                }, enabled = newTitle.isNotBlank()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (deleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete Conversation?") },
            text = { Text("This permanently deletes the conversation on the Memoh server.") },
            confirmButton = {
                TextButton(onClick = { haptics.destructiveConfirmed(); vm.deleteSelectedSession(); deleteDialog = false }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MemohTurnView(turn: MemohTurn, vm: MemohViewModel) {
    if (turn.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (turn.text.isNotBlank()) {
                        Text(turn.text, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (turn.attachments.isNotEmpty()) {
                        if (turn.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                        TurnAttachmentsView(turn.attachments)
                    }
                }
            }
        }
    } else if (turn.role == "assistant") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (turn.attachments.isNotEmpty()) {
                TurnAttachmentsView(turn.attachments)
            }
            turn.blocks.forEach { block ->
                when (block.type) {
                    "text" -> if (block.text.isNotBlank()) Markdown(block.text)
                    "reasoning" -> ReasoningBlock(block)
                    "tool" -> ToolBlock(block, vm)
                    "attachments" -> TurnAttachmentsView(block.attachments)
                    "error", "notice" -> Text(block.text,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    else -> if (block.text.isNotBlank()) Text(block.text)
                }
                if (block.type != "attachments" && block.attachments.isNotEmpty()) {
                    TurnAttachmentsView(block.attachments)
                }
            }
        }
    }
}

