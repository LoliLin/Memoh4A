package com.newoether.agora.memoh

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.newoether.agora.R
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
    val uiResources = LocalContext.current.resources
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
            notify(e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_load_schedules), isError = true)
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A fixed 85% height would sit under the keyboard while editing a schedule; the IME
        // padding is what keeps the fields and the editor visible.
        Surface(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.memoh_schedules_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.memoh_close)) }
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
                    TextButton(onClick = { reloadKey++ }, enabled = !loading) { Text(stringResource(R.string.memoh_refresh)) }
                    TextButton(onClick = { editor = MemohSchedule("", "", "", "", "", true) }) {
                        Text(stringResource(R.string.memoh_schedule_new))
                    }
                    TextButton(onClick = { allLogsOpen = true }, enabled = !loading) {
                        Text(stringResource(R.string.memoh_schedule_all_runs))
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
                                // Resolved outside the coroutine; uiResources is a plain lookup,
                                // not a composable call.
                                val toggled = uiResources.getString(
                                    if (enabled) R.string.memoh_error_schedule_toggled_on
                                    else R.string.memoh_error_schedule_toggled_off,
                                    task.name,
                                )
                                scope.launch {
                                    try {
                                        api.setScheduleEnabled(botId, task.id, enabled)
                                        notify(toggled, false)
                                        reloadKey++
                                    } catch (e: Exception) {
                                        notify(e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_update_schedule), true)
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
            title = { Text(stringResource(R.string.memoh_schedule_delete_title)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_permanently_delete_1_s_from_memoh_its_run_history_is_deleted_too, target.name)) },
            confirmButton = {
                TextButton(enabled = pendingId.isBlank(), onClick = {
                    pendingId = target.id
                    scope.launch {
                        try {
                            api.deleteSchedule(botId, target.id)
                            deleteTarget = null
                            notify(uiResources.getString(R.string.memoh_ui_1_s_deleted, target.name), false)
                            reloadKey++
                        } catch (e: Exception) {
                            notify(e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_delete_schedule), true)
                        } finally { pendingId = "" }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
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
    val uiResources = LocalContext.current.resources
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(task.name.ifBlank { uiResources.getString(R.string.memoh_ui_untitled_schedule) }, style = MaterialTheme.typography.titleSmall)
            Text(task.pattern, style = MaterialTheme.typography.bodySmall)
            if (task.description.isNotBlank()) {
                Text(task.description, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when (task.runTarget) {
                    "existing_session" ->
                        uiResources.getString(R.string.memoh_ui_continues_session_1_s, task.targetSessionId.take(8).ifBlank { "?" })
                    else -> uiResources.getString(R.string.memoh_ui_starts_a_new_session)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.modelId.isNotBlank()) {
                Text(uiResources.getString(R.string.memoh_ui_model_override_1_s, task.modelId.take(8)), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.maxRunSeconds > 0) {
                Text(uiResources.getString(R.string.memoh_ui_budget_1_ss, task.maxRunSeconds), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.maxCalls > 0) {
                Text(uiResources.getString(R.string.memoh_ui_used_1_s_2_s_calls, task.currentCalls, task.maxCalls),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.enabled, enabled = !busy, onCheckedChange = onToggle)
                Text(if (task.enabled) uiResources.getString(R.string.memoh_ui_enabled) else uiResources.getString(R.string.memoh_ui_disabled), modifier = Modifier.weight(1f))
                TextButton(onClick = onLogs, enabled = !busy) { Text(stringResource(R.string.memoh_schedule_runs)) }
                TextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.delete)) }
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
    val uiResources = LocalContext.current.resources
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
    var botAgentId by remember(draft.id) { mutableStateOf(draft.botAgentId) }

    val budgetValue = budget.trim().toIntOrNull()
    val budgetValid = budget.isBlank() || (budgetValue != null && budgetValue in 300..86400)
    val canSave = name.isNotBlank() && pattern.isNotBlank() && command.isNotBlank() && budgetValid &&
        (runTarget != "existing_session" || targetSessionId.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isNew) uiResources.getString(R.string.memoh_ui_new_schedule) else uiResources.getString(R.string.memoh_ui_edit_schedule)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),value = pattern, onValueChange = { pattern = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_cron_pattern)) }, placeholder = { Text("0 9 * * *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(uiResources.getString(R.string.memoh_ui_minute_hour_day_month_weekday)) })
                OutlinedTextField(value = command, onValueChange = { command = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_agent_instruction)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_description_optional)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)

                Text(uiResources.getString(R.string.memoh_ui_run_in), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { runTarget = "new_session"; targetSessionId = "" }) {
                        Text(if (runTarget == "new_session") uiResources.getString(R.string.memoh_ui_new_session_9c2e0a) else uiResources.getString(R.string.memoh_ui_new_session))
                    }
                    TextButton(onClick = { runTarget = "existing_session" }) {
                        Text(if (runTarget == "existing_session") uiResources.getString(R.string.memoh_ui_existing_session_f7aa7a) else uiResources.getString(R.string.memoh_ui_existing_session))
                    }
                }
                if (runTarget == "existing_session") {
                    MemohResourceField(
                        api, "/bots/${android.net.Uri.encode(botId)}/sessions?types=chat,discuss,acp_agent&hide_schedule_runs=true&limit=50",
                        uiResources.getString(R.string.memoh_ui_conversation), targetSessionId, { targetSessionId = it }, enabled = !busy,
                        currentLabel = sessions.firstOrNull { it.id == targetSessionId }?.title.orEmpty(),
                    )
                } else {
                    MemohResourceField(api, "/models", uiResources.getString(R.string.memoh_ui_model), modelId, { modelId = it },
                        enabled = !busy, optional = true, modelType = "chat")
                    MemohResourceField(api, "/bots/${android.net.Uri.encode(botId)}/agents", uiResources.getString(R.string.memoh_ui_agent), botAgentId,
                        { botAgentId = it }, enabled = !busy, optional = true)
                }
                OutlinedTextField(value = budget, onValueChange = { budget = it.filter(Char::isDigit) },
                    label = { Text(uiResources.getString(R.string.memoh_ui_run_budget_in_seconds_optional)) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = !budgetValid,
                    supportingText = {
                        Text(if (budgetValid) uiResources.getString(R.string.memoh_ui_300_86400_empty_uses_the_one_hour_default)
                        else uiResources.getString(R.string.memoh_ui_must_be_between_300_and_86400))
                    })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text(uiResources.getString(R.string.memoh_ui_enabled))
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
                                maxRunSeconds = budgetValue ?: 0, enabled = enabled, botAgentId = if (runTarget == "new_session") botAgentId else "",
                            )
                            onSaved(uiResources.getString(R.string.memoh_ui_schedule_created))
                        } else {
                            api.updateSchedule(
                                botId = botId, scheduleId = draft.id,
                                name = name, pattern = pattern, command = command,
                                description = description, runTarget = runTarget,
                                targetSessionId = if (runTarget == "existing_session") targetSessionId else "",
                                modelId = if (runTarget == "new_session") modelId else "",
                                botAgentId = if (runTarget == "new_session") botAgentId else "",
                                maxRunSeconds = budgetValue ?: 0, enabled = enabled,
                            )
                            onSaved(uiResources.getString(R.string.memoh_ui_schedule_updated))
                        }
                    } catch (e: Exception) {
                        error = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_save_schedule)
                    } finally { busy = false }
                }
            }) { Text(if (isNew) uiResources.getString(R.string.memoh_ui_create) else uiResources.getString(R.string.memoh_ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
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
    val uiResources = LocalContext.current.resources
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
            error = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_load_run_history)
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
            sessionsError = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_load_schedule_conversations)
        } finally { sessionsLoading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_runs_1_s, schedule.name.ifBlank { uiResources.getString(R.string.memoh_ui_schedule) })) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!loading && error.isBlank() && logs.isEmpty()) {
                    Text(uiResources.getString(R.string.memoh_ui_no_runs_recorded_yet))
                }
                if (sessions.isNotEmpty()) {
                    Text(uiResources.getString(R.string.memoh_ui_run_conversations), style = MaterialTheme.typography.titleSmall)
                    sessions.forEach { run ->
                        TextButton(onClick = { onOpenSession(run.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(run.title.ifBlank { run.id.take(8) })
                        }
                    }
                    Text(uiResources.getString(R.string.memoh_ui_runs), style = MaterialTheme.typography.titleSmall)
                }
                if (sessionsLoading) CircularProgressIndicator()
                if (sessionsError.isNotBlank()) Text(sessionsError, color = MaterialTheme.colorScheme.error)
                if (nextCursor != null) {
                    TextButton(enabled = !sessionsLoading, onClick = { sessionCursor = nextCursor.orEmpty() }) {
                        Text(uiResources.getString(R.string.memoh_ui_load_older_conversations))
                    }
                }
                TextButton(enabled = !loading && !sessionsLoading, onClick = {
                    logOffset = 0
                    sessionCursor = ""
                    reloadKey++
                }) { Text(uiResources.getString(R.string.memoh_ui_refresh)) }
                logs.forEach { log -> ScheduleLogCard(log, onOpenSession) }
                if (moreLogs) TextButton(enabled = !loading && error.isBlank(), onClick = { logOffset += 20 }) {
                    Text(uiResources.getString(R.string.memoh_ui_load_older_runs))
                }
            }
        },
        confirmButton = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text(uiResources.getString(R.string.memoh_ui_clear_all_bot_run_history)) }
            } else {
                TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_close)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_close)) } },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_clear_run_history)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_deletes_every_recorded_run_for_this_bot_s_schedules_the_schedules_themse)) },
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
                            error = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_clear_history)
                        }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
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
    val uiResources = LocalContext.current.resources
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
            error = e.message ?: uiResources.getString(R.string.memoh_ui_unable_to_load_run_history)
        } finally { loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_all_schedule_runs)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!loading && error.isBlank() && logs.isEmpty()) Text(uiResources.getString(R.string.memoh_ui_no_runs_recorded_yet))
                logs.forEach { log -> ScheduleLogCard(log, onOpenSession) }
                if (hasMore) TextButton(enabled = !loading && error.isBlank(), onClick = { offset += 50 }) {
                    Text(uiResources.getString(R.string.memoh_ui_load_older_runs))
                }
                TextButton(enabled = !loading, onClick = {
                    if (error.isBlank()) offset = 0
                    reloadKey++
                }) {
                    Text(if (error.isBlank()) uiResources.getString(R.string.memoh_ui_refresh) else uiResources.getString(R.string.memoh_ui_retry))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_close)) } },
    )
}

@Composable
private fun ScheduleLogCard(log: MemohScheduleLog, onOpenSession: (String) -> Unit) {
    val uiResources = LocalContext.current.resources
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
                Text(uiResources.getString(R.string.memoh_ui_finished_1_s, log.completedAt), style = MaterialTheme.typography.labelSmall)
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
                    Text(uiResources.getString(R.string.memoh_ui_open_conversation_1_s, log.sessionId.take(8)))
                }
            }
        }
    }
}
