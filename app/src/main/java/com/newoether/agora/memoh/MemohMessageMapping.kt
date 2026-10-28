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
                .toChatMessage(MEMOH_LIVE_MESSAGE_ID, running, cacheDir),
        )
    }
}

internal fun memohTranscriptRows(
    turns: List<MemohTurn>,
    runtimeUsers: List<MemohTurn>,
    pendingText: String?,
    pendingAttachments: List<MemohAttachment>,
    pendingTurnId: String?,
    liveTurnId: String,
    hasLive: Boolean,
    cacheDir: java.io.File?,
): List<ChatMessage> {
    val merged = turns.distinctBy(::memohTurnIdentity).toMutableList()
    fun addUserIfMissing(user: MemohTurn) {
        if (user.turnId.isNotBlank() && merged.any { it.role == "user" && it.turnId == user.turnId }) return
        val answer = merged.indexOfFirst { it.role == "assistant" && user.turnId.isNotBlank() && it.turnId == user.turnId }
        if (answer >= 0) merged.add(answer, user) else merged.add(user)
    }
    runtimeUsers.forEach(::addUserIfMissing)
    // History and runtime users may arrive in different orders; position is server-owned.
    val ordered = memohOrderTurns(merged).toList()
    merged.clear()
    merged.addAll(ordered)
    if (pendingText != null) {
        addUserIfMissing(MemohTurn(
            MEMOH_PENDING_MESSAGE_ID, "user", pendingText,
            attachments = pendingAttachments, turnId = pendingTurnId.orEmpty(),
        ))
    }
    var livePlaced = false
    val projected = merged.mapIndexed { index, turn ->
        if (hasLive && !livePlaced && liveTurnId.isNotBlank() &&
            turn.role == "assistant" && turn.turnId == liveTurnId) {
            livePlaced = true
            memohLiveStub()
        } else {
            val id = if (turn.id == MEMOH_PENDING_MESSAGE_ID) MEMOH_PENDING_MESSAGE_ID else memohMessageId(turn, index)
            turn.toChatMessage(id, cacheDir = cacheDir)
        }
    }
    return if (hasLive && !livePlaced) projected + memohLiveStub() else projected
}

internal fun memohTurnIdentity(turn: MemohTurn): Pair<String, String> =
    turn.role to turn.turnId.ifBlank { turn.id }

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
internal fun MemohAttachment.wireBase64(): String =
    if (base64.startsWith("data:", ignoreCase = true)) base64
    else "data:${mime.ifBlank { "application/octet-stream" }};base64,$base64"

internal fun MemohAttachment.inlineBytes(): ByteArray? {
    val raw = base64.ifBlank { url.takeIf { it.startsWith("data:", ignoreCase = true) }.orEmpty() }
    if (raw.isBlank()) return null
    val payload = if (raw.startsWith("data:", ignoreCase = true)) {
        val comma = raw.indexOf(',')
        if (comma < 0 || !raw.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
        raw.substring(comma + 1)
    } else raw
    if (!payload.matches(Regex("[A-Za-z0-9+/=\\r\\n\\t ]+"))) return null
    return runCatching { java.util.Base64.getMimeDecoder().decode(payload) }.getOrNull()?.takeIf { it.isNotEmpty() }
}

internal fun MemohAttachment.materialize(cacheDir: java.io.File): java.io.File? {
    cachedFile(cacheDir)?.let { return it }
    val bytes = inlineBytes() ?: return null
    val target = cachedTarget(cacheDir)
    return runCatching {
        writeMemohMediaAtomically(target) { it.write(bytes) }
        target
    }.getOrNull()
}

/** Cache lookup never decodes or writes while a message is composing. */
internal fun MemohAttachment.cachedFile(cacheDir: java.io.File): java.io.File? =
    cachedTarget(cacheDir).takeIf { it.isFile && it.length() > 0 }

internal fun MemohAttachment.needsDownload(): Boolean =
    base64.isBlank() && !url.startsWith("data:", ignoreCase = true) &&
        (contentHash.isNotBlank() || path.isNotBlank() || url.isNotBlank())

/** Empty IDs are common in server rows; include the source so equal filenames cannot collide. */
internal fun MemohAttachment.cachedTarget(cacheDir: java.io.File): java.io.File {
    val key = listOf(id, contentHash, botId, path, url, base64).joinToString("\u0000")
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val safeName = name.ifBlank { "attachment" }.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100)
    return java.io.File(java.io.File(cacheDir, "attachments"), "$digest-$safeName")
}

/** One cache slot per content hash, so the same file referenced twice is stored once. */
internal fun MemohAsset.cachedTarget(cacheDir: java.io.File): java.io.File {
    val safeName = name.ifBlank { "asset" }.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100)
    return java.io.File(java.io.File(cacheDir, "assets"), "${contentHash.take(64)}-$safeName")
}

