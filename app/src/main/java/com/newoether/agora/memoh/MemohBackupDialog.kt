package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.newoether.agora.ui.components.rememberSecretVisible
import com.newoether.agora.ui.components.secretVisualTransformation
import com.newoether.agora.ui.components.SecretVisibilityToggle
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
internal fun MemohBackupSettingsTab(
    api: MemohApi,
    bot: MemohBot,
    status: String,
    canManage: Boolean,
    onExport: (sections: List<String>, passphrase: String) -> Unit,
    onChooseImport: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    var summary by remember(bot.id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(bot.id) { mutableStateOf("") }
    var loading by remember(bot.id) { mutableStateOf(true) }
    var selectedSections by remember(bot.id) { mutableStateOf(MemohBackupSection.entries.toSet()) }
    var passphraseVisible by rememberSecretVisible()
    var passphrase by remember(bot.id) { mutableStateOf("") }
    LaunchedEffect(bot.id) {
        try { summary = api.botBackupSummary(bot.id) }
        catch (t: Exception) { error = t.message ?: uiResources.getString(R.string.memoh_ui_could_not_load_backup_summary) }
        finally { loading = false }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiResources.getString(R.string.memoh_ui_export_or_restore_this_bot_s_server_data), style = MaterialTheme.typography.bodySmall)
        if (!canManage) {
            Text(uiResources.getString(R.string.memoh_ui_import_is_available_only_to_bot_managers), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Text(uiResources.getString(R.string.memoh_ui_export_sections), style = MaterialTheme.typography.titleSmall) }
            items(MemohBackupSection.entries, key = { it.key }) { section ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = section in selectedSections,
                        onCheckedChange = { checked ->
                            selectedSections = if (checked) selectedSections + section else selectedSections - section
                        },
                    )
                    Text(backupSectionLabel(section.key))
                }
            }
            item {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_optional_encryption_passphrase)) },
                    visualTransformation = secretVisualTransformation(passphraseVisible),
                    trailingIcon = { SecretVisibilityToggle(passphraseVisible) { passphraseVisible = !passphraseVisible } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                if (loading) CircularProgressIndicator()
                else summary?.let { BackupSummary(it) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TextButton(onClick = onChooseImport, enabled = canManage) { Text(uiResources.getString(R.string.memoh_ui_import)) }
            Button(
                onClick = { onExport(selectedSections.map { it.key }, passphrase) },
                enabled = !loading && summary != null && selectedSections.isNotEmpty(),
            ) { Text(uiResources.getString(R.string.memoh_ui_export)) }
        }
    }
}

