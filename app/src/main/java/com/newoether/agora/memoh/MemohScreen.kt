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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

@Composable
private fun MemohLogin(vm: MemohViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Memoh", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Your bots, in a native Android chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = vm.server, onValueChange = { vm.server = it },
                label = { Text("API address") }, singleLine = true,
                supportingText = { Text("Cloud: https://app.memoh.net/api · Self-hosted: your API base URL") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.username, onValueChange = { vm.username = it },
                label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.password, onValueChange = { vm.password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth())
            if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = vm::login, enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()) {
                if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Connect")
            }
            Text("The Memoh server owns every conversation and tool run. Your access token is encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemohScreen(vm: MemohViewModel) {
    if (!vm.signedIn) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MemohLogin(vm)
        }
        return
    }
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
    val createBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selectedBot = vm.bot
        if (uri != null && selectedBot != null) backupScope.launch {
            backupStatus = "Saving backup…"
            runCatching { vm.workspaceApi.exportBotBackup(selectedBot.id, uri, context.contentResolver, pendingBackupSections, pendingBackupPassphrase) }
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
    var selectedAttachments by remember(vm.bot?.id, vm.session?.id) { mutableStateOf<List<MemohAttachment>>(emptyList()) }
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
                    items(vm.sessions, key = { it.id }) { item ->
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
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.bot?.title ?: "Memoh", maxLines = 1)
                                Box {
                                    IconButton(onClick = { botMenu = true }) {
                                        Icon(Icons.Default.MoreVert, "Choose bot")
                                    }
                                    DropdownMenu(expanded = botMenu, onDismissRequest = { botMenu = false }) {
                                        vm.bots.forEach { option ->
                                            DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                                vm.selectBot(option); botMenu = false
                                            })
                                        }
                                    }
                                }
                            }
                            Text(if (vm.connected) "Connected" else "Reconnecting…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Default.Menu, "Open conversations")
                    } },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.Add, "New chat") }
                        Box {
                            IconButton(onClick = { accountMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Account")
                            }
                            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                                DropdownMenuItem(text = { Text("Refresh bots") }, onClick = {
                                    accountMenu = false; vm.refreshBots()
                                })
                                if (vm.bot != null) {
                                    DropdownMenuItem(text = { Text("Bot Details") }, onClick = {
                                        accountMenu = false
                                        botDetailsOpen = true
                                    })
                                    DropdownMenuItem(text = { Text("Bot Backup") }, onClick = {
                                        accountMenu = false
                                        backupStatus = ""
                                        backupOpen = true
                                    })
                                    DropdownMenuItem(text = { Text("Workspace Files") }, onClick = {
                                        accountMenu = false
                                        filesOpen = true
                                    })
                                    DropdownMenuItem(text = { Text("Memory") }, onClick = {
                                        accountMenu = false
                                        memoriesOpen = true
                                    })
                                    DropdownMenuItem(text = { Text("Schedules") }, onClick = {
                                        accountMenu = false
                                        schedulesOpen = true
                                    })
                                }
                                if (vm.session != null) {
                                    DropdownMenuItem(text = { Text("Message Queue") }, onClick = {
                                        accountMenu = false
                                        queueOpen = true
                                        vm.refreshQueue()
                                    })
                                    DropdownMenuItem(text = { Text("Rename chat") }, onClick = {
                                        newTitle = vm.session?.title.orEmpty()
                                        renameDialog = true
                                        accountMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Delete chat") }, onClick = {
                                        deleteDialog = true
                                        accountMenu = false
                                    })
                                }
                                DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                                    accountMenu = false; vm.logout()
                                })
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column(Modifier.imePadding()) {
                    HorizontalDivider()
                    AttachmentDraftStrip(
                        attachments = selectedAttachments,
                        onRemove = { selectedAttachments = selectedAttachments - it }
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom) {
                        AttachmentPickerButton(
                            enabled = vm.bot != null,
                            onAttachmentsPicked = { selectedAttachments = selectedAttachments + it }
                        )
                        Spacer(Modifier.width(4.dp))
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            enabled = vm.bot != null,
                            placeholder = { Text("Message ${vm.bot?.title ?: "Memoh"}") },
                            modifier = Modifier.weight(1f),
                            maxLines = 6,
                            shape = RoundedCornerShape(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (vm.running && vm.runId.isNotBlank()) {
                            IconButton(onClick = vm::abort, enabled = vm.controlsReady) {
                                Icon(Icons.Default.Stop, "Stop run")
                            }
                            Box {
                                IconButton(onClick = { sendQueueMenu = true },
                                    enabled = composer.isNotBlank() && !vm.queueBusy) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Queue message")
                                }
                                DropdownMenu(expanded = sendQueueMenu,
                                    onDismissRequest = { sendQueueMenu = false }) {
                                    DropdownMenuItem(text = { Text("Add Follow-Up") }, onClick = {
                                        if (vm.queueMessage(composer, "follow_up")) composer = ""
                                        sendQueueMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Steer Current Run") },
                                        enabled = vm.queueState?.steerSupported == true,
                                        onClick = {
                                            if (vm.queueMessage(composer, "steer")) composer = ""
                                            sendQueueMenu = false
                                        })
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                if (composer.isNotBlank() || selectedAttachments.isNotEmpty()) {
                                    if (vm.send(composer, selectedAttachments)) {
                                        composer = ""
                                        selectedAttachments = emptyList()
                                    }
                                }
                            }, enabled = (composer.isNotBlank() || selectedAttachments.isNotEmpty()) && vm.bot != null && vm.pendingUserText == null && !vm.running) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (vm.error.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.error, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (vm.bot == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No accessible bots yet")
                    }
                } else if (vm.turns.isEmpty() && vm.liveBlocks.isEmpty() && vm.pendingUserText == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Start a conversation", style = MaterialTheme.typography.titleLarge)
                            Text("Your messages will stay on the Memoh server.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (vm.olderMessagesAvailable || vm.loadingOlderMessages) {
                            item(key = "older-messages") {
                                TextButton(onClick = vm::loadOlderMessages,
                                    enabled = !vm.loadingOlderMessages,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (vm.loadingOlderMessages) "Loading…" else "Load earlier messages")
                                }
                            }
                        }
                        items(vm.turns, key = { it.id.ifBlank { "${it.role}:${vm.turns.indexOf(it)}" } }) { turn ->
                            MemohTurnView(turn, vm)
                        }
                        vm.pendingUserText?.let { value ->
                            item(key = "optimistic") {
                                MemohTurnView(MemohTurn("optimistic", "user", value, attachments = vm.pendingUserAttachments), vm)
                            }
                        }
                        if (vm.liveBlocks.isNotEmpty()) {
                            item(key = "live") {
                                MemohTurnView(MemohTurn("live", "assistant", blocks = vm.liveBlocks), vm)
                            }
                        } else if (vm.running) {
                            item(key = "running") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Agent is working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (backupOpen && vm.bot != null) {
        MemohBackupDialog(vm.workspaceApi, vm.bot!!, backupStatus,
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
        if (selectedBot != null) MemohBackupImportDialog(vm.workspaceApi, selectedBot, context, uri,
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
            api = vm.workspaceApi,
            bot = vm.bot!!,
            agentController = vm.agentController,
            onDismiss = { botDetailsOpen = false },
            onBotUpdated = { updated -> vm.selectBot(updated) },
            onBotDeleted = { vm.refreshBots() },
            onOpenMemory = { memoriesOpen = true; botDetailsOpen = false },
            onOpenWorkspace = { filesOpen = true; botDetailsOpen = false },
        )
    }
    if (memoriesOpen && vm.bot != null) {
        MemohMemoryDialog(vm.workspaceApi, vm.bot!!.id, onDismiss = { memoriesOpen = false })
    }
    if (filesOpen && vm.bot != null) {
        MemohWorkspaceDialog(vm.workspaceApi, vm.bot!!.id, onDismiss = { filesOpen = false })
    }
    if (schedulesOpen && vm.bot != null) {
        MemohSchedulesDialog(vm.workspaceApi, vm.bot!!.id,
            onDismiss = { schedulesOpen = false })
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
                TextButton(onClick = { vm.deleteSelectedSession(); deleteDialog = false }) {
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

