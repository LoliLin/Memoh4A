package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Attachments travel inside the message payload as raw base64 strings, so the whole
 * file is held in memory twice (raw bytes + base64 string, which is ~4/3 larger)
 * before the request body adds a third copy. A picked video used to be read in
 * unconditionally and took the process down with an OutOfMemoryError.
 *
 * Cap it instead: anything larger is refused with an explicit message rather than
 * crashing or being silently dropped.
 */
internal const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024

/** Thrown when a picked file is too large to inline into a message. */
internal class AttachmentTooLargeException(val name: String, val bytes: Long, limit: Long = MAX_ATTACHMENT_BYTES) :
    IllegalArgumentException(
        "$name exceeds the remaining attachment allowance of " +
            (if (limit == 0L) "0 B" else formatAttachmentSize(limit)) +
            ". Each draft is limited to ${formatAttachmentSize(MAX_DRAFT_ATTACHMENT_BYTES)}."
    )

/**
 * Reads a picked document into an inline attachment.
 *
 * Returns null only when the file cannot be read at all; a file that is readable
 * but too large throws [AttachmentTooLargeException] so the caller can tell the
 * user why nothing was attached.
 */
internal suspend fun resolveAttachmentFromUri(
    context: Context,
    uri: Uri,
    remainingBytes: Long = MAX_DRAFT_ATTACHMENT_BYTES,
): MemohAttachment? =
    withContext(Dispatchers.IO) {
        val limit = minOf(MAX_ATTACHMENT_BYTES, remainingBytes.coerceAtLeast(0))
        var displayName = "attachment"
        var declaredSize = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex != -1) declaredSize = cursor.getLong(sizeIndex)
            }
        }
        // Reject on the provider's declared size first so an oversized file is
        // never read into memory at all.
        if (declaredSize > limit) {
            throw AttachmentTooLargeException(displayName, declaredSize, limit)
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val type = if (mime.startsWith("image/")) "image" else "file"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readAttachmentAtMost(stream, limit)
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return@withContext null
        } ?: return@withContext null
        if (bytes.size > limit) {
            // The provider under-reported its size; the remaining draft cap still applies.
            throw AttachmentTooLargeException(displayName, bytes.size.toLong(), limit)
        }
        // `base64` is the raw payload field in conversation.UIAttachment; the
        // `url` field is for server-side references (path/storage_key). Putting
        // the same data URL in both doubled the bytes on the wire for no gain.
        MemohAttachment(
            id = UUID.randomUUID().toString(),
            name = displayName,
            mime = mime,
            type = type,
            size = bytes.size.toLong(),
            url = "",
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

/**
 * Resolves a server URL or decodes inline base64 into bytes that Coil can load.
 * Both `url` and `base64` may contain a legacy base64 data URL.
 * Returns null for missing or malformed inline data; Coil handles image decoding.
 */
internal fun MemohAttachment.imageSource(): Any? {
    val source = url.takeIf { it.isNotBlank() } ?: base64.takeIf { it.isNotBlank() } ?: return null
    val payload = if (source.startsWith("data:", ignoreCase = true)) {
        val comma = source.indexOf(',')
        if (comma < 0 || !source.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
        source.substring(comma + 1)
    } else {
        if (url.isNotBlank()) return url
        source
    }
    // Android's decoder skips unknown characters; reject corrupt input explicitly.
    if (!payload.matches(Regex("[A-Za-z0-9+/=\\r\\n\\t ]+"))) return null
    return runCatching { Base64.decode(payload, Base64.DEFAULT) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

internal fun formatAttachmentSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}

@Composable
internal fun AttachmentPickerButton(
    enabled: Boolean,
    currentOwner: () -> MemohAttachmentDraftOwner?,
    currentAttachments: () -> List<MemohAttachment>,
    onAttachmentsPicked: (List<MemohAttachment>) -> Unit,
    onAttachmentError: (String) -> Unit = {},
) {
    val uiResources = LocalContext.current.resources
    val context = androidx.compose.ui.platform.LocalContext.current
    var reading by remember { mutableStateOf(false) }
    var launchOwner by remember { mutableStateOf<MemohAttachmentDraftOwner?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ownerNow by rememberUpdatedState(currentOwner)
    val attachmentsNow by rememberUpdatedState(currentAttachments)
    val onPickedNow by rememberUpdatedState(onAttachmentsPicked)
    val onErrorNow by rememberUpdatedState(onAttachmentError)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val owner = launchOwner
        launchOwner = null
        if (owner == null || owner != ownerNow() || uris.isEmpty()) {
            reading = false
        } else {
            scope.launch {
                try {
                    for (uri in uris) {
                        if (owner != ownerNow()) break
                        val draft = attachmentsNow()
                        val rejection = attachmentPickerError(draft)
                        if (rejection != null) {
                            onErrorNow(rejection)
                            break
                        }
                        val remaining = remainingAttachmentBytes(draft)
                        try {
                            val attachment = resolveAttachmentFromUri(context, uri, remaining)
                            if (owner != ownerNow()) break
                            if (attachment == null) {
                                onErrorNow(uiResources.getString(R.string.memoh_ui_could_not_read_a_selected_file))
                            } else if (attachmentPickerError(attachmentsNow()) == null &&
                                attachmentDraftError(attachmentsNow() + attachment) == null) {
                                // Publish each file immediately: the next read uses the live draft budget.
                                onPickedNow(listOf(attachment))
                            } else {
                                onErrorNow(uiResources.getString(R.string.memoh_ui_the_selected_file_no_longer_fits_in_the_attachment_draft))
                            }
                        } catch (e: AttachmentTooLargeException) {
                            if (owner != ownerNow()) break
                            onErrorNow(e.message ?: uiResources.getString(R.string.memoh_ui_a_selected_file_is_too_large))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // A provider may fail while answering name/type queries, before a
                            // stream exists; report that as one unreadable file, not a crash.
                            if (owner != ownerNow()) break
                            onErrorNow(uiResources.getString(R.string.memoh_ui_could_not_read_a_selected_file_1_s, e.message ?: uiResources.getString(R.string.memoh_ui_provider_error)))
                        }
                    }
                } finally {
                    reading = false
                }
            }
        }
    }

    IconButton(
        onClick = {
            if (!reading) {
                val owner = ownerNow()
                if (owner != null) {
                    val rejection = attachmentPickerError(attachmentsNow())
                    if (rejection != null) {
                        onErrorNow(rejection)
                        return@IconButton
                    }
                    launchOwner = owner
                    reading = true
                    launcher.launch("*/*")
                }
            }
        },
        enabled = enabled && !reading
    ) {
        if (reading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.AttachFile, contentDescription = uiResources.getString(R.string.memoh_ui_add_attachment))
        }
    }
}

@Composable
internal fun AttachmentDraftStrip(
    attachments: List<MemohAttachment>,
    onRemove: (MemohAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiResources = LocalContext.current.resources
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { att ->
            val imageSource = remember(att) { if (att.type == "image") att.imageSource() else null }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (imageSource != null) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = att.name,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (att.type == "image") Icons.Default.Image else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        Text(
                            text = att.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (att.size > 0) {
                            Text(
                                text = formatAttachmentSize(att.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onRemove(att) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = uiResources.getString(R.string.memoh_ui_remove),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TurnAttachmentsView(
    attachments: List<MemohAttachment>,
    modifier: Modifier = Modifier,
) {
    val uiResources = LocalContext.current.resources
    if (attachments.isEmpty()) return
    var previewAttachment by remember { mutableStateOf<MemohAttachment?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { att ->
            val imageSource = remember(att) { if (att.type == "image") att.imageSource() else null }
            if (imageSource != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { previewAttachment = att }
                ) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = att.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = att.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (att.size > 0) {
                                Text(
                                    text = formatAttachmentSize(att.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewAttachment?.let { att ->
        val imageSource = remember(att) { att.imageSource() }
        if (imageSource == null) return@let
        Dialog(
            onDismissRequest = { previewAttachment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f))
                    .clickable { previewAttachment = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageSource,
                    contentDescription = att.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewAttachment = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = uiResources.getString(R.string.memoh_ui_close_preview),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}
