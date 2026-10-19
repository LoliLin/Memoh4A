package com.newoether.agora.memoh

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Read and edit Memoh's server-owned /data tree. Never persist remote copies in
 * Agora's Room store or overwrite a file without the server's revision fence.
 */
@Composable
internal fun MemohWorkspaceDialog(
    api: MemohApi,
    botId: String,
    onDismiss: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var directory by remember(botId) { mutableStateOf("/data") }
    var entries by remember(botId) { mutableStateOf<List<MemohFile>>(emptyList()) }
    var opened by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var fileContent by remember(botId) { mutableStateOf("") }
    var revision by remember(botId) { mutableStateOf("") }
    var loading by remember(botId) { mutableStateOf(false) }
    var saving by remember(botId) { mutableStateOf(false) }
    var message by remember(botId) { mutableStateOf("") }
    var reloadKey by remember(botId) { mutableStateOf(0) }
    val currentFolder = directory

    LaunchedEffect(botId, directory, reloadKey) {
        loading = true
        message = ""
        try {
            val result = api.listFiles(botId, directory)
            if (currentFolder == directory) entries = result
        } catch (e: Exception) {
            if (currentFolder == directory) message = e.message ?: "Unable to list files"
        } finally {
            if (currentFolder == directory) loading = false
        }
    }

    fun parent(path: String): String =
        path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val folder = directory
        val rawName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty()
        val name = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) {
            message = "Invalid file name"
        } else if (entries.any { it.name == name }) {
            message = "A file with this name already exists. Rename it before uploading."
        } else {
            val path = folder.trimEnd('/') + "/" + name
            scope.launch {
                saving = true
                try {
                    api.uploadFile(botId, path, uri, resolver, name)
                    if (directory == folder) {
                        message = "Uploaded $name"
                        reloadKey++
                    }
                } catch (e: Exception) {
                    if (directory == folder) message = e.message ?: "Upload failed"
                } finally {
                    saving = false
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workspace Files", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(opened?.path ?: directory, style = MaterialTheme.typography.bodySmall)
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                if (opened == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { if (directory != "/data") directory = parent(directory) },
                            enabled = directory != "/data") { Text("Up") }
                        TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
                        TextButton(onClick = { picker.launch("*/*") }, enabled = !saving) { Text("Upload") }
                    }
                    if (loading) CircularProgressIndicator()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(entries, key = { it.path }) { file ->
                            TextButton(
                                onClick = {
                                    if (file.isDirectory) directory = file.path else {
                                        opened = file
                                        fileContent = ""
                                        revision = ""
                                        scope.launch {
                                            loading = true
                                            message = ""
                                            try {
                                                val result = api.readFile(botId, file.path)
                                                if (opened?.path == file.path) {
                                                    fileContent = result.text
                                                    revision = result.revision
                                                }
                                            } catch (e: Exception) {
                                                if (opened?.path == file.path)
                                                    message = e.message ?: "Unable to read file"
                                            } finally { loading = false }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text((if (file.isDirectory) "📁 " else "📄 ") + file.name,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { opened = null; message = "" }) { Text("Back to Files") }
                    if (loading) CircularProgressIndicator()
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("File Contents") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Button(
                        enabled = !saving && !loading && revision.isNotBlank(),
                        onClick = {
                            val file = opened ?: return@Button
                            val previous = revision
                            val text = fileContent
                            scope.launch {
                                saving = true
                                try {
                                    val newRevision = api.writeFile(botId, file.path, text, previous)
                                    if (opened?.path == file.path) {
                                        revision = newRevision
                                        message = "Saved"
                                    }
                                } catch (e: Exception) {
                                    if (opened?.path == file.path) message =
                                        e.message ?: "Save failed — reload to resolve the conflict"
                                } finally { saving = false }
                            }
                        },
                    ) { Text(if (saving) "Saving…" else "Save with Revision Check") }
                }
            }
        }
    }
}
