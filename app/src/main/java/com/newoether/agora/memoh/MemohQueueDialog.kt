package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Remote queue content is never mirrored to Agora's local conversation store. */
@Composable
internal fun MemohQueueDialog(vm: MemohViewModel, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
    var cancelTarget by remember { mutableStateOf<MemohQueueItem?>(null) }
    var editTarget by remember { mutableStateOf<MemohQueueItem?>(null) }
    val snapshot = vm.queueState
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_message_queue)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text(if (snapshot?.steerSupported == true) uiResources.getString(R.string.memoh_ui_steering_available)
                        else uiResources.getString(R.string.memoh_ui_follow_up_queue), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = vm::refreshQueue) { Text(uiResources.getString(R.string.memoh_ui_refresh)) }
                }
                if (snapshot == null) Text(uiResources.getString(R.string.memoh_ui_loading_queue))
                else if (snapshot.items.isEmpty()) Text(uiResources.getString(R.string.memoh_ui_no_pending_messages))
                else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(snapshot.items, key = { it.type + ":" + it.id }) { item ->
                            QueueRow(
                                item = item,
                                aboveId = snapshot.items.firstOrNull {
                                    it.type == item.type && it.id != item.id
                                }?.id,
                                belowId = snapshot.items.lastOrNull {
                                    it.type == item.type && it.id != item.id
                                }?.id,
                                busy = vm.queueBusy,
                                onEdit = { editTarget = item },
                                onCancel = { cancelTarget = item },
                                onReorder = { before -> vm.reorderQueued(item, before) },
                                onPromote = { vm.promoteQueued(item) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_done)) } },
    )

    val target = cancelTarget
    if (target != null) AlertDialog(
        onDismissRequest = { cancelTarget = null },
        title = { Text(uiResources.getString(R.string.memoh_ui_remove_queued_message)) },
        text = { Text(target.text) },
        confirmButton = {
            TextButton(onClick = { vm.cancelQueued(target); cancelTarget = null },
                enabled = !vm.queueBusy) { Text(uiResources.getString(R.string.memoh_ui_remove)) }
        },
        dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
    )

    val editing = editTarget
    if (editing != null) {
        var draft by remember(editing.id) { mutableStateOf(editing.text) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_edit_queued_message)) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.editQueued(editing, draft); editTarget = null },
                    enabled = !vm.queueBusy && draft.isNotBlank() && draft.trim() != editing.text,
                ) { Text(uiResources.getString(R.string.memoh_ui_save)) }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
        )
    }
}

/** One queue item with the actions the Memoh web client exposes: edit, reorder, promote, remove. */
@Composable
private fun QueueRow(
    item: MemohQueueItem,
    aboveId: String?,
    belowId: String?,
    busy: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onReorder: (String?) -> Unit,
    onPromote: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val editable = item.status == "queued" || item.status == "pending"
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(if (item.type == "steer") uiResources.getString(R.string.memoh_ui_steer_1_s, item.status)
             else uiResources.getString(R.string.memoh_ui_follow_up_1_s, item.status),
            style = MaterialTheme.typography.labelSmall)
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
        if (editable) {
            Row {
                TextButton(onClick = onEdit, enabled = !busy) { Text(uiResources.getString(R.string.memoh_ui_edit)) }
                if (aboveId != null) TextButton(
                    onClick = { onReorder(aboveId) },
                    enabled = !busy,
                ) { Text(uiResources.getString(R.string.memoh_ui_move_up)) }
                if (belowId != null) TextButton(
                    onClick = { onReorder(null) },
                    enabled = !busy,
                ) { Text(uiResources.getString(R.string.memoh_ui_move_down)) }
                if (item.type == "follow_up") TextButton(
                    onClick = onPromote,
                    enabled = !busy,
                ) { Text(uiResources.getString(R.string.memoh_ui_steer_now)) }
                TextButton(onClick = onCancel, enabled = !busy) { Text(uiResources.getString(R.string.memoh_ui_remove)) }
            }
        }
    }
}