internal fun MemohAsset.cachedFile(cacheDir: java.io.File): java.io.File? =
    cachedTarget(cacheDir).takeIf { it.isFile && it.length() > 0 }

/**
 * The markdown link each asset answers, when the reply renders it as an inline image.
 *
 * A Bot cannot name a hash in its text, so the association is positional: the server stores
 * assets in the order the references appear. Pairing by ordinal is what lets the bubble put the
 * downloaded file where the `![](...)` stands instead of dropping the picture.
 *
 * [renderedText] must be the exact markdown the bubble will show, not the raw turn text: a reply
 * may carry its body in blocks, and pairing against a different string shifts every ordinal.
 */
internal fun markdownImageLinks(assets: List<MemohAsset>, renderedText: String): Map<String, MemohAsset> {
    val images = assets.filter { it.isImage }
    if (images.isEmpty()) return emptyMap()
    val targets = markdownImageTargets(renderedText)
    if (targets.isEmpty()) return emptyMap()
    val linked = linkedMapOf<String, MemohAsset>()
    images.forEachIndexed { position, asset ->
        // Prefer the asset's own ordinal; fall back to arrival order if the server omitted it.
        val target = targets.getOrNull(asset.ordinal) ?: targets.getOrNull(position)
        if (target != null) linked[target] = asset
    }
    return linked
}

private fun MemohTurn.toChatMessage(messageId: String, running: Boolean = false, cacheDir: java.io.File? = null): ChatMessage {
    val displayBlocks = blocks + if (role != "user" && attachments.isNotEmpty())
        listOf(MemohBlock(-1, "attachments", "", attachments = attachments)) else emptyList()
    val segments = displayBlocks.mapNotNull { block ->
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
                toolImages = block.attachments.filter { it.type == "image" || it.mime.startsWith("image/") }.mapNotNull { att ->
                    val file = cacheDir?.let { dir -> att.cachedFile(dir) }
                    // Coil can only open a real file or an absolute URL. A server-relative path
                    // like `/bots/{id}/media/{hash}` is not something it can resolve, and handing
                    // it over produced a card with a permanently empty picture slot. Until the
                    // scoped download lands there is simply nothing to show yet.
                    val path = file?.absolutePath
                        ?: att.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
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
        if (text.isNotBlank() && mapped.none { it.type == "answer" }) {
            listOf(MessageSegment("answer", text)) + mapped
        } else {
            mapped
        }
    }
    // The markdown the bubble will actually render, used both as the message text and as the
    // string the image references are paired against.
    val bodyText = if (role == "user") text
        else segments.filter { it.type == "answer" }.joinToString("\n") { it.content }
    return ChatMessage(
        id = messageId,
        text = bodyText,
        images = if (role == "user" && cacheDir != null) {
            attachments.map { att ->
                if (att.base64.isBlank() && att.url.isBlank() && att.path.isBlank() && att.contentHash.isBlank()) ""
                else att.cachedFile(cacheDir)?.absolutePath
                    ?: att.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: ""
            }
        } else emptyList(),
        attachmentMeta = if (role == "user" && attachments.isNotEmpty()) {
            // Link every item to its file slot so the shared bubble's ordering and
            // tap-to-open resolution stay driven by metadata, exactly like native rows.
            AttachmentMeta(items = attachments.mapIndexedNotNull { index, att ->
                if (att.base64.isBlank() && att.url.isBlank() && att.path.isBlank() && att.contentHash.isBlank()) return@mapIndexedNotNull null
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
        // A Bot writes `![alt](/data/x.png)` and the server registers the file it found there as
        // an asset keyed by content_hash. Without this map the markdown renderer has no local
        // file for the link, so it draws nothing at all and the picture silently disappears.
        markdownImages = if (cacheDir == null) emptyMap() else
            markdownImageLinks(assets, bodyText).mapValues { (_, asset) ->
                val file = asset.cachedFile(cacheDir)
                val attachment = file?.let {
                    com.newoether.agora.model.ToolImageAttachment(
                        path = it.absolutePath,
                        mimeType = asset.mime.ifBlank { "application/octet-stream" },
                        sizeBytes = asset.sizeBytes,
                        sha256 = asset.contentHash,
                    )
                }
                com.newoether.agora.model.MarkdownImage(attachment, failed = false)
            },
        status = when {
            running && blocks.any { it.type == "tool" && it.toolRunning } -> MessageStatus.TOOL_CALLING
            running && segments.none { it.type == "answer" && it.content.isNotBlank() } -> MessageStatus.THINKING
            running -> MessageStatus.SENDING
            blocks.any { it.type == "error" } -> MessageStatus.ERROR
            else -> MessageStatus.SUCCESS
        },
    )
}
