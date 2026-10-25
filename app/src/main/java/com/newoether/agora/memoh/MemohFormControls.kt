package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.newoether.agora.ui.settings.SettingsItem
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.TimeZone

/**
 * Searchable time-zone chooser, mirroring the reference client's `TimeZoneFormField`.
 *
 * A free-text field for an IANA identifier is a trap: one typo produces a silently invalid value,
 * and nobody remembers whether the server wants `Asia/Shanghai` or `+08:00`. The reference lists
 * "Default", the device zone, then every known identifier with a search filter — so this does too.
 */
@Composable
internal fun TimeZonePickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    var search by remember { mutableStateOf("") }
    val deviceZone = remember { TimeZone.getDefault().id }
    val zones = remember(search) {
        val query = search.trim()
        if (query.isEmpty()) {
            TimeZone.getAvailableIDs().toList()
        } else {
            TimeZone.getAvailableIDs().filter { id ->
                id.contains(query, ignoreCase = true) ||
                    displayZoneName(id).contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_time_zone)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_search_time_zones)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    item {
                        ZoneRow(
                            title = uiResources.getString(R.string.memoh_ui_default),
                            subtitle = uiResources.getString(R.string.memoh_ui_server_default),
                            selected = current.isBlank(),
                            onClick = { onSelected(""); onDismiss() },
                        )
                    }
                    item {
                        ZoneRow(
                            title = uiResources.getString(R.string.memoh_ui_device_time_zone),
                            subtitle = deviceZone,
                            selected = current == deviceZone,
                            onClick = { onSelected(deviceZone); onDismiss() },
                        )
                    }
                    items(zones, key = { it }) { id ->
                        ZoneRow(
                            title = id.replace('_', ' '),
                            subtitle = displayZoneName(id),
                            selected = current == id,
                            onClick = { onSelected(id); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
    )
}

@Composable
private fun ZoneRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
        }
    }
}


internal data class MemohResourceChoice(val id: String, val label: String, val detail: String = "")

/** Same response shapes and identifier fallbacks as Homem's ResourceReferencePicker. */
internal fun resourceChoices(response: JSONObject, modelType: String = "", current: String = ""): List<MemohResourceChoice> {
    val array = response.optJSONArray("items") ?: response.optJSONArray("targets")
        ?: response.optJSONArray("candidates") ?: JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val id = sequenceOf("id", "user_id", "channel_identity_id", "runtime_id", "target_id")
            .map { row.optString(it) }.firstOrNull(String::isNotBlank) ?: return@mapNotNull null
        if (id != current && modelType.isNotBlank() &&
            (row.optString("type") != modelType || !row.optBoolean("enable", true) ||
                row.optJSONObject("config")?.optBoolean("catalog_available", true) == false)) return@mapNotNull null
        val label = sequenceOf("display_name", "username", "name", "title", "model_id", "platform")
            .map { row.optString(it) }.firstOrNull(String::isNotBlank).orEmpty()
        MemohResourceChoice(id, label, row.optString("description"))
    }.distinctBy { it.id }
}

@Composable
internal fun MemohChoiceField(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    SettingsItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.Default.KeyboardArrowDown, null) },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
internal fun MemohTimeZoneField(value: String, onValueChange: (String) -> Unit, enabled: Boolean = true) {
    val uiResources = LocalContext.current.resources
    var picking by remember { mutableStateOf(false) }
    MemohChoiceField(uiResources.getString(R.string.memoh_ui_time_zone), value.ifBlank { uiResources.getString(R.string.memoh_ui_server_default) }, enabled) { picking = true }
    if (picking) TimeZonePickerDialog(value, { picking = false }, onValueChange)
}

