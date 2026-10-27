package com.newoether.agora.memoh

import com.newoether.agora.R

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
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
    val uiResources = LocalContext.current.resources
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var directory by remember(botId) { mutableStateOf("/data") }
    var entries by remember(botId) { mutableStateOf<List<MemohFile>>(emptyList()) }
    var opened by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var fileContent by remember(botId) { mutableStateOf("") }
    var revision by remember(botId) { mutableStateOf("") }
    var loading by remember(botId) { mutableStateOf(true) }
    var saving by remember(botId) { mutableStateOf(false) }
    var message by remember(botId) { mutableStateOf("") }
    var messageIsError by remember(botId) { mutableStateOf(true) }
    var reloadKey by remember(botId) { mutableStateOf(0) }
    // Pending entry actions: rename, delete, and the folder-name prompt.
    var entryMenu by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var renameTarget by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var renameDraft by remember(botId) { mutableStateOf("") }
    var deleteTarget by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var newFolder by remember(botId) { mutableStateOf(false) }
    var folderDraft by remember(botId) { mutableStateOf("") }
    /** Entry queued for the system save picker once the user confirms. */
    var downloadTarget by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var extracting by remember(botId) { mutableStateOf<MemohFile?>(null) }
    var extractError by remember(botId) { mutableStateOf("") }
    var archiveTarget by remember(botId) { mutableStateOf<String?>(null) }
    val currentFolder = directory

    LaunchedEffect(botId, directory, reloadKey) {
        loading = true
        try {
            val result = api.listFiles(botId, directory)
            if (currentFolder == directory) entries = result
        } catch (e: Exception) {
            if (currentFolder == directory) message = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_list_files)
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
            message = uiResources.getString(R.string.memoh_ui_invalid_file_name)
        } else if (entries.any { it.name == name }) {
            message = uiResources.getString(R.string.memoh_ui_a_file_with_this_name_already_exists_rename_it_before_uploading)
        } else {
            val path = folder.trimEnd('/') + "/" + name
            scope.launch {
                saving = true
                try {
                    api.uploadFile(botId, path, uri, resolver, name)
                    if (directory == folder) {
                        message = uiResources.getString(R.string.memoh_ui_uploaded_1_s, name)
                        reloadKey++
                    }
                } catch (e: Exception) {
                    if (directory == folder) message = e.message ?: uiResources.getString(R.string.memoh_ui_upload_failed)
                } finally {
                    saving = false
                }
            }
        }
    }

    // System save pickers must be created at a stable composition position, so
    // both live here rather than inside the toolbar Row.
    val saveArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        val target = archiveTarget
        archiveTarget = null
        if (uri != null && target != null) {
            val folder = directory
            scope.launch {
                saving = true
                try {
                    api.downloadArchive(botId, listOf(target), uri, resolver)
                    if (directory == folder) {
                        message = uiResources.getString(R.string.memoh_ui_folder_archive_saved)
                        messageIsError = false
                    }
                } catch (e: Exception) {
                    if (directory == folder) {
                        message = e.message ?: uiResources.getString(R.string.memoh_ui_archive_download_failed)
                        messageIsError = true
                    }
                } finally { saving = false }
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val entry = downloadTarget
            if (entry != null) {
                scope.launch {
                    saving = true
                    try {
                        api.downloadFile(botId, entry.path, uri, resolver)
                        message = uiResources.getString(R.string.memoh_ui_1_s_saved, entry.name)
                        messageIsError = false
                    } catch (e: Exception) {
                        message = e.message ?: uiResources.getString(R.string.memoh_ui_download_failed)
                        messageIsError = true
                    } finally {
                        saving = false
                        downloadTarget = null
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large) {
            Column(
                // The file editor and rename field live inside this sheet; without IME padding
                // the keyboard covers them.
                Modifier.imePadding().navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiResources.getString(R.string.memoh_ui_workspace_files), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_close)) }
                }
                Text(
                    opened?.path ?: directory,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.isNotBlank()) Text(message,
                    color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                if (opened == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { if (directory != "/data") directory = parent(directory) },
                            enabled = directory != "/data") { Text(uiResources.getString(R.string.memoh_ui_up)) }
                        TextButton(onClick = { reloadKey++ }) { Text(uiResources.getString(R.string.memoh_ui_refresh)) }
                        TextButton(onClick = { picker.launch("*/*") }, enabled = !saving) { Text(uiResources.getString(R.string.memoh_ui_upload)) }
                        TextButton(onClick = { folderDraft = ""; newFolder = true },
                            enabled = !saving) { Text(uiResources.getString(R.string.memoh_ui_new_folder)) }
                        TextButton(onClick = {
                            archiveTarget = directory
                            saveArchive.launch(
                                "${directory.trimEnd('/').substringAfterLast('/').ifBlank { "workspace" }}.tar.gz"
                            )
                        }, enabled = !saving && entries.isNotEmpty()) { Text(uiResources.getString(R.string.memoh_ui_download_folder)) }
                    }
                    if (loading) CircularProgressIndicator()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (entries.isEmpty() && !loading && message.isBlank()) {
                            item {
                                Text(
                                    uiResources.getString(R.string.memoh_ui_this_folder_is_empty),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(entries, key = { it.path }) { file ->
                            androidx.compose.material3.Surface(
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                                    .combinedClickable(
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
                                                        if (opened?.path == file.path) {
                                                            message = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_read_file)
                                                            messageIsError = true
                                                        }
                                                    } finally { loading = false }
                                                }
                                            }
                                        },
                                        onLongClick = { entryMenu = file },
                                    ),
                            ) {
                                Text((if (file.isDirectory) "📁 " else "📄 ") + file.name,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { opened = null; message = "" }) { Text(uiResources.getString(R.string.memoh_ui_back_to_files)) }
                    if (loading) CircularProgressIndicator()
                    OutlinedTextField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text(uiResources.getString(R.string.memoh_ui_file_contents)) },
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
                                        message = uiResources.getString(R.string.memoh_ui_saved)
                                    }
                                } catch (e: Exception) {
                                    if (opened?.path == file.path) message =
                                        e.message ?: uiResources.getString(R.string.memoh_ui_save_failed_reload_to_resolve_the_conflict)
                                } finally { saving = false }
                            }
                        },
                    ) { Text(if (saving) uiResources.getString(R.string.memoh_ui_saving) else uiResources.getString(R.string.memoh_ui_save_with_revision_check)) }
                }
            }
        }
    }

    // ---- Entry actions ----------------------------------------------------

    val menuEntry = entryMenu
    if (menuEntry != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { entryMenu = null },
            title = { Text(menuEntry.name) },
            text = { Text(uiResources.getString(R.string.memoh_ui_choose_an_action_for_this_1_s, if (menuEntry.isDirectory) uiResources.getString(R.string.memoh_ui_folder) else uiResources.getString(R.string.memoh_ui_file))) },
            confirmButton = {
                TextButton(onClick = {
                    entryMenu = null
                    renameTarget = menuEntry
                    renameDraft = menuEntry.name
                }) { Text(uiResources.getString(R.string.memoh_ui_rename)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        entryMenu = null
                        if (menuEntry.isDirectory) {
                            archiveTarget = menuEntry.path
                            saveArchive.launch("${menuEntry.name}.tar.gz")
                        } else {
                            downloadTarget = menuEntry
                            saveFile.launch(menuEntry.name)
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_download)) }
                    // Only offer extraction for something that looks like an archive.
                    if (!menuEntry.isDirectory && isArchiveName(menuEntry.name)) {
                        TextButton(onClick = {
                            entryMenu = null
                            extracting = menuEntry
                            extractError = ""
                        }) { Text(uiResources.getString(R.string.memoh_ui_extract)) }
                    }
                    TextButton(onClick = { entryMenu = null; deleteTarget = menuEntry }) {
                        Text(uiResources.getString(R.string.memoh_ui_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    val renaming = renameTarget
    if (renaming != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_rename)) },
            text = {
                OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_new_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && renameDraft.isNotBlank() &&
                        renameDraft != renaming.name && '/' !in renameDraft && '\\' !in renameDraft,
                    onClick = {
                        val parentPath = renaming.path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
                        val target = parentPath.trimEnd('/') + "/" + renameDraft.trim()
                        scope.launch {
                            saving = true
                            try {
                                api.renameEntry(botId, renaming.path, target)
                                renameTarget = null
                                message = uiResources.getString(R.string.memoh_ui_renamed_to_1_s, renameDraft.trim())
                                messageIsError = false
                                reloadKey++
                            } catch (e: Exception) {
                                message = e.message ?: uiResources.getString(R.string.memoh_ui_rename_failed)
                                messageIsError = true
                            } finally { saving = false }
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_rename)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }

    val deleting = deleteTarget
    if (deleting != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_1_s, if (deleting.isDirectory) uiResources.getString(R.string.memoh_ui_folder) else uiResources.getString(R.string.memoh_ui_file))) },
            text = {
                Text(
                    uiResources.getString(R.string.memoh_ui_permanently_deletes_1_s_from_the_bot_workspace, deleting.name) +
                        if (deleting.isDirectory) uiResources.getString(R.string.memoh_ui_including_everything_inside_it) else "."
                )
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        try {
                            api.deleteEntry(botId, deleting.path, recursive = deleting.isDirectory)
                            deleteTarget = null
                            message = uiResources.getString(R.string.memoh_ui_1_s_deleted, deleting.name)
                            messageIsError = false
                            reloadKey++
                        } catch (e: Exception) {
                            message = e.message ?: uiResources.getString(R.string.memoh_ui_delete_failed)
                            messageIsError = true
                        } finally { saving = false }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }

    val archive = extracting
    if (archive != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!saving) extracting = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_extract_1_s, archive.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiResources.getString(R.string.memoh_ui_memoh_extracts_this_archive_into_a_sibling_folder_named_after_it_an_exis))
                    if (extractError.isNotBlank()) Text(extractError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val destination = api.extractArchive(botId, archive.path)
                            extracting = null
                            message = uiResources.getString(R.string.memoh_ui_extracted_to_1_s, destination)
                            messageIsError = false
                            reloadKey++
                        } catch (e: Exception) {
                            extractError = e.message ?: uiResources.getString(R.string.memoh_ui_extract_failed)
                            message = extractError
                            messageIsError = true
                        } finally { saving = false }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_extract)) }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { extracting = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }

    if (newFolder) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_new_folder)) },
            text = {
                OutlinedTextField(value = folderDraft, onValueChange = { folderDraft = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_folder_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && folderDraft.isNotBlank() &&
                        '/' !in folderDraft && '\\' !in folderDraft,
                    onClick = {
                        val folder = directory
                        val target = folder.trimEnd('/') + "/" + folderDraft.trim()
                        scope.launch {
                            saving = true
                            try {
                                api.makeDirectory(botId, target)
                                newFolder = false
                                message = uiResources.getString(R.string.memoh_ui_created_1_s, folderDraft.trim())
                                messageIsError = false
                                if (directory == folder) reloadKey++
                            } catch (e: Exception) {
                                message = e.message ?: uiResources.getString(R.string.memoh_ui_could_not_create_folder)
                                messageIsError = true
                            } finally { saving = false }
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
}

/**
 * Whether a filename looks like an archive the server can extract.
 *
 * Purely a UI affordance — the server still validates the real format, so a
 * false positive only surfaces as an error after the user confirms.
 */
internal fun isArchiveName(name: String): Boolean {
    val lower = name.lowercase()
    return ARCHIVE_SUFFIXES.any { lower.endsWith(it) }
}

private val ARCHIVE_SUFFIXES = listOf(
    ".tar.gz", ".tgz", ".zip",
)