@Composable
private fun BackupSummary(data: JSONObject) {
    val uiResources = LocalContext.current.resources
    val profile = data.optJSONObject("profile")
    if (profile != null) {
        Text(profile.optString("display_name").ifBlank { uiResources.getString(R.string.memoh_ui_bot_profile) }, style = MaterialTheme.typography.titleMedium)
        if (profile.optString("timezone").isNotBlank()) Text(uiResources.getString(R.string.memoh_ui_timezone_1_s, profile.optString("timezone")))
        if (profile.has("is_active")) Text(if (profile.optBoolean("is_active")) uiResources.getString(R.string.memoh_ui_active) else uiResources.getString(R.string.memoh_ui_inactive))
    }
    val sections = data.optJSONArray("sections") ?: JSONArray()
    if (sections.length() == 0) Text(uiResources.getString(R.string.memoh_ui_no_export_sections_were_reported))
    for (i in 0 until sections.length()) {
        val section = sections.optJSONObject(i) ?: continue
        val key = section.optString("key").ifBlank { uiResources.getString(R.string.memoh_ui_section) }
        val count = section.optInt("count")
        val targetCount = section.optInt("target_count", -1)
        Text("${backupSectionLabel(key)} · $count" +
            if (targetCount >= 0) uiResources.getString(R.string.memoh_ui_target_has_1_s, targetCount) else "")
        val items = section.optJSONArray("items")
        if (items != null) for (j in 0 until minOf(items.length(), 8))
            Text("  • ${items.optString(j)}", style = MaterialTheme.typography.bodySmall)
        if (section.optBoolean("sensitive")) Text(uiResources.getString(R.string.memoh_ui_contains_sensitive_data), color = MaterialTheme.colorScheme.error,
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
    val uiResources = LocalContext.current.resources
    var passphraseVisible by rememberSecretVisible()
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
            } catch (t: Exception) { error = t.message ?: uiResources.getString(R.string.memoh_ui_backup_preview_failed) }
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
            } catch (t: Exception) { error = t.message ?: uiResources.getString(R.string.memoh_ui_backup_import_failed) }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (result == null) uiResources.getString(R.string.memoh_ui_import_bot_backup) else uiResources.getString(R.string.memoh_ui_import_complete)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result != null) {
                    Text(uiResources.getString(R.string.memoh_ui_bot_id_1_s, result!!.optString("bot_id")))
                    Text(if (result!!.optBoolean("created")) uiResources.getString(R.string.memoh_ui_a_bot_was_created) else uiResources.getString(R.string.memoh_ui_backup_applied_to_an_existing_bot))
                    result!!.optJSONObject("imported")?.let { imported ->
                        for (key in imported.keys()) Text(uiResources.getString(R.string.memoh_ui_1_s_2_s_imported, backupSectionLabel(key), imported.optInt(key)))
                    }
                    JsonStringList(result!!.optJSONArray("warnings"), uiResources.getString(R.string.memoh_ui_warnings))
                } else {
                    Text(uiResources.getString(R.string.memoh_ui_the_server_will_inspect_the_selected_file_before_any_changes_are_made))
                    if (preview == null) {
                        Text(uiResources.getString(R.string.memoh_ui_import_mode_create_a_new_bot_or_overwrite_an_existing_bot))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { mode = "create" }) { Text(if (mode == "create") uiResources.getString(R.string.memoh_ui_create_11fedf) else uiResources.getString(R.string.memoh_ui_create)) }
                            TextButton(onClick = { mode = "overwrite" }) { Text(if (mode == "overwrite") uiResources.getString(R.string.memoh_ui_overwrite_8f21f4) else uiResources.getString(R.string.memoh_ui_overwrite)) }
                        }
                        if (mode == "overwrite") MemohResourceField(
                            api, "/bots", uiResources.getString(R.string.memoh_ui_target_bot), targetId, { targetId = it },
                            enabled = !busy, currentLabel = if (targetId == bot.id) bot.title else "",
                        )
                        if (encrypted || requiresPassphrase) OutlinedTextField(passphrase, { passphrase = it },
                            label = { Text(uiResources.getString(R.string.memoh_ui_backup_passphrase)) }, singleLine = true,
                            enabled = !busy,
                            visualTransformation = secretVisualTransformation(passphraseVisible),
                            trailingIcon = { SecretVisibilityToggle(passphraseVisible) { passphraseVisible = !passphraseVisible } },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                    } else {
                        if (encrypted) OutlinedTextField(passphrase, { passphrase = it },
                            label = { Text(uiResources.getString(R.string.memoh_ui_backup_passphrase)) }, singleLine = true,
                            enabled = !busy,
                            visualTransformation = secretVisualTransformation(passphraseVisible),
                            trailingIcon = { SecretVisibilityToggle(passphraseVisible) { passphraseVisible = !passphraseVisible } },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                        val manifest = preview!!.optJSONObject("manifest")
                        Text(uiResources.getString(R.string.memoh_ui_source_1_s, manifest?.optString("source_bot_name").orEmpty().ifBlank { uiResources.getString(R.string.memoh_ui_unknown_bot) }))
                        if (manifest != null) Text(uiResources.getString(R.string.memoh_ui_exported_1_s_schema_2_s, manifest.optString("exported_at"), manifest.optInt("schema_version")))
                        val profile = preview!!.optJSONObject("profile")
                        if (profile != null) {
                            Text(uiResources.getString(R.string.memoh_ui_profile_1_s, profile.optString("display_name").ifBlank { uiResources.getString(R.string.memoh_ui_unnamed_bot) }))
                            if (profile.optString("timezone").isNotBlank()) Text(uiResources.getString(R.string.memoh_ui_timezone_1_s, profile.optString("timezone")))
                            if (profile.has("is_active")) Text(if (profile.optBoolean("is_active")) uiResources.getString(R.string.memoh_ui_profile_active) else uiResources.getString(R.string.memoh_ui_profile_inactive))
                        }
                        val plan = preview!!.optJSONObject("restore_plan")
                        if (plan != null) {
                            Text(uiResources.getString(R.string.memoh_ui_mode_1_s, plan.optString("mode").ifBlank { mode }))
                            if (plan.optString("target_bot_id").isNotBlank()) Text(uiResources.getString(R.string.memoh_ui_target_bot_1_s, plan.optString("target_bot_id")))
                            if (plan.optBoolean("will_create_bot")) Text(uiResources.getString(R.string.memoh_ui_a_new_bot_will_be_created))
                            if (plan.optBoolean("will_restore_workspace")) Text(uiResources.getString(R.string.memoh_ui_workspace_data_will_be_restored))
                            val matches = plan.optJSONObject("dependency_matches")
                            matches?.let { Text(uiResources.getString(R.string.memoh_ui_dependency_matches_1_s, it.toString())) }
                        }
                        val sections = preview!!.optJSONArray("sections") ?: JSONArray()
                        Text(uiResources.getString(R.string.memoh_ui_sections_to_import), style = MaterialTheme.typography.titleSmall)
                        for (i in 0 until sections.length()) {
                            val item = sections.optJSONObject(i) ?: continue
                            Text(uiResources.getString(R.string.memoh_ui_1_s_2_s_items, item.optString("key"), item.optInt("count")) +
                                if (item.optBoolean("conflict")) uiResources.getString(R.string.memoh_ui_conflict) else "")
                        }
                        JsonStringList(preview!!.optJSONArray("conflicts"), uiResources.getString(R.string.memoh_ui_conflicts))
                        JsonStringList(preview!!.optJSONArray("missing"), uiResources.getString(R.string.memoh_ui_missing))
                        JsonStringList(preview!!.optJSONArray("warnings"), uiResources.getString(R.string.memoh_ui_warnings))
                        if (encrypted && manifest == null) Text(
                            if (requiresPassphrase) uiResources.getString(R.string.memoh_ui_enter_the_passphrase_to_inspect_this_encrypted_backup)
                            else uiResources.getString(R.string.memoh_ui_the_passphrase_was_not_accepted_check_it_and_preview_again),
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
                result != null -> TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_done)) }
                preview == null || (encrypted && preview?.optJSONObject("manifest") == null) -> Button(
                    onClick = { previewBackup() }, enabled = mode != "overwrite" || targetId.isNotBlank()) { Text(uiResources.getString(R.string.memoh_ui_preview)) }
                else -> Button(onClick = { importBackup() }) { Text(uiResources.getString(R.string.memoh_ui_confirm_import)) }
            }
        },
        dismissButton = {
            if (result == null && preview != null) TextButton(onClick = { preview = null; error = "" }, enabled = !busy) { Text(uiResources.getString(R.string.memoh_ui_change_options)) }
            else TextButton(onClick = onDismiss, enabled = !busy) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
        },
    )
}

@Composable
private fun JsonStringList(values: JSONArray?, title: String) {
    if (values == null || values.length() == 0) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    for (i in 0 until values.length()) Text("• ${values.optString(i)}")
}

@Composable
private fun backupSectionLabel(key: String): String {
    val uiResources = LocalContext.current.resources
    return when (key) {
        "profile" -> uiResources.getString(R.string.memoh_ui_bot_profile)
        "settings" -> uiResources.getString(R.string.memoh_ui_settings)
        "models" -> uiResources.getString(R.string.memoh_ui_models)
        "acl" -> uiResources.getString(R.string.memoh_ui_access)
        "channels" -> uiResources.getString(R.string.memoh_ui_channels)
        "mcp" -> uiResources.getString(R.string.memoh_ui_mcp)
        "schedules" -> uiResources.getString(R.string.memoh_ui_schedules)
        "history" -> uiResources.getString(R.string.memoh_ui_chat_history)
        "assets" -> uiResources.getString(R.string.memoh_ui_attachments)
        "workspace" -> uiResources.getString(R.string.memoh_ui_workspace_files_7e0294)
        else -> key
    }
}
