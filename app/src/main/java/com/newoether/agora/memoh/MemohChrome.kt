package com.newoether.agora.memoh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.theme.ChatType

/** Shared chat chrome geometry, mirroring Agora's capsules and composer controls. */
internal val ComposerOuterRadius = 28.dp
internal val ComposerControlHeight = 48.dp
private val TopBarCapsuleHeight = 52.dp
private val ConversationTitleMaxWidth = 180.dp

/**
 * Agora's top bar is not a Material TopAppBar: two pill capsules float over a gradient
 * that fades into the transcript, so messages scroll underneath instead of being clipped.
 */
@Composable
internal fun MemohTopBar(
    vm: MemohViewModel,
    botMenu: Boolean,
    onBotMenuChange: (Boolean) -> Unit,
    accountMenu: Boolean,
    onAccountMenuChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onRefreshBots: () -> Unit,
    onOpenBotDetails: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenQueue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                    0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                    1.0f to Color.Transparent
                )
            )
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TopBarCapsuleHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemohCapsule(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(5.dp))
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Menu, "Open conversations", modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Column(Modifier.padding(end = 20.dp).widthIn(max = ConversationTitleMaxWidth)) {
                        Text(
                            text = if (vm.session == null) vm.bot?.title ?: "Memoh"
                            else vm.session?.title.orEmpty(),
                            style = if (vm.session == null) ChatType.brandTitle else ChatType.conversationTitleSolo,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                if (vm.session != null) append(vm.bot?.title ?: "Memoh").append(" · ")
                                append(if (vm.connected) "Connected" else "Reconnecting…")
                            },
                            style = ChatType.micro,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            MemohCapsule(modifier = Modifier.fillMaxHeight().width(98.dp)) {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(5.dp))
                    Box {
                        IconButton(onClick = { onBotMenuChange(true) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.MoreVert, "Choose bot", modifier = Modifier.size(26.dp))
                        }
                        AgoraDropdownMenu(expanded = botMenu, onDismissRequest = { onBotMenuChange(false) }) {
                            vm.bots.forEach { option ->
                                AgoraDropdownMenuItem(text = { Text(option.title) }, onClick = {
                                    vm.selectBot(option); onBotMenuChange(false)
                                })
                            }
                        }
                    }
                    IconButton(onClick = onNewChat, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Add, "New chat", modifier = Modifier.size(30.dp))
                    }
                    Box {
                        IconButton(onClick = { onAccountMenuChange(true) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.MoreVert, "Account", modifier = Modifier.size(26.dp))
                        }
                        AgoraDropdownMenu(
                            expanded = accountMenu,
                            onDismissRequest = { onAccountMenuChange(false) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 16.dp,
                        ) {
                            AgoraDropdownMenuItem(text = { Text("Refresh bots") }, onClick = {
                                onAccountMenuChange(false); onRefreshBots()
                            })
                            if (vm.bot != null) {
                                AgoraDropdownMenuItem(text = { Text("Bot Details") }, onClick = onOpenBotDetails)
                                AgoraDropdownMenuItem(text = { Text("Bot Backup") }, onClick = onOpenBackup)
                                AgoraDropdownMenuItem(text = { Text("Workspace Files") }, onClick = onOpenFiles)
                                AgoraDropdownMenuItem(text = { Text("Memory") }, onClick = onOpenMemory)
                                AgoraDropdownMenuItem(text = { Text("Schedules") }, onClick = onOpenSchedules)
                            }
                            if (vm.session != null) {
                                AgoraDropdownMenuItem(text = { Text("Message Queue") }, onClick = onOpenQueue)
                                AgoraDropdownMenuItem(text = { Text("Rename chat") }, onClick = onRename)
                                AgoraDropdownMenuItem(text = { Text("Delete chat") }, onClick = onDelete)
                            }
                            AgoraDropdownMenuItem(text = { Text("Sign out") }, onClick = onSignOut)
                        }
                    }
                }
            }
        }
    }
}

/** Pill capsule with a shadow layer beneath, as Agora builds its bar controls. */
@Composable
private fun MemohCapsule(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(modifier = modifier, propagateMinConstraints = true) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = 4.dp,
        ) {}
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

/** Circular send control: filled only when the draft can be sent. */
@Composable
internal fun MemohSendButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ComposerControlHeight),
        shape = RoundedCornerShape(50),
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(22.dp))
        }
    }
}
