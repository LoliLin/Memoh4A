package com.newoether.agora.memoh

import com.newoether.agora.model.AttachmentItem
import com.newoether.agora.model.AttachmentMeta
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
    cacheDir: java.io.File? = null,
): List<ChatMessage> = buildList {
    turns.forEachIndexed { index, turn ->
        add(turn.toChatMessage(memohMessageId(turn, index), cacheDir = cacheDir))
    }
    if (pendingText != null) {
        add(
            MemohTurn(MEMOH_PENDING_MESSAGE_ID, "user", pendingText, attachments = pendingAttachments)
                .toChatMessage(MEMOH_PENDING_MESSAGE_ID, cacheDir = cacheDir),
        )
    }
    if (liveBlocks.isNotEmpty() || running) {
        add(
            MemohTurn(MEMOH_LIVE_MESSAGE_ID, "assistant", blocks = liveBlocks)
                .toChatMessage(MEMOH_LIVE_MESSAGE_ID, running),
        )
    }
}

/**
 * A payload-free placeholder for the live assistant row. The full streaming payload arrives
 * through MessageList's streamingMessage overlay channel (matched by id), so each runtime
 * delta replaces only the overlay instead of rebuilding the whole list.
 */
internal fun memohLiveStub(): ChatMessage = ChatMessage(
    id = MEMOH_LIVE_MESSAGE_ID,
    text = "",
    participant = Participant.MODEL,
    status = MessageStatus.SENDING,
)

/**
 * Materialize base64/URL attachments as cached files so the shared Agora bubble can render
 * them through its own attachment-thumbnail pipeline (Coil paths only, never raw base64).
 * Image metadata links each item back to its file through imageIndex.
 */
internal fun MemohAttachment.materialize(cacheDir: java.io.File): java.io.File? {
    if (base64.isBlank()) return null
    val target = java.io.File(cacheDir, "attachments").apply { mkdirs() }
    val safeName = name.ifBlank { "attachment-${id.hashCode()}" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = java.io.File(target, "${id.hashCode()}-$safeName")
    if (file.exists() && file.length() > 0) return file
    return try {
        java.util.Base64.getDecoder().decode(base64).let { file.writeBytes(it) }
        file
    } catch (_: IllegalArgumentException) { null }
}

/** Cache lookup without writing; composition calls this, workers call [materialize]. */
internal fun MemohAttachment.cachedFile(cacheDir: java.io.File): java.io.File? {
    if (base64.isBlank() && path.isBlank() && url.isBlank()) return null
    val safeName = name.ifBlank { "attachment-${id.hashCode()}" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = java.io.File(java.io.File(cacheDir, "attachments"), "${id.hashCode()}-$safeName")
    return file.takeIf { it.exists() && it.length() > 0 }
}

/** Server-history attachments reference container paths; bytes arrive on demand. */
internal fun MemohAttachment.needsDownload(): Boolean =
    base64.isBlank() && (path.isNotBlank() || url.isNotBlank())

/** Where [downloadAttachmentBytes] writes; the same cache slot [cachedFile] reads. */
internal fun MemohAttachment.cachedTarget(cacheDir: java.io.File): java.io.File {
    val safeName = name.ifBlank { "attachment-${id.hashCode()}" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return java.io.File(java.io.File(cacheDir, "attachments"), "${id.hashCode()}-$safeName")
}

private fun MemohTurn.toChatMessage(messageId: String, running: Boolean = false, cacheDir: java.io.File? = null): ChatMessage {
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
            // Assistant attachment rows ride the tool-image channel: the shared message list
            // already renders toolImages as tappable image cards.
            "attachments" -> MessageSegment(
                type = "tool",
                content = block.text.ifBlank { "Attachments" },
                toolName = "attachments",
                toolCallId = "memoh-attachments-${block.id}",
                toolState = ToolExecutionStates.SUCCEEDED,
                toolImages = block.attachments.mapNotNull { att ->
                    val file = cacheDir?.let { dir -> att.cachedFile(dir) ?: att.materialize(dir) }
                    val path = file?.absolutePath ?: att.url.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    com.newoether.agora.model.ToolImageAttachment(
                        path = path, mimeType = att.mime.ifBlank { "application/octet-stream" },
                        sizeBytes = att.size, sha256 = att.id,
                    )
                },
            ).takeIf { it.toolImages.isNotEmpty() }
            // Unknown notice kinds keep their text but never become tool cards.
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
        images = if (role == "user" && cacheDir != null) {
            attachments.mapNotNull { att ->
                if (att.base64.isBlank() && att.url.isBlank() && att.path.isBlank()) null
                else att.cachedFile(cacheDir)?.absolutePath
                    ?: att.materialize(cacheDir)?.absolutePath
                    ?: att.url.takeIf(String::isNotBlank) ?: ""
            }.filter(String::isNotEmpty)
        } else emptyList(),
        attachmentMeta = if (role == "user" && attachments.isNotEmpty()) {
            // Link every item to its file slot so the shared bubble's ordering and
            // tap-to-open resolution stay driven by metadata, exactly like native rows.
            AttachmentMeta(items = attachments.mapIndexedNotNull { index, att ->
                if (att.base64.isBlank() && att.url.isBlank() && att.path.isBlank()) return@mapIndexedNotNull null
                AttachmentItem(
                    type = when {
                        att.mime.startsWith("image/") -> "image"
                        att.mime.startsWith("video/") -> "video"
                        att.mime.contains("pdf") -> "pdf"
                        else -> "file"
                    },
                    fileName = att.name.takeIf(String::isNotBlank),
                    mimeType = att.mime.takeIf(String::isNotBlank),
                    imageIndex = index,
                    originalUri = att.url.takeIf(String::isNotBlank),
                )
            })
        } else null,
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
