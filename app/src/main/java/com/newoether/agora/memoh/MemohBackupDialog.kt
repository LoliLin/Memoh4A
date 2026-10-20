package com.newoether.agora.memoh

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal enum class MemohBackupSection(val key: String) {
    PROFILE("profile"), SETTINGS("settings"), MODELS("models"), ACL("acl"), CHANNELS("channels"),
    MCP("mcp"), SCHEDULES("schedules"), HISTORY("history"), ASSETS("assets"), WORKSPACE("workspace"),
}

@Composable
internal fun MemohBackupDialog(
    api: MemohApi,
    bot: MemohBot,
    status: String,
    onExport: (sections: List<String>, passphrase: String) -> Unit,
    onChooseImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var summary by remember(bot.id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(bot.id) { mutableStateOf("") }
    var loading by remember(bot.id) { mutableStateOf(true) }
    var selectedSections by remember(bot.id) { mutableStateOf(MemohBackupSection.entries.toSet()) }
    var passphrase by remember(bot.id) { mutableStateOf("") }
    LaunchedEffect(bot.id) {
        try { summary = api.botBackupSummary(bot.id) }
        catch (t: Exception) { error = t.message ?: "Could not load backup summary" }
        finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bot backup · ${bot.title}") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Export sections", style = MaterialTheme.typography.titleSmall)
                MemohBackupSection.entries.forEach { section ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = section in selectedSections,
                            onCheckedChange = { checked ->
                                selectedSections = if (checked) selectedSections + section else selectedSections - section
                            },
                        )
                        Text(section.key.replaceFirstChar { it.uppercase() })
                    }
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Optional encryption passphrase") },
                    singleLine = true,
                )
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
                if (loading) CircularProgressIndicator()
                summary?.let { BackupSummary(it) }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onChooseImport) { Text("Import…") }
                Button(onClick = { onExport(selectedSections.map { it.key }, passphrase) }, enabled = !loading && summary != null && selectedSections.isNotEmpty()) { Text("Export…") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BackupSummary(data: JSONObject) {
    val profile = data.optJSONObject("profile")
    if (profile != null) {
        Text(profile.optString("display_name").ifBlank { "Bot profile" }, style = MaterialTheme.typography.titleMedium)
        if (profile.optString("timezone").isNotBlank()) Text("Timezone: ${profile.optString("timezone")}")
        if (profile.has("is_active")) Text(if (profile.optBoolean("is_active")) "Active" else "Inactive")
    }
    val sections = data.optJSONArray("sections") ?: JSONArray()
    if (sections.length() == 0) Text("No export sections were reported.")
    for (i in 0 until sections.length()) {
        val section = sections.optJSONObject(i) ?: continue
        val key = section.optString("key").ifBlank { "Section" }
        val count = section.optInt("count")
        val targetCount = section.optInt("target_count", -1)
        Text("${key.replaceFirstChar { it.uppercase() }}: $count item${if (count == 1) "" else "s"}" +
            if (targetCount >= 0) " · target has $targetCount" else "")
        val items = section.optJSONArray("items")
        if (items != null) for (j in 0 until minOf(items.length(), 8))
            Text("  • ${items.optString(j)}", style = MaterialTheme.typography.bodySmall)
        if (section.optBoolean("sensitive")) Text("Contains sensitive data", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun MemohBackupImportDialog(
    api: MemohApi,
    bot: MemohBot,
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onImported: (JSONObject) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember(uri) { mutableStateOf("create") }
    var targetId by remember(uri) { mutableStateOf(bot.id) }
    var passphrase by remember(uri) { mutableStateOf("") }
    var preview by remember(uri) { mutableStateOf<JSONObject?>(null) }
    var result by remember(uri) { mutableStateOf<JSONObject?>(null) }
    var error by remember(uri) { mutableStateOf("") }
    var busy by remember(uri) { mutableStateOf(false) }
    val encrypted = preview?.optBoolean("encrypted") == true
    val requiresPassphrase = preview?.optBoolean("requires_passphrase") == true
    fun previewBackup() {
        busy = true; error = ""
        scope.launch {
            try {
                preview = api.previewBotBackup(uri, context.contentResolver, mode,
                    if (mode == "overwrite") targetId else "", emptyList(), passphrase)
            } catch (t: Exception) { error = t.message ?: "Backup preview failed" }
            finally { busy = false }
        }
    }
    fun importBackup() {
        busy = true; error = ""
        scope.launch {
            try {
                result = api.importBotBackup(uri, context.contentResolver, mode,
                    if (mode == "overwrite") targetId else "", emptyList(), passphrase)
                onImported(result ?: JSONObject())
            } catch (t: Exception) { error = t.message ?: "Backup import failed" }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (result == null) "Import bot backup" else "Import complete") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result != null) {
                    Text("Bot ID: ${result!!.optString("bot_id")}")
                    Text(if (result!!.optBoolean("created")) "A bot was created." else "Backup applied to an existing bot.")
                    result!!.optJSONObject("imported")?.let { imported ->
                        for (key in imported.keys()) Text("${key.replaceFirstChar { it.uppercase() }}: ${imported.optInt(key)} imported")
                    }
                    JsonStringList(result!!.optJSONArray("warnings"), "Warnings")
                } else {
                    Text("The server will inspect the selected file before any changes are made.")
                    if (preview == null) {
                        Text("Import mode: create a new bot or overwrite an existing bot.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { mode = "create" }) { Text(if (mode == "create") "✓ Create" else "Create") }
                            TextButton(onClick = { mode = "overwrite" }) { Text(if (mode == "overwrite") "✓ Overwrite" else "Overwrite") }
                        }
                        if (mode == "overwrite") OutlinedTextField(targetId, { targetId = it },
                            label = { Text("Target bot ID") }, singleLine = true)
                        if (encrypted || requiresPassphrase) OutlinedTextField(passphrase, { passphrase = it },
                            label = { Text("Backup passphrase") }, singleLine = true)
                    } else {
                        if (encrypted) OutlinedTextField(passphrase, { passphrase = it },
                            label = { Text("Backup passphrase") }, singleLine = true)
                        val manifest = preview!!.optJSONObject("manifest")
                        Text("Source: ${manifest?.optString("source_bot_name").orEmpty().ifBlank { "Unknown bot" }}")
                        if (manifest != null) Text("Exported: ${manifest.optString("exported_at")} · schema ${manifest.optInt("schema_version")}")
                        val profile = preview!!.optJSONObject("profile")
                        if (profile != null) {
                            Text("Profile: ${profile.optString("display_name").ifBlank { "Unnamed bot" }}")
                            if (profile.optString("timezone").isNotBlank()) Text("Timezone: ${profile.optString("timezone")}")
                            if (profile.has("is_active")) Text(if (profile.optBoolean("is_active")) "Profile active" else "Profile inactive")
                        }
                        val plan = preview!!.optJSONObject("restore_plan")
                        if (plan != null) {
                            Text("Mode: ${plan.optString("mode").ifBlank { mode }}")
                            if (plan.optString("target_bot_id").isNotBlank()) Text("Target bot: ${plan.optString("target_bot_id")}")
                            if (plan.optBoolean("will_create_bot")) Text("A new bot will be created.")
                            if (plan.optBoolean("will_restore_workspace")) Text("Workspace data will be restored.")
                            val matches = plan.optJSONObject("dependency_matches")
                            matches?.let { Text("Dependency matches: ${it.toString()}") }
                        }
                        val sections = preview!!.optJSONArray("sections") ?: JSONArray()
                        Text("Sections to import", style = MaterialTheme.typography.titleSmall)
                        for (i in 0 until sections.length()) {
                            val item = sections.optJSONObject(i) ?: continue
                            Text("${item.optString("key")}: ${item.optInt("count")} items" +
                                if (item.optBoolean("conflict")) " · conflict" else "")
                        }
                        JsonStringList(preview!!.optJSONArray("conflicts"), "Conflicts")
                        JsonStringList(preview!!.optJSONArray("missing"), "Missing")
                        JsonStringList(preview!!.optJSONArray("warnings"), "Warnings")
                        if (encrypted && manifest == null) Text(
                            if (requiresPassphrase) "Enter the passphrase to inspect this encrypted backup."
                            else "The passphrase was not accepted. Check it and preview again.",
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                if (busy) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when {
                busy -> Unit
                result != null -> TextButton(onClick = onDismiss) { Text("Done") }
                preview == null || (encrypted && preview?.optJSONObject("manifest") == null) -> Button(
                    onClick = { previewBackup() }, enabled = mode != "overwrite" || targetId.isNotBlank()) { Text("Preview") }
                else -> Button(onClick = { importBackup() }) { Text("Confirm import") }
            }
        },
        dismissButton = {
            if (result == null && preview != null) TextButton(onClick = { preview = null; error = "" }, enabled = !busy) { Text("Change options") }
            else TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun JsonStringList(values: JSONArray?, title: String) {
    if (values == null || values.length() == 0) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    for (i in 0 until values.length()) Text("• ${values.optString(i)}")
}
