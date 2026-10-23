package com.newoether.agora.memoh

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Raw inline bytes retained by one composer draft, across all picker selections. */
internal const val MAX_DRAFT_ATTACHMENT_BYTES = 16L * 1024 * 1024
internal const val MAX_DRAFT_ATTACHMENTS = 10

internal fun remainingAttachmentBytes(
    attachments: List<MemohAttachment>,
    maxBytes: Long = MAX_DRAFT_ATTACHMENT_BYTES,
): Long = attachments.fold(maxBytes) { remaining, attachment ->
    // Locally picked attachments record the actual stream length, never provider metadata.
    (remaining - attachment.size.coerceAtLeast(0)).coerceAtLeast(0)
}

internal fun attachmentDraftError(
    attachments: List<MemohAttachment>,
    maxBytes: Long = MAX_DRAFT_ATTACHMENT_BYTES,
    maxFileBytes: Long = MAX_ATTACHMENT_BYTES,
    maxCount: Int = MAX_DRAFT_ATTACHMENTS,
): String? {
    if (attachments.size > maxCount) return "Each draft allows at most $maxCount attachments."
    var remaining = maxBytes
    for (attachment in attachments) {
        if (attachment.size < 0 || attachment.size > maxFileBytes) {
            return "Each attachment must be at most ${formatAttachmentSize(maxFileBytes)}."
        }
        if (attachment.size > remaining) {
            return "Each draft allows at most ${formatAttachmentSize(maxBytes)} of attachments."
        }
        remaining -= attachment.size
    }
    return null
}

internal fun attachmentPickerError(attachments: List<MemohAttachment>): String? = when {
    attachments.size >= MAX_DRAFT_ATTACHMENTS ->
        "Each draft allows at most $MAX_DRAFT_ATTACHMENTS attachments. Remove an attachment before adding more."
    remainingAttachmentBytes(attachments) == 0L ->
        "The attachment draft is full (${formatAttachmentSize(MAX_DRAFT_ATTACHMENT_BYTES)}). Remove an attachment before adding more."
    else -> attachmentDraftError(attachments)
}

/** Reads only the allowance plus one probe byte, including for unknown-length streams. */
internal fun readAttachmentAtMost(stream: InputStream, limit: Long): ByteArray {
    require(limit in 0..MAX_ATTACHMENT_BYTES)
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(minOf(8 * 1024L, limit + 1).toInt())
    var total = 0L
    while (total <= limit) {
        val count = stream.read(chunk, 0, minOf(chunk.size.toLong(), limit + 1 - total).toInt())
        if (count < 0) break
        if (count == 0) {
            val byte = stream.read()
            if (byte < 0) break
            buffer.write(byte)
            total++
        } else {
            buffer.write(chunk, 0, count)
            total += count
        }
    }
    return buffer.toByteArray()
}

/** Revisions also distinguish leaving and returning to the same bot/chat/login. */
internal data class MemohAttachmentDraftOwner(val revision: Int, val sessionId: String?)

internal val MemohViewModel.attachmentDraftOwner: MemohAttachmentDraftOwner?
    get() = if (signedIn) MemohAttachmentDraftOwner(attachmentDraftRevision, session?.id) else null
