package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Section bodies reused by the Bot settings pages.
 *
 * They were originally private tabs inside a modal sheet; the sheet is now a settings destination,
 * so the bodies live here and the dialog is gone. Each one is a self-contained scroll owner.
 */

@Composable
internal fun BotGeneralSection(
    api: MemohApi,
    bot: MemohBot,
    canManage: Boolean,
    onBotUpdated: (MemohBot) -> Unit,
    onError: (String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var displayName by remember(bot.id) { mutableStateOf(bot.displayName) }
    var name by remember(bot.id) { mutableStateOf(bot.name) }
    var avatarUrl by remember(bot.id) { mutableStateOf(bot.avatarUrl) }
    var timezone by remember(bot.id) { mutableStateOf(bot.timezone) }
    var isActive by remember(bot.id) { mutableStateOf(bot.isActive) }
    var saving by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(uiResources.getString(R.string.memoh_ui_display_name)) },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(uiResources.getString(R.string.memoh_ui_system_name_slug)) },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text(uiResources.getString(R.string.memoh_ui_avatar_url)) },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            MemohTimeZoneField(timezone, { timezone = it }, enabled = canManage && !saving)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(uiResources.getString(R.string.memoh_ui_bot_is_active), modifier = Modifier.weight(1f))
                Switch(checked = isActive, onCheckedChange = { isActive = it }, enabled = canManage && !saving)
            }
        }
        if (canManage) {
            item {
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                onBotUpdated(
                                    api.updateBot(
                                        botId = bot.id,
                                        displayName = displayName,
                                        name = name,
                                        avatarUrl = avatarUrl,
                                        timezone = timezone,
                                        isActive = isActive,
                                    ),
                                )
                            } catch (e: Exception) {
                                onError(e.message ?: uiResources.getString(R.string.memoh_ui_failed_to_update_bot_settings))
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) uiResources.getString(R.string.memoh_ui_saving) else uiResources.getString(R.string.memoh_ui_save_settings))
                }
            }
        }
    }
}

@Composable
internal fun BotAccessSection(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    onError: (String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var grants by remember { mutableStateOf<List<MemohUserGrant>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var targetUserId by remember { mutableStateOf("") }
    var grantChat by remember { mutableStateOf(true) }
    var grantManage by remember { mutableStateOf(false) }
    var deletingGrantId by remember { mutableStateOf<String?>(null) }

    fun refreshGrants() {
        scope.launch {
            loading = true
            runCatching { api.botUserGrants(botId) }
                .onSuccess { grants = it }
                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_load_user_access_grants)) }
            loading = false
        }
    }

    LaunchedEffect(botId) { refreshGrants() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(uiResources.getString(R.string.memoh_ui_user_access_grants), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
            if (canManage) {
                TextButton(onClick = { showAddDialog = true }) { Text(uiResources.getString(R.string.memoh_ui_add_member)) }
            }
        }

        if (grants.isEmpty() && !loading) {
            Text(
                uiResources.getString(R.string.memoh_ui_no_explicit_user_grants_assigned_to_this_bot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(grants, key = { it.id }) { grant ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        grant.userDisplayName.ifBlank { grant.userUsername.ifBlank { grant.userId } },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (grant.isOwner) {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(uiResources.getString(R.string.memoh_ui_owner), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Text(
                                    uiResources.getString(R.string.memoh_ui_id_1_s, grant.userId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    grant.permissions.forEach { perm ->
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(perm, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            if (canManage && !grant.isOwner) {
                                TextButton(onClick = { deletingGrantId = grant.id }) {
                                    Text(uiResources.getString(R.string.memoh_ui_revoke), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_grant_bot_access)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemohResourceField(
                        api, "/bots/${android.net.Uri.encode(botId)}/user-access/candidates",
                        uiResources.getString(R.string.memoh_ui_user), targetUserId, { targetUserId = it }, serverSearch = true,
                    )
                    Text(uiResources.getString(R.string.memoh_ui_permissions), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = grantChat, onCheckedChange = { grantChat = it })
                        Text(uiResources.getString(R.string.memoh_ui_chat_talk_with_this_bot))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = grantManage, onCheckedChange = { grantManage = it })
                        Text(uiResources.getString(R.string.memoh_ui_manage_configure_bot_settings_agents))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = targetUserId.isNotBlank() && (grantChat || grantManage),
                    onClick = {
                        val perms = buildList {
                            if (grantChat) add("chat")
                            if (grantManage) add("manage")
                        }
                        scope.launch {
                            runCatching { api.createBotUserGrant(botId, targetUserId, perms) }
                                .onSuccess {
                                    showAddDialog = false
                                    targetUserId = ""
                                    refreshGrants()
                                }
                                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_create_access_grant)) }
                        }
                    },
                ) { Text(uiResources.getString(R.string.memoh_ui_grant_access)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            },
        )
    }

    if (deletingGrantId != null) {
        val grantId = deletingGrantId!!
        AlertDialog(
            onDismissRequest = { deletingGrantId = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_revoke_access)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_the_user_loses_permissions_immediately)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { api.deleteBotUserGrant(botId, grantId) }
                                .onSuccess {
                                    deletingGrantId = null
                                    refreshGrants()
                                }
                                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_revoke_grant)) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(uiResources.getString(R.string.memoh_ui_revoke)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingGrantId = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            },
        )
    }
}

@Composable
internal fun BotContainerSection(api: MemohApi, botId: String, onOpenWorkspace: () -> Unit) {
    val uiResources = LocalContext.current.resources
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(botId) {
        loading = true
        runCatching { api.botContainer(botId) }
            .onSuccess { info = it }
            .onFailure { error = it.message ?: uiResources.getString(R.string.memoh_ui_container_information_unavailable) }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(uiResources.getString(R.string.memoh_ui_workspace_container), style = MaterialTheme.typography.titleMedium)
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        info?.let { json ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BotInfoRow(uiResources.getString(R.string.memoh_ui_status), json.optString("status").ifBlank { uiResources.getString(R.string.memoh_ui_ready) })
                    BotInfoRow(uiResources.getString(R.string.memoh_ui_workdir), json.optString("workdir").ifBlank { "/data" })
                    BotInfoRow(uiResources.getString(R.string.memoh_ui_image), json.optString("image").ifBlank { uiResources.getString(R.string.memoh_ui_default_sandbox) })
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text(uiResources.getString(R.string.memoh_ui_open_workspace_file_explorer))
        }
    }
}

@Composable
private fun BotInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
