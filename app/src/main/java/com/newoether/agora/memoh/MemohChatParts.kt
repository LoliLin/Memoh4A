package com.newoether.agora.memoh

import androidx.compose.ui.platform.LocalContext

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.chat.AgoraDrawerConversationRow
import com.newoether.agora.ui.chat.DrawerConversationIndicator
import com.newoether.agora.ui.chat.DrawerEdgeFadeTolerance
import com.newoether.agora.ui.chat.isDrawerListAtBottom
import com.newoether.agora.ui.chat.isDrawerListAtTop
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.ui.theme.ChatType
import com.newoether.agora.util.verticalEdgeFade

/** Account menu actions that the top bar's overflow surfaces. */
internal enum class MemohAccountAction {
    Search, BotSettings, Files, Schedules, Terminal, Queue, RuntimeControls, Compact, Rename, Delete,
}

/** Memoh's server-backed drawer using Agora's shared shell and conversation row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemohDrawerContent(
    vm: MemohViewModel,
    drawerWidth: Dp,
    selectedId: String?,
    onNewChat: () -> Unit,
    onSelect: (MemohSession) -> Unit,
    onLoadOlder: () -> Unit,
    onRename: (MemohSession) -> Unit,
    onDelete: (MemohSession) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val drawerSessions = (vm.sessions + listOfNotNull(vm.session)).distinctBy { it.id }
    val showInitialListLoading = vm.loadingSessions && drawerSessions.isEmpty()
    val motionPolicy = LocalAgoraMotionPolicy.current
    val sessionListState = rememberLazyListState()
    val density = LocalDensity.current
    val edgeFadeTolerancePx = with(density) { DrawerEdgeFadeTolerance.roundToPx() }
    val atTop by remember(sessionListState, edgeFadeTolerancePx) {
        derivedStateOf {
            isDrawerListAtTop(
                firstVisibleItemIndex = sessionListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffsetPx = sessionListState.firstVisibleItemScrollOffset,
                tolerancePx = edgeFadeTolerancePx,
            )
        }
    }
    val atBottom by remember(sessionListState, edgeFadeTolerancePx) {
        derivedStateOf {
            val layoutInfo = sessionListState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.maxByOrNull { it.index }
            isDrawerListAtBottom(
                totalItemsCount = layoutInfo.totalItemsCount,
                lastVisibleItemIndex = lastVisibleItem?.index,
                lastVisibleItemEndOffsetPx = lastVisibleItem?.let { it.offset + it.size },
                viewportEndOffsetPx = layoutInfo.viewportEndOffset,
                tolerancePx = edgeFadeTolerancePx,
            )
        }
    }
    val topFadeWeight by animateFloatAsState(
        targetValue = if (atTop) 0f else 1f,
        animationSpec = tween(200),
        label = "memohDrawerTopFade",
    )
    val bottomFadeWeight by animateFloatAsState(
        targetValue = if (atBottom) 0f else 1f,
        animationSpec = tween(200),
        label = "memohDrawerBottomFade",
    )

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 1.dp,
        modifier = Modifier.width(drawerWidth).graphicsLayer { clip = true },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            if (vm.teams.enabled) MemohTeamPicker(vm.teams)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNewChat,
                enabled = vm.bot != null,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat), style = ChatType.drawerButton)
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showInitialListLoading,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                ) {
                    LazyColumn(
                        state = sessionListState,
                        modifier = Modifier.fillMaxSize().verticalEdgeFade(
                            edgeFadeDp = 40f,
                            topWeight = topFadeWeight,
                            bottomWeight = bottomFadeWeight,
                        ),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.conversations),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                                    .semantics { heading() },
                            )
                        }
                        items(drawerSessions, key = { it.id }) { session ->
                            Box(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = if (motionPolicy.allowSpatialTransitions) {
                                        tween(400)
                                    } else {
                                        null
                                    },
                                    fadeOutSpec = tween(180),
                                ),
                            ) {
                                MemohDrawerConversationRow(
                                    title = session.title,
                                    selected = session.id == selectedId,
                                    running = vm.running && vm.session?.id == session.id,
                                    onSelect = { onSelect(session) },
                                    onRename = { onRename(session) },
                                    onDelete = { onDelete(session) },
                                )
                            }
                        }
                        if (vm.olderSessionsAvailable || vm.loadingSessions) {
                            item {
                                TextButton(
                                    onClick = onLoadOlder,
                                    enabled = !vm.loadingSessions,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (vm.loadingSessions) uiResources.getString(R.string.memoh_ui_loading) else uiResources.getString(R.string.memoh_ui_load_older_chats))
                                }
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showInitialListLoading,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 4.dp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = CircleShape,
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings), style = ChatType.drawerButton)
            }
        }
    }
}

@Composable
private fun MemohDrawerConversationRow(
    title: String,
    selected: Boolean,
    running: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box {
        AgoraDrawerConversationRow(
            title = title,
            selected = selected,
            indicator = if (running) DrawerConversationIndicator.GENERATING
            else DrawerConversationIndicator.NONE,
            contextMenuVisible = showMenu,
            onClick = onSelect,
            onLongClick = { offset ->
                menuOffset = offset
                showMenu = true
            },
        )
        AgoraDropdownMenu(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 16.dp,
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = menuOffset,
        ) {
            AgoraDropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { showMenu = false; onRename() },
            )
            AgoraDropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { showMenu = false; onDelete() },
            )
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
    val uiResources = LocalContext.current.resources
    val sectionModifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (vm.bots.size > 1) {
        Text("BOTS", modifier = sectionModifier, style = MaterialTheme.typography.labelSmall, color = sectionColor)
        vm.bots.forEach { option ->
            AgoraDropdownMenuItem(
                text = { Text(if (option.id == vm.bot?.id) "● ${option.title}" else option.title) },
                onClick = { dismiss(); vm.selectBot(option) },
            )
        }
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }

    if (vm.bot != null) {
        Text("BOT", modifier = sectionModifier, style = MaterialTheme.typography.labelSmall, color = sectionColor)
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_bot_settings)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.BotSettings) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_workspace_files)) },
            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.Files) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_terminal)) },
            leadingIcon = { Icon(Icons.Default.Code, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.Terminal) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_schedules)) },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.Schedules) },
        )
    }

    if (vm.session != null) {
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("CONVERSATION", modifier = sectionModifier, style = MaterialTheme.typography.labelSmall, color = sectionColor)
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_search_in_conversation)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.Search) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_message_queue)) },
            onClick = { dismiss(); onAction(MemohAccountAction.Queue) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_runtime_controls)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.RuntimeControls) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_compact_context)) },
            onClick = { dismiss(); onAction(MemohAccountAction.Compact) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_rename_chat)) },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.Rename) },
        )
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_delete_chat)) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { dismiss(); onAction(MemohAccountAction.Delete) },
        )
    }

    // Signing out lives in Settings → Account. Duplicating it in the one-tap overflow menu made an
    // irreversible session action far too easy to hit while reaching for a chat action.
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
    botSettingsOpen: Boolean,
    filesOpen: Boolean,
    schedulesOpen: Boolean,
    queueOpen: Boolean,
    runtimeControlsOpen: Boolean,
    compactConfirm: Boolean,
    importUri: Uri?,
    backupStatus: String,
    onRenameDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onBotSettingsDismiss: () -> Unit,
    onFilesDismiss: () -> Unit,
    onSchedulesDismiss: () -> Unit,
    onQueueDismiss: () -> Unit,
    onRuntimeControlsDismiss: () -> Unit,
    onCompactDismiss: () -> Unit,
    onImportConsumed: () -> Unit,
    onBackupExport: (List<String>, String) -> Unit,
    onBackupImport: () -> Unit,
    onOpenWorkspaceFromSettings: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val bot = vm.bot
    // Bot settings is a full settings destination now (see MemohBotSettingsScreen), so this host
    // only keeps the dialogs that genuinely are dialogs.
    importUri?.let { uri ->
        if (bot != null) {
            MemohBackupImportDialog(
                api = api, bot = bot, context = context, uri = uri,
                onDismiss = onImportConsumed,
                onImported = { vm.refreshBots(); onImportConsumed() },
            )
        }
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
    if (runtimeControlsOpen && vm.session != null) {
        MemohRuntimeControlsDialog(vm, onDismiss = onRuntimeControlsDismiss)
    }
    if (compactConfirm) {
        AlertDialog(
            onDismissRequest = onCompactDismiss,
            title = { Text(uiResources.getString(R.string.memoh_ui_compact_context)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_ask_the_server_to_compact_this_conversation_s_context_now) +
                uiResources.getString(R.string.memoh_ui_the_conversation_stays_on_the_server_compaction_summarizes_older_turns)) },
            confirmButton = {
                TextButton(onClick = { vm.compactSession(); onCompactDismiss() },
                    enabled = !vm.runtimeControlsBusy) { Text(uiResources.getString(R.string.memoh_ui_compact)) }
            },
            dismissButton = { TextButton(onClick = onCompactDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
    if (renameDialog) {
        var draft = newTitle
        AlertDialog(
            onDismissRequest = onRenameDismiss,
            title = { Text(uiResources.getString(R.string.memoh_ui_rename_conversation)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.renameSelectedSession(draft); onRenameDismiss() },
                    enabled = draft.isNotBlank(),
                ) { Text(uiResources.getString(R.string.memoh_ui_save)) }
            },
            dismissButton = { TextButton(onClick = onRenameDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_conversation)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_this_permanently_deletes_the_conversation_on_the_memoh_server)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelectedSession(); onDeleteDismiss() }) {
                    Text(uiResources.getString(R.string.memoh_ui_delete))
                }
            },
            dismissButton = { TextButton(onClick = onDeleteDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
}
