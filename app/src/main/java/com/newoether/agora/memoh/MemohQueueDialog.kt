package com.newoether.agora.memoh

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
    var cancelTarget by remember { mutableStateOf<MemohQueueItem?>(null) }
    var editTarget by remember { mutableStateOf<MemohQueueItem?>(null) }
    val snapshot = vm.queueState
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Queue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text(if (snapshot?.steerSupported == true) "Steering Available"
                        else "Follow-Up Queue", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = vm::refreshQueue) { Text("Refresh") }
                }
                if (snapshot == null) Text("Loading queue…")
                else if (snapshot.items.isEmpty()) Text("No pending messages")
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    val target = cancelTarget
    if (target != null) AlertDialog(
        onDismissRequest = { cancelTarget = null },
        title = { Text("Remove Queued Message?") },
        text = { Text(target.text) },
        confirmButton = {
            TextButton(onClick = { vm.cancelQueued(target); cancelTarget = null },
                enabled = !vm.queueBusy) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("Cancel") } },
    )

    val editing = editTarget
    if (editing != null) {
        var draft by remember(editing.id) { mutableStateOf(editing.text) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Queued Message") },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.editQueued(editing, draft); editTarget = null },
                    enabled = !vm.queueBusy && draft.isNotBlank() && draft.trim() != editing.text,
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } },
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
    val editable = item.status == "queued" || item.status == "pending"
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(if (item.type == "steer") "Steer · ${item.status}"
             else "Follow-Up · ${item.status}",
            style = MaterialTheme.typography.labelSmall)
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
        if (editable) {
            Row {
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                if (aboveId != null) TextButton(
                    onClick = { onReorder(aboveId) },
                    enabled = !busy,
                ) { Text("Move Up") }
                if (belowId != null) TextButton(
                    onClick = { onReorder(null) },
                    enabled = !busy,
                ) { Text("Move Down") }
                if (item.type == "follow_up") TextButton(
                    onClick = onPromote,
                    enabled = !busy,
                ) { Text("Steer Now") }
                TextButton(onClick = onCancel, enabled = !busy) { Text("Remove") }
            }
        }
    }
}