/** IDs are selected from their owning collection. Unknown existing values are preserved on load errors. */
@Composable
internal fun MemohResourceField(
    api: MemohApi,
    source: String,
    label: String,
    current: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    optional: Boolean = false,
    modelType: String = "",
    currentLabel: String = "",
    serverSearch: Boolean = false,
) {
    val uiResources = LocalContext.current.resources
    val identity = api.scope
    var choices by remember(source, identity) { mutableStateOf<List<MemohResourceChoice>>(emptyList()) }
    var loading by remember(source, identity) { mutableStateOf(true) }
    var error by remember(source, identity) { mutableStateOf("") }
    var cursor by remember(source, identity) { mutableStateOf("") }
    var nextCursor by remember(source, identity) { mutableStateOf("") }
    var retry by remember(source, identity) { mutableStateOf(0) }
    var picking by remember(source) { mutableStateOf(false) }
    var search by remember(source) { mutableStateOf("") }
    val serverQuery = if (serverSearch) search.trim() else ""
    LaunchedEffect(source, identity, cursor, serverQuery, retry) {
        loading = true
        try {
            if (serverQuery.isNotBlank()) delay(250)
            val path = resourcePickerPath(source, cursor, serverQuery)
            val response = api.request(path)
            val page = resourceChoices(response, modelType, current)
            choices = ((if (cursor.isBlank()) emptyList() else choices) + page).distinctBy { it.id }
            nextCursor = response.optString("next_cursor").takeIf { it != cursor }.orEmpty()
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unable_to_load_choices)
        } finally { loading = false }
    }
    val selected = choices.firstOrNull { it.id == current }
    var selectedLabel by remember(source, identity, current) { mutableStateOf(currentLabel) }
    LaunchedEffect(selected?.label) { selected?.label?.takeIf { it.isNotBlank() }?.let { selectedLabel = it } }
    val value = when {
        current.isBlank() -> if (optional) uiResources.getString(R.string.memoh_ui_default) else uiResources.getString(R.string.memoh_ui_choose)
        !selected?.label.isNullOrBlank() -> selected.label
        selectedLabel.isNotBlank() -> selectedLabel
        else -> uiResources.getString(R.string.memoh_ui_current_selection)
    }
    MemohChoiceField(label, value, enabled) { picking = true }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it; if (serverSearch) cursor = "" },
                    label = { Text(uiResources.getString(R.string.memoh_ui_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    if (optional) item {
                        ZoneRow(uiResources.getString(R.string.memoh_ui_default), "", current.isBlank()) { onSelected(""); picking = false }
                    }
                    if (current.isNotBlank() && selected == null) item {
                        ZoneRow(value, "", true) { picking = false }
                    }
                    val visible = choices.filter { serverSearch || search.isBlank() || it.label.contains(search, true) || it.id.contains(search, true) }
                    items(visible, key = { it.id }) { choice ->
                        ZoneRow(choice.label.ifBlank { uiResources.getString(R.string.memoh_ui_untitled) }, choice.detail, choice.id == current) {
                            onSelected(choice.id); picking = false
                        }
                    }
                    if (loading) item { CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp)) }
                    if (error.isNotBlank()) item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }, enabled = !loading) { Text(uiResources.getString(R.string.memoh_ui_retry)) }
                    }
                    if (!loading && error.isBlank() && visible.isEmpty()) item { Text(uiResources.getString(R.string.memoh_ui_no_matching_choices)) }
                    if (nextCursor.isNotBlank()) item {
                        TextButton(enabled = !loading, onClick = { cursor = nextCursor }) { Text(uiResources.getString(R.string.memoh_ui_load_more)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text(uiResources.getString(R.string.memoh_ui_close)) } },
    )
}

internal fun resourcePickerPath(source: String, cursor: String = "", query: String = ""): String {
    val parameters = buildList {
        if (cursor.isNotBlank()) add("cursor=" + Uri.encode(cursor))
        if (query.isNotBlank()) add("q=" + Uri.encode(query.trim()))
    }
    return source + if (parameters.isEmpty()) "" else (if ('?' in source) "&" else "?") + parameters.joinToString("&")
}

internal fun displayZoneName(id: String): String = runCatching {
    val zone = TimeZone.getTimeZone(id)
    val offsetMinutes = zone.getOffset(System.currentTimeMillis()) / 60_000
    val sign = if (offsetMinutes < 0) "-" else "+"
    val hours = kotlin.math.abs(offsetMinutes) / 60
    val minutes = kotlin.math.abs(offsetMinutes) % 60
    "GMT$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}.getOrDefault(id)
