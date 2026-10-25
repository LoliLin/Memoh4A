package com.newoether.agora.memoh

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
                label = { Text("Display name") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("System name (slug)") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text("Avatar URL") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = timezone,
                onValueChange = { timezone = it },
                label = { Text("Timezone (e.g. Asia/Shanghai, UTC)") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Bot is active", modifier = Modifier.weight(1f))
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
                                onError(e.message ?: "Failed to update bot settings")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Saving…" else "Save settings")
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
                .onFailure { onError(it.message ?: "Failed to load user access grants") }
            loading = false
        }
    }

    LaunchedEffect(botId) { refreshGrants() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("User access grants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
            if (canManage) {
                TextButton(onClick = { showAddDialog = true }) { Text("+ Add member") }
            }
        }

        if (grants.isEmpty() && !loading) {
            Text(
                "No explicit user grants assigned to this bot.",
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
                                            Text("Owner", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Text(
                                    "ID: ${grant.userId}",
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
                                    Text("Revoke", color = MaterialTheme.colorScheme.error)
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
            title = { Text("Grant bot access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetUserId,
                        onValueChange = { targetUserId = it },
                        label = { Text("User ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Permissions", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = grantChat, onCheckedChange = { grantChat = it })
                        Text("chat (talk with this bot)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = grantManage, onCheckedChange = { grantManage = it })
                        Text("manage (configure bot settings & agents)")
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
                                .onFailure { onError(it.message ?: "Failed to create access grant") }
                        }
                    },
                ) { Text("Grant access") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (deletingGrantId != null) {
        val grantId = deletingGrantId!!
        AlertDialog(
            onDismissRequest = { deletingGrantId = null },
            title = { Text("Revoke access?") },
            text = { Text("The user loses permissions immediately.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { api.deleteBotUserGrant(botId, grantId) }
                                .onSuccess {
                                    deletingGrantId = null
                                    refreshGrants()
                                }
                                .onFailure { onError(it.message ?: "Failed to revoke grant") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { deletingGrantId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun BotContainerSection(api: MemohApi, botId: String, onOpenWorkspace: () -> Unit) {
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(botId) {
        loading = true
        runCatching { api.botContainer(botId) }
            .onSuccess { info = it }
            .onFailure { error = it.message ?: "Container information unavailable" }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Workspace container", style = MaterialTheme.typography.titleMedium)
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        info?.let { json ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BotInfoRow("Status", json.optString("status").ifBlank { "Ready" })
                    BotInfoRow("Workdir", json.optString("workdir").ifBlank { "/data" })
                    BotInfoRow("Image", json.optString("image").ifBlank { "Default sandbox" })
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text("Open workspace file explorer")
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
