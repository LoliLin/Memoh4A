package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun MemohMemoryDialog(api: MemohApi, botId: String, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
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
                        (e.message ?: uiResources.getString(R.string.memoh_ui_could_not_load_team_memory_configuration))
                }

                val data = api.listMemories(botId)
                val entries = data.optJSONArray("results") ?: data.optJSONArray("memories") ?: JSONArray()
                memories = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
                searching = false
                searchResults = emptyList()
                status = runCatching { api.memoryStatus(botId) }.getOrNull()
                usage = runCatching { api.memoryUsage(botId) }.getOrNull()
            } catch (e: Exception) {
                message = e.message ?: uiResources.getString(R.string.memoh_ui_could_not_load_memory_entries)
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(botId) { load() }
    val displayedMemories = if (searching) searchResults else memories

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxSize(0.92f).imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiResources.getString(R.string.memoh_ui_memory), style = MaterialTheme.typography.headlineSmall)
                        Text(uiResources.getString(R.string.memoh_ui_1_s_2_s_bot_scoped_3_s, displayedMemories.size, if (searching) uiResources.getString(R.string.memoh_ui_matches) else uiResources.getString(R.string.memoh_ui_entries), botId),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_close)) }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiResources.getString(R.string.memoh_ui_team_memory_configuration), style = MaterialTheme.typography.titleSmall)
                        Text(uiResources.getString(R.string.memoh_ui_choose_the_embedding_model_used_for_semantic_memory_recall_leave_blank_f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (configReadForbidden) {
                            Text(uiResources.getString(R.string.memoh_ui_you_can_view_bot_memory_but_your_account_cannot_read_the_team_configurat),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (configLoadError.isNotBlank()) {
                            Text(
                                configLoadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        MemohResourceField(
                            api, "/models", uiResources.getString(R.string.memoh_ui_embedding_model), embeddingModelId, { embeddingModelId = it },
                            enabled = !loading && !configReadForbidden && configLoadError.isBlank(),
                            optional = true, modelType = "embedding",
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
                                    message = uiResources.getString(R.string.memoh_ui_team_memory_configuration_saved)
                                } catch (e: Exception) {
                                    message = e.message ?: uiResources.getString(R.string.memoh_ui_could_not_save_team_memory_configuration)
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text(uiResources.getString(R.string.memoh_ui_save_team_configuration)) }
                    }
                }
                if (status != null || usage != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(uiResources.getString(R.string.memoh_ui_memory_health), style = MaterialTheme.typography.titleSmall)
                            Text(uiResources.getString(R.string.memoh_ui_mode_1_s_ccfcd7, status?.optString("memory_mode").orEmpty().ifBlank { "—" }) +
                                uiResources.getString(R.string.memoh_ui_provider_1_s, status?.optString("provider_type").orEmpty().ifBlank { "—" }))
                            Text(uiResources.getString(R.string.memoh_ui_sources_1_s, status?.optInt("source_count", 0) ?: 0) +
                                uiResources.getString(R.string.memoh_ui_indexed_1_s, status?.optInt("indexed_count", 0) ?: 0) +
                                uiResources.getString(R.string.memoh_ui_storage_1_s_bytes, usage?.optLong("estimated_storage_bytes", 0L) ?: 0L) +
                                uiResources.getString(R.string.memoh_ui_1_s_entries, usage?.optInt("count", 0) ?: 0))
                            if (status?.optBoolean("degraded") == true) {
                                Text(uiResources.getString(R.string.memoh_ui_search_index_is_behind_stored_memories), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text(uiResources.getString(R.string.memoh_ui_search_memories)) },
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
                                } catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_search_failed) }
                                finally { loading = false }
                            }
                        }) { Text(uiResources.getString(R.string.memoh_ui_search)) }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searching) TextButton(onClick = { searching = false; searchResults = emptyList() }) {
                        Text(uiResources.getString(R.string.memoh_ui_show_all))
                    }
                    Button(onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                if (editId == null) api.addMemory(botId, draft) else api.updateMemory(botId, editId!!, draft)
                                draft = ""; editId = null; load()
                            } catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_could_not_save_memory); loading = false }
                        }
                    }, enabled = draft.isNotBlank() && !loading) { Text(if (editId == null) uiResources.getString(R.string.memoh_ui_add_memory) else uiResources.getString(R.string.memoh_ui_save_edit)) }
                    TextButton(onClick = { confirmingDeleteAll = true }, enabled = memories.isNotEmpty() && !loading) {
                        Text(uiResources.getString(R.string.memoh_ui_delete_all), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try { graph = api.memoryGraph(botId) }
                            catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_could_not_load_graph) }
                            finally { loading = false }
                        }
                    }, enabled = !loading) { Text(uiResources.getString(R.string.memoh_ui_graph)) }
                }
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = { confirmingDeleteSelected = true }, enabled = !loading) {
                        Text(uiResources.getString(R.string.memoh_ui_delete_selected_1_s, selectedIds.size), color = MaterialTheme.colorScheme.error)
                    }
                }
                androidx.compose.material3.Slider(
                    value = (compactRatio.toFloatOrNull() ?: 0.8f).coerceIn(0.01f, 1f),
                    onValueChange = { compactRatio = "%.2f".format(java.util.Locale.ROOT, it) },
                    valueRange = 0.01f..1f, steps = 98, enabled = !loading,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),value = compactRatio, onValueChange = { compactRatio = it },
                        label = { Text(uiResources.getString(R.string.memoh_ui_keep_ratio)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),value = decayDays, onValueChange = { decayDays = it.filter(Char::isDigit) },
                        label = { Text(uiResources.getString(R.string.memoh_ui_decay_days_optional)) }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                if (draft.isNotEmpty() || editId != null) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        label = { Text(if (editId == null) uiResources.getString(R.string.memoh_ui_new_memory) else uiResources.getString(R.string.memoh_ui_edit_memory)) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        label = { Text(uiResources.getString(R.string.memoh_ui_add_a_memory)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = ::load, enabled = !loading) { Text(uiResources.getString(R.string.memoh_ui_retry)) }
                }
                graph?.let { graphData ->
                    Text(uiResources.getString(R.string.memoh_ui_graph_1_s_nodes, graphData.optJSONArray("nodes")?.length() ?: 0) +
                        uiResources.getString(R.string.memoh_ui_1_s_relations, graphData.optJSONArray("edges")?.length() ?: 0), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !loading, onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.ingestMemories(botId)
                                message = uiResources.getString(R.string.memoh_ui_ingested_1_s_files_skipped_2_s, response.optInt("ingested", 0), response.optInt("skipped", 0))
                                load()
                            }
                            catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_ingest_failed); loading = false }
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_ingest_files)) }
                    TextButton(enabled = !loading, onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.rebuildMemories(botId)
                                message = uiResources.getString(R.string.memoh_ui_restored_1_s_of, response.optInt("restored_count", 0)) +
                                    uiResources.getString(R.string.memoh_ui_1_s_filesystem_memories, response.optInt("fs_count", 0))
                                load()
                            }
                            catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_rebuild_failed); loading = false }
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_rebuild)) }
                    TextButton(enabled = !loading && compactRatio.toDoubleOrNull()?.let { it > 0.0 && it <= 1.0 } == true &&
                        (decayDays.isBlank() || decayDays.toIntOrNull() != null), onClick = {
                        scope.launch {
                            loading = true; message = ""
                            try {
                                val response = api.compactMemories(botId, compactRatio.toDouble(), decayDays.toIntOrNull())
                                message = uiResources.getString(R.string.memoh_ui_compacted_1_s_memories_to, response.optInt("before_count", 0)) +
                                    "${response.optInt("after_count", 0)}."
                                load()
                            } catch (e: Exception) {
                                message = e.message ?: uiResources.getString(R.string.memoh_ui_compaction_failed)
                                loading = false
                            }
                        }
                    }) { Text(uiResources.getString(R.string.memoh_ui_compact)) }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                        }
                    }
                    if (!loading && displayedMemories.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(if (searching) uiResources.getString(R.string.memoh_ui_no_matching_memories) else uiResources.getString(R.string.memoh_ui_no_memories_yet),
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (searching) uiResources.getString(R.string.memoh_ui_try_another_search_phrase) else uiResources.getString(R.string.memoh_ui_memories_saved_by_this_bot_will_appear_here),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = if (searching) ({ searching = false; searchResults = emptyList() }) else ::load) {
                            Text(if (searching) uiResources.getString(R.string.memoh_ui_show_all) else uiResources.getString(R.string.memoh_ui_refresh))
                        }
                    }
                    }
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
                                    Text(uiResources.getString(R.string.memoh_ui_updated_1_s, item.optString("updated_at").ifBlank { item.optString("created_at") }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        TextButton(onClick = { editId = id; draft = item.optString("memory") }) { Text(uiResources.getString(R.string.memoh_ui_edit)) }
                                        TextButton(onClick = { confirmingDeleteId = id }) {
                                            Text(uiResources.getString(R.string.memoh_ui_delete), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    item { Spacer(Modifier.height(2.dp)) }
                }
            }
        }
    }
    if (confirmingDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteSelected = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_selected_memories)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_this_permanently_removes_1_s_selected_memories, selectedIds.size)) },
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
                            message = e.message ?: uiResources.getString(R.string.memoh_ui_delete_failed)
                            loading = false
                        }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_delete_selected), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteSelected = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
    if (confirmingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteId = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_this_memory)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_this_permanently_removes_the_selected_memory)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = confirmingDeleteId ?: return@TextButton
                    confirmingDeleteId = null
                    scope.launch {
                        loading = true; message = ""
                        try { api.deleteMemory(botId, id); load() }
                        catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_delete_failed); loading = false }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteId = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
    if (confirmingDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteAll = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_all_memories)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_this_permanently_deletes_every_memory_shared_by_this_bot)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteAll = false
                    scope.launch {
                        loading = true; message = ""
                        try { api.deleteMemories(botId, emptyList()); load() }
                        catch (e: Exception) { message = e.message ?: uiResources.getString(R.string.memoh_ui_delete_failed); loading = false }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_delete_all), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteAll = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
}
