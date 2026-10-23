package com.newoether.agora.memoh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.newoether.agora.ui.chat.message.UserBubbleShape
import com.newoether.agora.ui.common.AgoraHaptics
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.theme.ChatType

/** User bubble sizing, matching Agora's own message bubbles. */
internal val UserBubbleMaxWidth = 300.dp
internal val UserBubbleContentPadding = 15.dp

/** Transient error strip above the transcript. */
@Composable
internal fun MemohErrorBanner(vm: MemohViewModel) {
    if (vm.error.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            vm.error,
            modifier = Modifier.weight(1f),
            style = ChatType.errorBody,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * Transcript column. The top padding leaves room for the floating bar, so the first
 * message starts below the capsules but still scrolls underneath the gradient.
 */
@Composable
internal fun MemohTranscript(vm: MemohViewModel, listState: LazyListState) {
    when {
        vm.bot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No accessible bots yet",
                style = ChatType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        vm.turns.isEmpty() && vm.liveBlocks.isEmpty() && vm.pendingUserText == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Start a conversation", style = ChatType.sheetTitle)
                    Text(
                        "Your messages stay on the Memoh server.",
                        style = ChatType.metaNormal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        else -> LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 84.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (vm.olderMessagesAvailable || vm.loadingOlderMessages) {
                item(key = "older-messages") {
                    TextButton(
                        onClick = vm::loadOlderMessages,
                        enabled = !vm.loadingOlderMessages,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (vm.loadingOlderMessages) "Loading…" else "Load earlier messages")
                    }
                }
            }
            items(vm.turns, key = { it.id.ifBlank { "${it.role}:${vm.turns.indexOf(it)}" } }) { turn ->
                MemohTurnView(turn, vm)
            }
            vm.pendingUserText?.let { value ->
                item(key = "optimistic") {
                    MemohTurnView(
                        MemohTurn("optimistic", "user", value, attachments = vm.pendingUserAttachments),
                        vm
                    )
                }
            }
            if (vm.liveBlocks.isNotEmpty()) {
                item(key = "live") {
                    MemohTurnView(MemohTurn("live", "assistant", blocks = vm.liveBlocks), vm)
                }
            } else if (vm.running) {
                item(key = "running") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Agent is working…",
                            style = ChatType.metaNormal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Composer panel: Agora's 28 dp outer radius with the input on top and the controls
 * row beneath, inside the navigation and IME insets.
 */
@Composable
internal fun MemohComposer(
    vm: MemohViewModel,
    composer: String,
    onComposerChange: (String) -> Unit,
    attachments: List<MemohAttachment>,
    onAttachmentsChange: (List<MemohAttachment>) -> Unit,
    sendQueueMenu: Boolean,
    onSendQueueMenuChange: (Boolean) -> Unit,
    haptics: AgoraHaptics,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(ComposerOuterRadius),
        ) {
            Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 8.dp)) {
                AttachmentDraftStrip(
                    attachments = attachments,
                    onRemove = { onAttachmentsChange(attachments - it) }
                )
                TextField(
                    value = composer,
                    onValueChange = onComposerChange,
                    enabled = vm.bot != null,
                    placeholder = {
                        Text(
                            "Message ${vm.bot?.title ?: "Memoh"}",
                            style = ChatType.input,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = ChatType.input.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AttachmentPickerButton(
                            enabled = vm.bot != null,
                            currentOwner = { vm.attachmentDraftOwner },
                            currentAttachments = { attachments },
                            onAttachmentsPicked = { onAttachmentsChange(attachments + it) },
                            onAttachmentError = { vm.reportAttachmentError(it) }
                        )
                        if (vm.running && vm.runId.isNotBlank()) {
                            IconButton(
                                onClick = { haptics.interrupt(); vm.abort() },
                                enabled = vm.controlsReady,
                                modifier = Modifier.size(ComposerControlHeight)
                            ) {
                                Icon(Icons.Default.Stop, "Stop run")
                            }
                            Box {
                                IconButton(
                                    onClick = { onSendQueueMenuChange(true) },
                                    enabled = composer.isNotBlank() && !vm.queueBusy,
                                    modifier = Modifier.size(ComposerControlHeight)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Queue message")
                                }
                                AgoraDropdownMenu(
                                    expanded = sendQueueMenu,
                                    onDismissRequest = { onSendQueueMenuChange(false) }
                                ) {
                                    AgoraDropdownMenuItem(text = { Text("Add Follow-Up") }, onClick = {
                                        if (vm.queueMessage(composer, "follow_up")) onComposerChange("")
                                        onSendQueueMenuChange(false)
                                    })
                                    AgoraDropdownMenuItem(
                                        text = { Text("Steer Current Run") },
                                        enabled = vm.queueState?.steerSupported == true,
                                        onClick = {
                                            if (vm.queueMessage(composer, "steer")) onComposerChange("")
                                            onSendQueueMenuChange(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    MemohSendButton(
                        enabled = (composer.isNotBlank() || attachments.isNotEmpty()) &&
                            vm.bot != null && vm.pendingUserText == null && !vm.running,
                        onClick = {
                            if (composer.isNotBlank() || attachments.isNotEmpty()) {
                                if (vm.send(composer, attachments)) {
                                    onComposerChange("")
                                    onAttachmentsChange(emptyList())
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** One transcript turn: Agora's right-aligned user bubble, then left-aligned markdown. */
@Composable
private fun MemohTurnView(turn: MemohTurn, vm: MemohViewModel) {
    if (turn.role == "user") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = UserBubbleShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = UserBubbleMaxWidth)
            ) {
                Column(Modifier.padding(UserBubbleContentPadding)) {
                    if (turn.text.isNotBlank()) {
                        Text(
                            turn.text,
                            style = ChatType.userBody,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (turn.attachments.isNotEmpty()) {
                        if (turn.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                        TurnAttachmentsView(turn.attachments)
                    }
                }
            }
        }
    } else if (turn.role == "assistant") {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (turn.attachments.isNotEmpty()) TurnAttachmentsView(turn.attachments)
            turn.blocks.forEach { block ->
                when (block.type) {
                    "text" -> if (block.text.isNotBlank()) Markdown(block.text)
                    "reasoning" -> ReasoningBlock(block)
                    "tool" -> ToolBlock(block, vm)
                    "attachments" -> TurnAttachmentsView(block.attachments)
                    "error", "notice" -> Text(
                        block.text,
                        color = MaterialTheme.colorScheme.error,
                        style = ChatType.errorBody
                    )
                    else -> if (block.text.isNotBlank()) Text(block.text, style = ChatType.body)
                }
                if (block.type != "attachments" && block.attachments.isNotEmpty()) {
                    TurnAttachmentsView(block.attachments)
                }
            }
        }
    }
}
