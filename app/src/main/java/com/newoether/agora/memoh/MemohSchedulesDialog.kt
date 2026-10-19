package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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

/** Server-owned schedules: create, toggle and delete. No local background jobs. */
@Composable
internal fun MemohSchedulesDialog(api: MemohApi, botId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var schedules by remember(botId) { mutableStateOf<List<MemohSchedule>>(emptyList()) }
    var loading by remember(botId) { mutableStateOf(false) }
    var message by remember(botId) { mutableStateOf("") }
    var reloadKey by remember(botId) { mutableStateOf(0) }
    var newSchedule by remember(botId) { mutableStateOf(false) }
    var name by remember(botId) { mutableStateOf("") }
    var cron by remember(botId) { mutableStateOf("") }
    var instruction by remember(botId) { mutableStateOf("") }
    var deleteTarget by remember(botId) { mutableStateOf<MemohSchedule?>(null) }
    var pendingId by remember(botId) { mutableStateOf("") }

    LaunchedEffect(botId, reloadKey) {
        loading = true
        message = ""
        try { schedules = api.schedules(botId) }
        catch (e: Exception) { message = e.message ?: "Unable to load schedules" }
        finally { loading = false }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedules", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (message.isNotBlank()) Text(message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { reloadKey++ }, enabled = !loading) { Text("Refresh") }
                    TextButton(onClick = { newSchedule = !newSchedule }) {
                        Text(if (newSchedule) "Cancel" else "New Schedule")
                    }
                }
                if (newSchedule) {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = cron, onValueChange = { cron = it },
                        label = { Text("Cron Pattern") },
                        placeholder = { Text("0 9 * * *") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = instruction, onValueChange = { instruction = it },
                        label = { Text("Agent Instruction") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4)
                    Button(enabled = name.isNotBlank() && cron.isNotBlank() && instruction.isNotBlank()
                        && pendingId.isBlank(), onClick = {
                        pendingId = "create"
                        scope.launch {
                            try {
                                api.createSchedule(botId, name, cron, instruction)
                                newSchedule = false
                                name = ""
                                cron = ""
                                instruction = ""
                                reloadKey++
                            } catch (e: Exception) {
                                message = e.message ?: "Failed to create schedule"
                            } finally { pendingId = "" }
                        }
                    }) { Text("Create") }
                }
                if (loading) CircularProgressIndicator()
                LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(schedules, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(task.name, style = MaterialTheme.typography.titleSmall)
                                Text(task.pattern, style = MaterialTheme.typography.bodySmall)
                                Text(task.description.ifBlank { task.command },
                                    maxLines = 3, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = task.enabled,
                                        enabled = pendingId.isBlank(),
                                        onCheckedChange = { enabled ->
                                            pendingId = task.id
                                            scope.launch {
                                                try {
                                                    api.setScheduleEnabled(botId, task.id, enabled)
                                                    reloadKey++
                                                } catch (e: Exception) {
                                                    message = e.message ?: "Unable to update schedule"
                                                } finally { pendingId = "" }
                                            }
                                        })
                                    Text(if (task.enabled) "Enabled" else "Disabled",
                                        modifier = Modifier.weight(1f))
                                    TextButton(onClick = { deleteTarget = task },
                                        enabled = pendingId.isBlank()) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = deleteTarget
    if (pendingDelete != null) {
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Schedule?") },
            text = { Text("Permanently delete ${pendingDelete.name} from Memoh.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingId = pendingDelete.id
                    scope.launch {
                        try {
                            api.deleteSchedule(botId, pendingDelete.id)
                            deleteTarget = null
                            reloadKey++
                        } catch (e: Exception) {
                            message = e.message ?: "Unable to delete schedule"
                        } finally { pendingId = "" }
                    }
                }, enabled = pendingId.isBlank()) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            })
    }
}
