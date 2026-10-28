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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
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
    Search, Files, Schedules, Terminal, ManageBots,
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
            // Bot and team share one line: they answer "where am I" together, and two stacked
            // rows spent drawer height on the thing the conversation list needs most.
            MemohDrawerIdentityRow(vm)
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
                if (vm.profileAvatarUrl.isNotBlank()) {
                    MemohAvatarImage(vm.profileAvatarUrl, null, Modifier.size(22.dp).clip(CircleShape))
                } else {
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    vm.profileDisplayName.ifBlank { stringResource(R.string.settings) },
                    style = ChatType.drawerButton,
                )
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

    if (vm.bots.isNotEmpty()) {
        Text("BOTS", modifier = sectionModifier, style = MaterialTheme.typography.labelSmall, color = sectionColor)
        vm.bots.forEach { option ->
            AgoraDropdownMenuItem(
                text = { Text(if (option.id == vm.bot?.id) "● ${option.title}" else option.title) },
                leadingIcon = { MemohBotAvatar(option, size = 22.dp) },
                onClick = { dismiss(); vm.selectBot(option) },
            )
        }
        AgoraDropdownMenuItem(
            text = { Text(uiResources.getString(R.string.memoh_ui_manage_bots)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { dismiss(); onAction(MemohAccountAction.ManageBots) },
        )
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }

    if (vm.bot != null) {
        // No second title here: the BOTS section above already names this Bot, and repeating it
        // is exactly what made the menu look like it had two Bot entries.
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
    importUri: Uri?,
    backupStatus: String,
    onRenameDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onBotSettingsDismiss: () -> Unit,
    onFilesDismiss: () -> Unit,
    onSchedulesDismiss: () -> Unit,
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

/** The drawer's bot identity: same avatar + name pair the web sidebar shows. */
@Composable
private fun MemohDrawerIdentityRow(vm: MemohViewModel) {
    val bot = vm.bot
    var botMenu by remember { mutableStateOf(false) }
    var teamMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The bot owns the row: its avatar and name are what a conversation is held with.
        Box(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = vm.bots.size > 1) { botMenu = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemohBotAvatar(bot, size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    bot?.title ?: stringResource(R.string.memoh_settings_no_bots),
                    style = ChatType.drawerButton,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (vm.bots.size > 1) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.memoh_settings_switch_bot),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AgoraDropdownMenu(
                expanded = botMenu,
                onDismissRequest = { botMenu = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 16.dp,
            ) {
                vm.bots.forEach { option ->
                    AgoraDropdownMenuItem(
                        text = { Text(if (option.id == vm.bot?.id) "● ${option.title}" else option.title) },
                        leadingIcon = { MemohBotAvatar(option, size = 22.dp) },
                        onClick = { botMenu = false; vm.selectBot(option) },
                    )
                }
            }
        }

        if (vm.teams.enabled) {
            Spacer(Modifier.width(4.dp))
            // Team is the quieter half: a compact chip on the same baseline, so the row still
            // reads as one line even with a long bot title.
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !vm.teams.busy) { teamMenu = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vm.teams.current?.title ?: stringResource(R.string.memoh_settings_choose_team),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 88.dp),
                    )
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = stringResource(R.string.memoh_settings_team),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AgoraDropdownMenu(
                    expanded = teamMenu,
                    onDismissRequest = { teamMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 16.dp,
                ) {
                    vm.teams.items.forEach { team ->
                        AgoraDropdownMenuItem(
                            text = { Text(if (team.id == vm.teams.selectedId) "✓ ${team.title}" else team.title) },
                            onClick = { teamMenu = false; vm.teams.select(team.id) },
                        )
                    }
                    AgoraDropdownMenuItem(
                        text = { Text(stringResource(R.string.memoh_settings_refresh_team)) },
                        onClick = { teamMenu = false; vm.teams.load() },
                    )
                }
            }
        }
    }
}
