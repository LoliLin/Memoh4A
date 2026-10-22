package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun MemohMemoryDialog(api: MemohApi, botId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var memories by remember(botId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var compactRatio by remember { mutableStateOf("0.8") }
    var decayDays by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var usage by remember { mutableStateOf<JSONObject?>(null) }
    var configReadForbidden by remember(botId) { mutableStateOf(false) }
    var configLoadError by remember(botId) { mutableStateOf("") }
    var graph by remember { mutableStateOf<JSONObject?>(null) }
    var embeddingModelId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    var confirmingDeleteSelected by remember { mutableStateOf(false) }
    var confirmingDeleteId by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            message = ""
            try {
                try {
                    val config = api.memoryConfig()
                    embeddingModelId = config.optString("embedding_model_id")
                    configReadForbidden = false
                    configLoadError = ""
                } catch (e: Exception) {
                    configReadForbidden = e is MemohHttpException && e.status == 403
                    configLoadError = if (configReadForbidden) "" else
                        (e.message ?: "Could not load team Memory configuration")
                }

                val data = api.listMemories(botId)
                val entries = data.optJSONArray("results") ?: data.optJSONArray("memories") ?: JSONArray()
                memories = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
                searching = false
                searchResults = emptyList()
                status = runCatching { api.memoryStatus(botId) }.getOrNull()
                usage = runCatching { api.memoryUsage(botId) }.getOrNull()
            } catch (e: Exception) {
                message = e.message ?: "Could not load Memory entries"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(botId) { load() }
    val displayedMemories = if (searching) searchResults else memories

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxSize(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Memory", style = MaterialTheme.typography.headlineSmall)
                        Text("${displayedMemories.size} ${if (searching) "matches" else "entries"} · bot-scoped: ${botId}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Team Memory configuration", style = MaterialTheme.typography.titleSmall)
                        Text("Choose the embedding model used for semantic memory recall. Leave blank for graph-only recall.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (configReadForbidden) {
                            Text("You can view bot Memory, but your account cannot read the team configuration; its editor is disabled.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (configLoadError.isNotBlank()) {
                            Text(configLoadError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedTextField(
                            value = embeddingModelId,
                            onValueChange = { embeddingModelId = it },
                            label = { Text("Embedding model ID") },
                            singleLine = true,
                            enabled = !loading && !configReadForbidden && configLoadError.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(enabled = !loading && !configReadForbidden && configLoadError.isBlank(), onClick = {
                            scope.launch {
                                loading = true
                                message = ""
                                try {
                                    val config = api.updateMemoryConfig(embeddingModelId)
                                    embeddingModelId = config.optString("embedding_model_id")
                                    configReadForbidden = false
                                    configLoadError = ""
                                    message = "Team Memory configuration saved."
                                } catch (e: Exception) {
                                    message = e.message ?: "Could not save team Memory configuration"
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text("Save team configuration") }
                    }
                }
                if (status != null || usage != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Memory health", style = MaterialTheme.typography.titleSmall)
                            Text("Mode: ${status?.optString("memory_mode").orEmpty().ifBlank { "—" }} · " +
                                "Provider: ${status?.optString("provider_type").orEmpty().ifBlank { "—" }}")
                            Text("Sources: ${status?.optInt("source_count", 0) ?: 0} · " +
                                "Indexed: ${status?.optInt("indexed_count", 0) ?: 0} · " +
                                "Storage: ${usage?.optLong("estimated_storage_bytes", 0L) ?: 0L} bytes " +
                                "(${usage?.optInt("count", 0) ?: 0} entries)")
                            if (status?.optBoolean("degraded") == true) {
                                Text("Search index is behind stored memories.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Search memories") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(enabled = query.isNotBlank() && !loading, onClick = {
                            scope.launch {
                                loading = true; message = ""
                                try {
                                    val data = api.searchMemories(botId, query)
                                    val hits = data.optJSONArray("results") ?: JSONArray()
                                    searchResults = (0 until hits.length()).mapNotNull { hits.optJSONObject(it) }
                                    searching = true
                                } catch (e: Exception) { message = e.message ?: "Search failed" }
                                finally { loading = false }
                            }
                        }) { Text("Search") }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searching) TextButton(onClick = { searching = false; searchResults = emptyList() }) {
                        Text("Show all")
                    }
                    Button(onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                if (editId == null) api.addMemory(botId, draft) else api.updateMemory(botId, editId!!, draft)
                                draft = ""; editId = null; load()
                            } catch (e: Exception) { message = e.message ?: "Could not save memory"; loading = false }
                        }
                    }, enabled = draft.isNotBlank() && !loading) { Text(if (editId == null) "Add memory" else "Save edit") }
                    TextButton(onClick = { confirmingDeleteAll = true }, enabled = memories.isNotEmpty() && !loading) {
                        Text("Delete all", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try { graph = api.memoryGraph(botId) }
                            catch (e: Exception) { message = e.message ?: "Could not load graph" }
                            finally { loading = false }
                        }
                    }, enabled = !loading) { Text("Graph") }
                }
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = { confirmingDeleteSelected = true }, enabled = !loading) {
                        Text("Delete selected (${selectedIds.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = compactRatio, onValueChange = { compactRatio = it },
                        label = { Text("Keep ratio") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = decayDays, onValueChange = { decayDays = it.filter(Char::isDigit) },
                        label = { Text("Decay days (optional)") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                if (draft.isNotEmpty() || editId != null) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        label = { Text(if (editId == null) "New memory" else "Edit memory") },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        label = { Text("Add a memory") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = ::load, enabled = !loading) { Text("Retry") }
                }
                graph?.let { graphData ->
                    Text("Graph: ${graphData.optJSONArray("nodes")?.length() ?: 0} nodes · " +
                        "${graphData.optJSONArray("edges")?.length() ?: 0} relations", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !loading, onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.ingestMemories(botId)
                                message = "Ingested ${response.optInt("ingested", 0)} files; skipped ${response.optInt("skipped", 0)}."
                                load()
                            }
                            catch (e: Exception) { message = e.message ?: "Ingest failed"; loading = false }
                        }
                    }) { Text("Ingest files") }
                    TextButton(enabled = !loading, onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.rebuildMemories(botId)
                                message = "Restored ${response.optInt("restored_count", 0)} of " +
                                    "${response.optInt("fs_count", 0)} filesystem memories."
                                load()
                            }
                            catch (e: Exception) { message = e.message ?: "Rebuild failed"; loading = false }
                        }
                    }) { Text("Rebuild") }
                    TextButton(enabled = !loading && compactRatio.toDoubleOrNull()?.let { it > 0.0 && it <= 1.0 } == true &&
                        (decayDays.isBlank() || decayDays.toIntOrNull() != null), onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.compactMemories(botId, compactRatio.toDouble(), decayDays.toIntOrNull())
                                message = "Compacted ${response.optInt("before_count", 0)} memories to " +
                                    "${response.optInt("after_count", 0)}."
                                load()
                            } catch (e: Exception) {
                                message = e.message ?: "Compaction failed"
                                loading = false
                            }
                        }
                    }) { Text("Compact") }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (displayedMemories.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(if (searching) "No matching memories" else "No memories yet",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (searching) "Try another search phrase." else "Memories saved by this bot will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = if (searching) ({ searching = false; searchResults = emptyList() }) else ::load) {
                            Text(if (searching) "Show all" else "Refresh")
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(displayedMemories, key = { it.optString("id") }) { item ->
                            val id = item.optString("id")
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = id in selectedIds, onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + id else selectedIds - id
                                        })
                                        Text(item.optString("memory"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("Updated ${item.optString("updated_at").ifBlank { item.optString("created_at") }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        TextButton(onClick = { editId = id; draft = item.optString("memory") }) { Text("Edit") }
                                        TextButton(onClick = { confirmingDeleteId = id }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
    if (confirmingDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteSelected = false },
            title = { Text("Delete selected memories?") },
            text = { Text("This permanently removes ${selectedIds.size} selected memories.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteSelected = false
                    val ids = selectedIds.toList()
                    scope.launch {
                        loading = true; message = ""
                        try {
                            api.deleteMemories(botId, ids)
                            selectedIds = emptySet()
                            load()
                        } catch (e: Exception) {
                            message = e.message ?: "Delete failed"
                            loading = false
                        }
                    }
                }) { Text("Delete selected", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteSelected = false }) { Text("Cancel") } },
        )
    }
    if (confirmingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteId = null },
            title = { Text("Delete this memory?") },
            text = { Text("This permanently removes the selected memory.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = confirmingDeleteId ?: return@TextButton
                    confirmingDeleteId = null
                    scope.launch {
                        loading = true; message = ""
                        try { api.deleteMemory(botId, id); load() }
                        catch (e: Exception) { message = e.message ?: "Delete failed"; loading = false }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteId = null }) { Text("Cancel") } },
        )
    }
    if (confirmingDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteAll = false },
            title = { Text("Delete all memories?") },
            text = { Text("This permanently deletes every memory shared by this bot.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteAll = false
                    scope.launch {
                        loading = true; message = ""
                        try { api.deleteMemories(botId, emptyList()); load() }
                        catch (e: Exception) { message = e.message ?: "Delete failed"; loading = false }
                    }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteAll = false }) { Text("Cancel") } },
        )
    }
}
