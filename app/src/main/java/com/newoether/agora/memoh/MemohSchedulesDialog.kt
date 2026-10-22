package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * Server-owned schedules: create, edit, toggle, delete, and inspect run history.
 * No local background jobs — the Memoh server is the only scheduler.
 */
@Composable
internal fun MemohSchedulesDialog(
    api: MemohApi,
    botId: String,
    onDismiss: () -> Unit,
    sessions: List<MemohSession> = emptyList(),
    /** Jump into a conversation a schedule run produced. */
    onOpenSession: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var schedules by remember(botId) { mutableStateOf<List<MemohSchedule>>(emptyList()) }
    var loading by remember(botId) { mutableStateOf(true) }
    var message by remember(botId) { mutableStateOf("") }
    var messageIsError by remember(botId) { mutableStateOf(false) }
    var reloadKey by remember(botId) { mutableStateOf(0) }
    var pendingId by remember(botId) { mutableStateOf("") }
    var deleteTarget by remember(botId) { mutableStateOf<MemohSchedule?>(null) }
    var logsTarget by remember(botId) { mutableStateOf<MemohSchedule?>(null) }
    var allLogsOpen by remember(botId) { mutableStateOf(false) }
    // null = the editor is closed; a blank MemohSchedule = creating a new one.
    var editor by remember(botId) { mutableStateOf<MemohSchedule?>(null) }

    fun notify(text: String, isError: Boolean) {
        message = text
        messageIsError = isError
    }

    LaunchedEffect(botId, reloadKey) {
        loading = true
        try {
            schedules = api.schedules(botId)
            message = ""
        } catch (e: Exception) {
            notify(e.message ?: "Unable to load schedules", isError = true)
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedules", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { reloadKey++ }, enabled = !loading) { Text("Refresh") }
                    TextButton(onClick = { editor = MemohSchedule("", "", "", "", "", true) }) {
                        Text("New Schedule")
                    }
                    TextButton(onClick = { allLogsOpen = true }, enabled = !loading) {
                        Text("All runs")
                    }
                }
                if (loading) CircularProgressIndicator()
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(schedules, key = { it.id }) { task ->
                        ScheduleCard(
                            task = task,
                            busy = pendingId.isNotBlank(),
                            onToggle = { enabled ->
                                pendingId = task.id
                                scope.launch {
                                    try {
                                        api.setScheduleEnabled(botId, task.id, enabled)
                                        notify("${task.name} ${if (enabled) "enabled" else "disabled"}.", false)
                                        reloadKey++
                                    } catch (e: Exception) {
                                        notify(e.message ?: "Unable to update schedule", true)
                                    } finally { pendingId = "" }
                                }
                            },
                            onEdit = { editor = task },
                            onLogs = { logsTarget = task },
                            onDelete = { deleteTarget = task },
                        )
                    }
                }
            }
        }
    }

    editor?.let { draft ->
        ScheduleEditor(
            api = api,
            botId = botId,
            draft = draft,
            sessions = sessions,
            isNew = draft.id.isBlank(),
            onDismiss = { editor = null },
            onSaved = { text ->
                editor = null
                notify(text, false)
                reloadKey++
            },
        )
    }

    logsTarget?.let { target ->
        ScheduleLogsDialog(
            api = api,
            botId = botId,
            schedule = target,
            onDismiss = { logsTarget = null },
            onOpenSession = onOpenSession,
        )
    }

    if (allLogsOpen) {
        AllScheduleLogsDialog(api, botId, onDismiss = { allLogsOpen = false }, onOpenSession = onOpenSession)
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Schedule?") },
            text = { Text("Permanently delete ${target.name} from Memoh. Its run history is deleted too.") },
            confirmButton = {
                TextButton(enabled = pendingId.isBlank(), onClick = {
                    pendingId = target.id
                    scope.launch {
                        try {
                            api.deleteSchedule(botId, target.id)
                            deleteTarget = null
                            notify("${target.name} deleted.", false)
                            reloadKey++
                        } catch (e: Exception) {
                            notify(e.message ?: "Unable to delete schedule", true)
                        } finally { pendingId = "" }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleCard(
    task: MemohSchedule,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onLogs: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(task.name.ifBlank { "Untitled schedule" }, style = MaterialTheme.typography.titleSmall)
            Text(task.pattern, style = MaterialTheme.typography.bodySmall)
            if (task.description.isNotBlank()) {
                Text(task.description, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when (task.runTarget) {
                    "existing_session" ->
                        "Continues session ${task.targetSessionId.take(8).ifBlank { "?" }}"
                    else -> "Starts a new session"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.modelId.isNotBlank()) {
                Text("Model override ${task.modelId.take(8)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.maxRunSeconds > 0) {
                Text("Budget ${task.maxRunSeconds}s", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.maxCalls > 0) {
                Text("Used ${task.currentCalls}/${task.maxCalls} calls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.enabled, enabled = !busy, onCheckedChange = onToggle)
                Text(if (task.enabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                TextButton(onClick = onLogs, enabled = !busy) { Text("Runs") }
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    api: MemohApi,
    botId: String,
    draft: MemohSchedule,
    sessions: List<MemohSession>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var pattern by remember(draft.id) { mutableStateOf(draft.pattern) }
    var command by remember(draft.id) { mutableStateOf(draft.command) }
    var description by remember(draft.id) { mutableStateOf(draft.description) }
    var runTarget by remember(draft.id) {
        mutableStateOf(if (draft.runTarget == "existing_session") "existing_session" else "new_session")
    }
    var targetSessionId by remember(draft.id) { mutableStateOf(draft.targetSessionId) }
    var modelId by remember(draft.id) { mutableStateOf(draft.modelId) }
    var budget by remember(draft.id) {
        mutableStateOf(if (draft.maxRunSeconds > 0) draft.maxRunSeconds.toString() else "")
    }
    var enabled by remember(draft.id) { mutableStateOf(draft.enabled) }
    var busy by remember(draft.id) { mutableStateOf(false) }
    var error by remember(draft.id) { mutableStateOf("") }
    var pickingSession by remember(draft.id) { mutableStateOf(false) }

    val budgetValue = budget.trim().toIntOrNull()
    val budgetValid = budget.isBlank() || (budgetValue != null && budgetValue in 300..86400)
    val canSave = name.isNotBlank() && pattern.isNotBlank() && command.isNotBlank() && budgetValid &&
        (runTarget != "existing_session" || targetSessionId.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isNew) "New Schedule" else "Edit Schedule") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pattern, onValueChange = { pattern = it },
                    label = { Text("Cron Pattern") }, placeholder = { Text("0 9 * * *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("minute hour day month weekday") })
                OutlinedTextField(value = command, onValueChange = { command = it },
                    label = { Text("Agent Instruction") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)

                Text("Run in", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { runTarget = "new_session"; targetSessionId = "" }) {
                        Text(if (runTarget == "new_session") "✓ New session" else "New session")
                    }
                    TextButton(onClick = { runTarget = "existing_session"; pickingSession = true }) {
                        Text(if (runTarget == "existing_session") "✓ Existing session" else "Existing session")
                    }
                }
                if (runTarget == "existing_session") {
                    Text(
                        if (targetSessionId.isBlank()) "No session chosen yet."
                        else "Target: ${targetSessionId.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { pickingSession = true }) { Text("Choose session") }
                } else {
                    OutlinedTextField(value = modelId, onValueChange = { modelId = it },
                        label = { Text("Model override ID (optional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Leave empty to use the bot's default model") })
                }
                OutlinedTextField(value = budget, onValueChange = { budget = it.filter(Char::isDigit) },
                    label = { Text("Run budget in seconds (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = !budgetValid,
                    supportingText = {
                        Text(if (budgetValid) "300–86400; empty uses the one-hour default"
                        else "Must be between 300 and 86400")
                    })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Enabled")
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = canSave && !busy, onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        if (isNew) {
                            api.createSchedule(
                                botId = botId, name = name, pattern = pattern, command = command,
                                description = description, runTarget = runTarget,
                                targetSessionId = if (runTarget == "existing_session") targetSessionId else "",
                                modelId = if (runTarget == "new_session") modelId else "",
                                maxRunSeconds = budgetValue ?: 0, enabled = enabled,
                            )
                            onSaved("Schedule created.")
                        } else {
                            api.updateSchedule(
                                botId = botId, scheduleId = draft.id,
                                name = name, pattern = pattern, command = command,
                                description = description, runTarget = runTarget,
                                targetSessionId = if (runTarget == "existing_session") targetSessionId else "",
                                modelId = if (runTarget == "new_session") modelId else "",
                                botAgentId = if (runTarget == "new_session") draft.botAgentId else "",
                                maxRunSeconds = budgetValue ?: 0, enabled = enabled,
                            )
                            onSaved("Schedule updated.")
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Unable to save schedule"
                    } finally { busy = false }
                }
            }) { Text(if (isNew) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )

    if (pickingSession) {
        SessionPickerDialog(
            api = api,
            botId = botId,
            initialSessions = sessions,
            onPick = { targetSessionId = it.id; pickingSession = false },
            onDismiss = { pickingSession = false },
        )
    }
}

@Composable
private fun SessionPickerDialog(
    api: MemohApi,
    botId: String,
    initialSessions: List<MemohSession>,
    onPick: (MemohSession) -> Unit,
    onDismiss: () -> Unit,
) {
    var sessions by remember(botId) { mutableStateOf(initialSessions) }
    var cursor by remember(botId) { mutableStateOf("") }
    var nextCursor by remember(botId) { mutableStateOf<String?>(null) }
    var loading by remember(botId) { mutableStateOf(true) }
    var error by remember(botId) { mutableStateOf("") }
    var retry by remember(botId) { mutableStateOf(0) }
    LaunchedEffect(botId, cursor, retry) {
        loading = true
        error = ""
        try {
            val page = api.sessionsPage(botId, cursor)
            sessions = (sessions + page.items).distinctBy { it.id }
            nextCursor = page.nextCursor?.takeIf { it != cursor }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Unable to load conversations"
        } finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Session") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }, enabled = !loading) { Text("Retry") }
                }
                if (!loading && error.isBlank() && sessions.isEmpty()) Text("No conversations available.")
                sessions.forEach { session ->
                    TextButton(onClick = { onPick(session) }, modifier = Modifier.fillMaxWidth()) {
                        Text(session.title.ifBlank { session.id.take(8) })
                    }
                }
                if (nextCursor != null) TextButton(enabled = !loading && error.isBlank(),
                    onClick = { cursor = nextCursor.orEmpty() }) { Text("Load Older Conversations") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScheduleLogsDialog(
    api: MemohApi,
    botId: String,
    schedule: MemohSchedule,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var logs by remember(schedule.id) { mutableStateOf<List<MemohScheduleLog>>(emptyList()) }
    var sessions by remember(schedule.id) { mutableStateOf<List<MemohSession>>(emptyList()) }
    var nextCursor by remember(schedule.id) { mutableStateOf<String?>(null) }
    var sessionCursor by remember(schedule.id) { mutableStateOf("") }
    var loading by remember(schedule.id) { mutableStateOf(true) }
    var error by remember(schedule.id) { mutableStateOf("") }
    var confirmClear by remember(schedule.id) { mutableStateOf(false) }
    var reloadKey by remember(schedule.id) { mutableStateOf(0) }

    var logOffset by remember(schedule.id) { mutableStateOf(0) }
    var moreLogs by remember(schedule.id) { mutableStateOf(false) }
    LaunchedEffect(botId, schedule.id, logOffset, reloadKey) {
        loading = true
        try {
            val page = api.scheduleLogs(botId, schedule.id, offset = logOffset)
            logs = ((if (logOffset == 0) emptyList() else logs) + page).distinctBy { it.id }
            moreLogs = page.size == 20
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Unable to load run history"
        } finally { loading = false }
    }

    var sessionsLoading by remember(schedule.id) { mutableStateOf(false) }
    var sessionsError by remember(schedule.id) { mutableStateOf("") }
    LaunchedEffect(botId, schedule.id, sessionCursor, reloadKey) {
        sessionsLoading = true
        sessionsError = ""
        try {
            val page = api.scheduleSessions(botId, schedule.id, sessionCursor)
            sessions = ((if (sessionCursor.isBlank()) emptyList() else sessions) + page.items).distinctBy { it.id }
            nextCursor = page.nextCursor?.takeIf { it != sessionCursor }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            sessionsError = e.message ?: "Unable to load schedule conversations"
        } finally { sessionsLoading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runs · ${schedule.name.ifBlank { "schedule" }}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!loading && error.isBlank() && logs.isEmpty()) {
                    Text("No runs recorded yet.")
                }
                if (sessions.isNotEmpty()) {
                    Text("Run Conversations", style = MaterialTheme.typography.titleSmall)
                    sessions.forEach { run ->
                        TextButton(onClick = { onOpenSession(run.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(run.title.ifBlank { run.id.take(8) })
                        }
                    }
                    Text("Runs", style = MaterialTheme.typography.titleSmall)
                }
                if (sessionsLoading) CircularProgressIndicator()
                if (sessionsError.isNotBlank()) Text(sessionsError, color = MaterialTheme.colorScheme.error)
                if (nextCursor != null) {
                    TextButton(enabled = !sessionsLoading, onClick = { sessionCursor = nextCursor.orEmpty() }) {
                        Text("Load Older Conversations")
                    }
                }
                TextButton(enabled = !loading && !sessionsLoading, onClick = {
                    logOffset = 0
                    sessionCursor = ""
                    reloadKey++
                }) { Text("Refresh") }
                logs.forEach { log -> ScheduleLogCard(log, onOpenSession) }
                if (moreLogs) TextButton(enabled = !loading && error.isBlank(), onClick = { logOffset += 20 }) {
                    Text("Load Older Runs")
                }
            }
        },
        confirmButton = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text("Clear All Bot Run History") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear run history?") },
            text = { Text("Deletes every recorded run for this bot's schedules. The schedules themselves stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        try {
                            api.deleteScheduleLogs(botId)
                            logOffset = 0
                            sessionCursor = ""
                            reloadKey++
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to clear history"
                        }
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Every schedule run this bot has recorded, across all schedules. */
@Composable
private fun AllScheduleLogsDialog(
    api: MemohApi,
    botId: String,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    var logs by remember(botId) { mutableStateOf<List<MemohScheduleLog>>(emptyList()) }
    var loading by remember(botId) { mutableStateOf(true) }
    var error by remember(botId) { mutableStateOf("") }

    var offset by remember(botId) { mutableStateOf(0) }
    var hasMore by remember(botId) { mutableStateOf(false) }
    var reloadKey by remember(botId) { mutableStateOf(0) }
    LaunchedEffect(botId, offset, reloadKey) {
        loading = true
        try {
            val page = api.allScheduleLogs(botId, offset = offset)
            logs = ((if (offset == 0) emptyList() else logs) + page).distinctBy { it.id }
            hasMore = page.size == 50
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Unable to load run history"
        } finally { loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All schedule runs") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!loading && error.isBlank() && logs.isEmpty()) Text("No runs recorded yet.")
                logs.forEach { log -> ScheduleLogCard(log, onOpenSession) }
                if (hasMore) TextButton(enabled = !loading && error.isBlank(), onClick = { offset += 50 }) {
                    Text("Load Older Runs")
                }
                TextButton(enabled = !loading, onClick = {
                    if (error.isBlank()) offset = 0
                    reloadKey++
                }) {
                    Text(if (error.isBlank()) "Refresh" else "Retry")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ScheduleLogCard(log: MemohScheduleLog, onOpenSession: (String) -> Unit) {
    val failed = log.status.equals("failed", ignoreCase = true) ||
        log.status.equals("error", ignoreCase = true) || log.errorCode.isNotBlank()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${log.status.ifBlank { "unknown" }} · ${log.startedAt.ifBlank { "?" }}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (log.completedAt.isNotBlank()) {
                Text("Finished ${log.completedAt}", style = MaterialTheme.typography.labelSmall)
            }
            val failure = log.errorMessage.ifBlank { log.errorCode }
            if (failure.isNotBlank()) {
                Text(failure, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (log.resultText.isNotBlank()) {
                Text(log.resultText, style = MaterialTheme.typography.bodySmall, maxLines = 6)
            }
            if (log.sessionId.isNotBlank()) {
                TextButton(onClick = { onOpenSession(log.sessionId) }) {
                    Text("Open Conversation · ${log.sessionId.take(8)}")
                }
            }
        }
    }
}
