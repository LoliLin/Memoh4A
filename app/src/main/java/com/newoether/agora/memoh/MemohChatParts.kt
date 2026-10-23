package com.newoether.agora.memoh

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon

/** Account menu actions that the top bar's overflow surfaces. */
internal enum class MemohAccountAction {
    BotDetails, Backup, Files, Memory, Schedules, Queue, Rename, Delete,
}

/**
 * Memoh's drawer body inside Agora's drawer host: conversations, older pages and
 * the team picker. The gesture shell and scrim belong to the shared component.
 */
@Composable
internal fun MemohDrawerContent(
    vm: MemohViewModel,
    selectedId: String?,
    onNewChat: () -> Unit,
    onSelect: (MemohSession) -> Unit,
    onLoadOlder: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.fillMaxSize()) {
            if (vm.teams.enabled) MemohTeamPicker(vm.teams)
            Text(
                "Conversations",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            NavigationDrawerItem(
                label = { Text("New chat") },
                selected = selectedId == null,
                icon = { Icon(Icons.Default.Add, null) },
                onClick = onNewChat,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(
                    (vm.sessions + listOfNotNull(vm.session)).distinctBy { it.id },
                    key = { it.id },
                ) { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title, maxLines = 2) },
                        selected = selectedId == item.id,
                        onClick = { onSelect(item) },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                if (vm.olderSessionsAvailable || vm.loadingSessions) {
                    item {
                        TextButton(
                            onClick = onLoadOlder,
                            enabled = !vm.loadingSessions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (vm.loadingSessions) "Loading…" else "Load older chats")
                        }
                    }
                }
            }
        }
    }
}

/** Overflow items for the session/account menu; the caller owns which dialog opens. */
@Composable
internal fun MemohAccountMenu(
    vm: MemohViewModel,
    dismiss: () -> Unit,
    onAction: (MemohAccountAction) -> Unit,
) {
    AgoraDropdownMenuItem(
        text = { Text("Refresh bots") },
        onClick = { dismiss(); vm.refreshBots() },
    )
    if (vm.bot != null) {
        AgoraDropdownMenuItem(
            text = { Text("Bot Details") },
            onClick = { dismiss(); onAction(MemohAccountAction.BotDetails) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Bot Backup") },
            onClick = { dismiss(); onAction(MemohAccountAction.Backup) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Workspace Files") },
            onClick = { dismiss(); onAction(MemohAccountAction.Files) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Memory") },
            onClick = { dismiss(); onAction(MemohAccountAction.Memory) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Schedules") },
            onClick = { dismiss(); onAction(MemohAccountAction.Schedules) },
        )
    }
    if (vm.session != null) {
        AgoraDropdownMenuItem(
            text = { Text("Message Queue") },
            onClick = { dismiss(); onAction(MemohAccountAction.Queue) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Rename chat") },
            onClick = { dismiss(); onAction(MemohAccountAction.Rename) },
        )
        AgoraDropdownMenuItem(
            text = { Text("Delete chat") },
            onClick = { dismiss(); onAction(MemohAccountAction.Delete) },
        )
    }
    AgoraDropdownMenuItem(
        text = { Text("Sign out") },
        onClick = { dismiss(); vm.logout() },
    )
}

/** Every Memoh dialog the chat screen can raise, kept out of the screen body. */
@Composable
internal fun MemohDialogs(
    vm: MemohViewModel,
    api: MemohApi,
    context: Context,
    renameDialog: Boolean,
    newTitle: String,
    deleteDialog: Boolean,
    botDetailsOpen: Boolean,
    memoriesOpen: Boolean,
    filesOpen: Boolean,
    schedulesOpen: Boolean,
    queueOpen: Boolean,
    backupOpen: Boolean,
    importUri: Uri?,
    backupStatus: String,
    onRenameDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onBotDetailsDismiss: () -> Unit,
    onMemoriesDismiss: () -> Unit,
    onFilesDismiss: () -> Unit,
    onSchedulesDismiss: () -> Unit,
    onQueueDismiss: () -> Unit,
    onBackupDismiss: () -> Unit,
    onBackupStatusClear: () -> Unit,
    onImportConsumed: () -> Unit,
    onExport: (List<String>, String) -> Unit,
    onChooseImport: () -> Unit,
    onOpenMemoryFromDetails: () -> Unit,
    onOpenWorkspaceFromDetails: () -> Unit,
) {
    val bot = vm.bot
    if (backupOpen && bot != null) {
        MemohBackupDialog(
            api = api, bot = bot, status = backupStatus,
            onExport = onExport,
            onChooseImport = onChooseImport,
            onDismiss = onBackupDismiss,
        )
    }
    importUri?.let { uri ->
        if (bot != null) {
            MemohBackupImportDialog(
                api = api, bot = bot, context = context, uri = uri,
                onDismiss = onImportConsumed,
                onImported = { vm.refreshBots(); onImportConsumed() },
            )
        }
    }
    if (backupStatus.isNotBlank() && !backupOpen && importUri == null) {
        AlertDialog(
            onDismissRequest = onBackupStatusClear,
            title = { Text("Bot backup") },
            text = { Text(backupStatus) },
            confirmButton = { TextButton(onClick = onBackupStatusClear) { Text("OK") } },
        )
    }
    if (botDetailsOpen && bot != null) {
        MemohBotDetailDialog(
            api = api, bot = bot, agentController = vm.agentController,
            onDismiss = onBotDetailsDismiss,
            onBotUpdated = vm::updateBotMetadata,
            onBotDeleted = { vm.refreshBots(); onBotDetailsDismiss() },
            onOpenMemory = onOpenMemoryFromDetails,
            onOpenWorkspace = onOpenWorkspaceFromDetails,
        )
    }
    if (memoriesOpen && bot != null) {
        MemohMemoryDialog(api, bot.id, onDismiss = onMemoriesDismiss)
    }
    if (filesOpen && bot != null) {
        MemohWorkspaceDialog(api, bot.id, onDismiss = onFilesDismiss)
    }
    if (schedulesOpen && bot != null) {
        MemohSchedulesDialog(
            api = api, botId = bot.id,
            onDismiss = onSchedulesDismiss,
            sessions = vm.sessions,
            onOpenSession = { sessionId ->
                onSchedulesDismiss()
                vm.openSessionById(sessionId)
            },
        )
    }
    if (queueOpen && vm.session != null) {
        MemohQueueDialog(vm, onDismiss = onQueueDismiss)
    }
    if (renameDialog) {
        var draft = newTitle
        AlertDialog(
            onDismissRequest = onRenameDismiss,
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.renameSelectedSession(draft); onRenameDismiss() },
                    enabled = draft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onRenameDismiss) { Text("Cancel") } },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text("Delete Conversation?") },
            text = { Text("This permanently deletes the conversation on the Memoh server.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelectedSession(); onDeleteDismiss() }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = onDeleteDismiss) { Text("Cancel") } },
        )
    }
}
