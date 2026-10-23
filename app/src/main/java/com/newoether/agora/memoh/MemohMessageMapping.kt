package com.newoether.agora.memoh

import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.MessageSegment
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.Participant
import com.newoether.agora.model.ToolExecutionStates

internal const val MEMOH_LIVE_MESSAGE_ID = "memoh:live"
internal const val MEMOH_PENDING_MESSAGE_ID = "memoh:pending"

internal fun memohMessageId(turn: MemohTurn, index: Int): String =
    "memoh:turn:${turn.role}:${turn.id.ifBlank { index.toString() }}"

/**
 * Projects Memoh's server-owned turns into the display model the shared Agora
 * message list already renders. This is presentation only: Memoh keeps owning
 * tool execution, history and approval identifiers.
 */
internal fun memohMessages(
    turns: List<MemohTurn>,
    pendingText: String?,
    pendingAttachments: List<MemohAttachment>,
    liveBlocks: List<MemohBlock>,
    running: Boolean,
): List<ChatMessage> = buildList {
    turns.forEachIndexed { index, turn -> add(turn.toChatMessage(memohMessageId(turn, index))) }
    if (pendingText != null) {
        add(
            MemohTurn(MEMOH_PENDING_MESSAGE_ID, "user", pendingText, attachments = pendingAttachments)
                .toChatMessage(MEMOH_PENDING_MESSAGE_ID),
        )
    }
    if (liveBlocks.isNotEmpty() || running) {
        add(
            MemohTurn(MEMOH_LIVE_MESSAGE_ID, "assistant", blocks = liveBlocks)
                .toChatMessage(MEMOH_LIVE_MESSAGE_ID, running),
        )
    }
}

private fun MemohTurn.toChatMessage(messageId: String, running: Boolean = false): ChatMessage {
    val segments = blocks.mapNotNull { block ->
        when (block.type) {
            "reasoning" -> MessageSegment("thought", block.text)
            "tool" -> MessageSegment(
                type = "tool",
                content = block.text,
                toolName = block.toolName,
                toolCallId = block.id.toString(),
                toolArgs = block.toolInput,
                toolResult = listOf(block.toolOutput, block.toolDiff)
                    .filter(String::isNotBlank).joinToString("\n\n"),
                toolState = if (block.toolRunning) ToolExecutionStates.RUNNING else ToolExecutionStates.SUCCEEDED,
                toolProgress = block.text.takeIf { block.toolRunning },
            )
            "error" -> MessageSegment("error", block.text)
            // Attachment rows and unknown notice kinds keep their text but never become tool cards.
            else -> block.text.takeIf(String::isNotBlank)?.let { MessageSegment("answer", it) }
        }
    }.let { mapped ->
        if (text.isNotBlank() && mapped.none { it.type == "answer" && it.content == text }) {
            listOf(MessageSegment("answer", text)) + mapped
        } else {
            mapped
        }
    }
    return ChatMessage(
        id = messageId,
        text = if (role == "user") text else segments.filter { it.type == "answer" }
            .joinToString("\n") { it.content },
        participant = if (role == "user") Participant.USER else Participant.MODEL,
        segments = if (role == "user") null else segments,
        status = when {
            running && blocks.any { it.type == "tool" && it.toolRunning } -> MessageStatus.TOOL_CALLING
            running && segments.none { it.type == "answer" && it.content.isNotBlank() } -> MessageStatus.THINKING
            running -> MessageStatus.SENDING
            blocks.any { it.type == "error" } -> MessageStatus.ERROR
            else -> MessageStatus.SUCCESS
        },
    )
}
