package com.newoether.agora.memoh

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
import java.io.InputStream
import java.util.UUID

/**
 * Attachments travel inside the message payload as base64 data URLs, so the whole
 * file is held in memory twice (raw bytes + base64 string, which is ~4/3 larger)
 * before the request body adds a third copy. A picked video used to be read in
 * unconditionally and took the process down with an OutOfMemoryError.
 *
 * Cap it instead: anything larger is refused with an explicit message rather than
 * crashing or being silently dropped.
 */
internal const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024

/** Thrown when a picked file is too large to inline into a message. */
internal class AttachmentTooLargeException(val name: String, val bytes: Long) :
    IllegalArgumentException(
        "$name is ${formatAttachmentSize(bytes)}; attachments are limited to " +
            formatAttachmentSize(MAX_ATTACHMENT_BYTES)
    )

/**
 * Reads a picked document into an inline attachment.
 *
 * Returns null only when the file cannot be read at all; a file that is readable
 * but too large throws [AttachmentTooLargeException] so the caller can tell the
 * user why nothing was attached.
 */
internal suspend fun resolveAttachmentFromUri(context: Context, uri: Uri): MemohAttachment? =
    withContext(Dispatchers.IO) {
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
        if (declaredSize > MAX_ATTACHMENT_BYTES) {
            throw AttachmentTooLargeException(displayName, declaredSize)
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val type = if (mime.startsWith("image/")) "image" else "file"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readAtMost(stream, MAX_ATTACHMENT_BYTES)
            }
        }.getOrElse { return@withContext null } ?: return@withContext null
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            // The provider under-reported its size; the cap still applies.
            throw AttachmentTooLargeException(displayName, bytes.size.toLong())
        }
        // `base64` is the raw payload field in conversation.UIAttachment; the
        // `url` field is for server-side references (path/storage_key). Putting
        // the same data URL in both doubled the bytes on the wire for no gain.
        MemohAttachment(
            id = UUID.randomUUID().toString(),
            name = displayName,
            mime = mime,
            type = type,
            size = if (declaredSize > 0) declaredSize else bytes.size.toLong(),
            url = "",
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

/**
 * Reads up to [limit] bytes, then probes a single extra byte.
 *
 * Returns at most `limit + 1` bytes so the caller can detect an oversized stream
 * without buffering the entire file first.
 */
private fun readAtMost(stream: InputStream, limit: Long): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0L
    while (total <= limit) {
        val read = stream.read(chunk)
        if (read < 0) break
        val allowed = minOf(read.toLong(), limit + 1 - total).toInt()
        if (allowed <= 0) break
        buffer.write(chunk, 0, allowed)
        total += allowed
        if (total > limit) break
    }
    return buffer.toByteArray()
}

/**
 * Resolves the loadable image source for an attachment.
 *
 * `url` holds a server reference (http/data URL) when present. Otherwise the
 * payload arrives as a bare base64 string in `base64`, which an image loader
 * cannot parse on its own — rebuild the data URL from the attachment's mime type.
 * Returns null when there is nothing renderable.
 */
internal fun MemohAttachment.imageSource(): String? {
    if (url.isNotBlank()) return url
    if (base64.isBlank()) return null
    // Tolerate an older peer that sent a full data URL in `base64`.
    if (base64.startsWith("data:", ignoreCase = true)) return base64
    val safeMime = mime.ifBlank { "application/octet-stream" }
    return "data:$safeMime;base64,$base64"
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
    onAttachmentsPicked: (List<MemohAttachment>) -> Unit,
    onAttachmentError: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            reading = true
            scope.launch {
                val accepted = mutableListOf<MemohAttachment>()
                val problems = mutableListOf<String>()
                for (uri in uris) {
                    try {
                        resolveAttachmentFromUri(context, uri)?.let { accepted += it }
                            ?: problems.add("Could not read a selected file.")
                    } catch (e: AttachmentTooLargeException) {
                        problems.add(e.message ?: "A selected file is too large.")
                    }
                }
                if (accepted.isNotEmpty()) onAttachmentsPicked(accepted)
                if (problems.isNotEmpty()) onAttachmentError(problems.joinToString("\n"))
                reading = false
            }
        }
    }

    IconButton(
        onClick = { launcher.launch("*/*") },
        enabled = enabled && !reading
    ) {
        if (reading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.AttachFile, contentDescription = "Add attachment")
        }
    }
}

@Composable
internal fun AttachmentDraftStrip(
    attachments: List<MemohAttachment>,
    onRemove: (MemohAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { att ->
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
                    if (att.type == "image" && att.imageSource() != null) {
                        AsyncImage(
                            model = att.imageSource(),
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
                            contentDescription = "Remove",
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
    if (attachments.isEmpty()) return
    var previewAttachment by remember { mutableStateOf<MemohAttachment?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { att ->
            if (att.type == "image" && att.imageSource() != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { previewAttachment = att }
                ) {
                    AsyncImage(
                        model = att.imageSource(),
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
                    model = att.imageSource(),
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
                        contentDescription = "Close preview",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}
