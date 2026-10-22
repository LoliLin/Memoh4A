package com.newoether.agora.memoh

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.newoether.agora.ui.common.LocalAgoraHaptics
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.json.JSONObject

private val BOT_TABS = listOf(
    "overview" to "Overview",
    "general" to "General",
    "agents" to "Agents",
    "access" to "Access",
    "container" to "Container",
    "memory" to "Memory",
    "channels" to "Channels",
    "remote-runtime" to "Runtime",
    "advanced" to "Advanced",
    "mcp" to "MCP",
    "apps" to "Apps",
)

/**
 * Native Bot Detail dialog aligned with apps/web/src/pages/bots/detail.vue (11 tabs).
 * Enforces canManageBot permissions strictly: read-only access when "manage" permission is absent.
 */
@Composable
internal fun MemohBotDetailDialog(
    api: MemohApi,
    bot: MemohBot,
    agentController: MemohAgentController,
    onDismiss: () -> Unit,
    onBotUpdated: (MemohBot) -> Unit,
    onBotDeleted: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalAgoraHaptics.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var currentBot by remember(bot.id) { mutableStateOf(bot) }
    var loadingBot by remember { mutableStateOf(false) }
    // The parent re-supplies the bot after an edit or a rename elsewhere. Keying
    // the local copy on `bot.id` alone captured the initial value forever, so
    // those updates never reached this sheet. Adopt the incoming value whenever
    // it actually differs.
    LaunchedEffect(bot) {
        if (bot != currentBot) currentBot = bot
    }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }

    val canManage = currentBot.canManage

    fun notify(msg: String, isError: Boolean = false) {
        message = msg
        messageIsError = isError
    }

    LaunchedEffect(bot.id) {
        loadingBot = true
        runCatching { api.getBot(bot.id) }
            .onSuccess {
                currentBot = it
                onBotUpdated(it)
            }
            .onFailure { notify(it.message ?: "Failed to refresh bot details", isError = true) }
        loadingBot = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(currentBot.title, style = MaterialTheme.typography.titleLarge)
                        Text("ID: ${currentBot.id}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (loadingBot) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                if (!canManage) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            "Read-only mode: your account does not have 'manage' permission on this bot.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // 11 Tabs matching detail.vue
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    edgePadding = 8.dp
                ) {
                    BOT_TABS.forEachIndexed { index, (_, label) ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                haptics.selection()
                                selectedTabIndex = index
                            },
                            text = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(bottom = 8.dp))

                Box(Modifier.weight(1f)) {
                    when (BOT_TABS[selectedTabIndex].first) {
                        "overview" -> BotOverviewTab(
                            bot = currentBot,
                            canManage = canManage,
                            onDelete = {
                                scope.launch {
                                    runCatching { api.deleteBot(currentBot.id) }
                                        .onSuccess {
                                            onBotDeleted()
                                            onDismiss()
                                        }
                                        .onFailure { notify(it.message ?: "Delete failed", isError = true) }
                                }
                            }
                        )
                        "general" -> BotGeneralTab(
                            api = api,
                            bot = currentBot,
                            canManage = canManage,
                            onBotUpdated = {
                                currentBot = it
                                onBotUpdated(it)
                                notify("Bot settings updated successfully")
                            },
                            onError = { notify(it, isError = true) }
                        )
                        "remote-runtime" -> MemohBotRuntimeTab(api = api, botId = currentBot.id, canManage = canManage)
                        "container" -> BotContainerTab(api = api, botId = currentBot.id, onOpenWorkspace = onOpenWorkspace)
                        "memory" -> BotMemoryTabShortcut(onOpenMemory = onOpenMemory)
                        "channels" -> MemohBotChannelsTab(api = api, botId = currentBot.id, canManage = canManage)
                        "advanced" -> MemohBotAdvancedTab(api = api, botId = currentBot.id, canManage = canManage)
                        "access" -> BotAccessTab(api = api, botId = currentBot.id, canManage = canManage, onError = { notify(it, isError = true) })
                        "agents" -> BotAgentsTab(api = api, botId = currentBot.id, controller = agentController, canManage = canManage, onError = { notify(it, isError = true) })
                        "mcp" -> MemohBotMcpTab(api = api, botId = currentBot.id, canManage = canManage)
                        "apps" -> MemohBotAppsTab(api = api, botId = currentBot.id, canManage = canManage)
                    }
                }
            }
        }
    }
}

@Composable
private fun BotOverviewTab(
    bot: MemohBot,
    canManage: Boolean,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Basic Information", style = MaterialTheme.typography.titleMedium)
                    InfoRow("Title", bot.title)
                    InfoRow("Name (slug)", bot.name.ifBlank { "—" })
                    InfoRow("Display Name", bot.displayName.ifBlank { "—" })
                    InfoRow("Status", bot.status.ifBlank { if (bot.isActive) "Active" else "Inactive" })
                    InfoRow("Timezone", bot.timezone.ifBlank { "Default / System" })
                    InfoRow("Created At", bot.createdAt.ifBlank { "—" })
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your Permissions", style = MaterialTheme.typography.titleMedium)
                    if (bot.currentUserPermissions.isEmpty()) {
                        Text("Implicit full management (no granular permission constraints)",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            bot.currentUserPermissions.forEach { perm ->
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(perm, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canManage) {
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Bot")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Bot '${bot.title}'?") },
            text = { Text("This will permanently delete the bot, its workspace data, and all conversations. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BotGeneralTab(
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
                label = { Text("Display Name") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("System Name (Slug)") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text("Avatar URL") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = timezone,
                onValueChange = { timezone = it },
                label = { Text("Timezone (e.g. Asia/Shanghai, UTC)") },
                enabled = canManage && !saving,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Bot Active Status", modifier = Modifier.weight(1f))
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
                                val updated = api.updateBot(
                                    botId = bot.id,
                                    displayName = displayName,
                                    name = name,
                                    avatarUrl = avatarUrl,
                                    timezone = timezone,
                                    isActive = isActive,
                                )
                                onBotUpdated(updated)
                            } catch (e: Exception) {
                                onError(e.message ?: "Failed to update bot settings")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving…" else "Save Settings")
                }
            }
        }
    }
}

@Composable
private fun BotAccessTab(
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
            Text("User Access Grants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
            if (canManage) {
                TextButton(onClick = { showAddDialog = true }) { Text("+ Add Member") }
            }
        }

        if (grants.isEmpty() && !loading) {
            Text("No explicit user grants assigned to this bot.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(grants, key = { it.id }) { grant ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(grant.userDisplayName.ifBlank { grant.userUsername.ifBlank { grant.userId } },
                                        style = MaterialTheme.typography.titleSmall)
                                    if (grant.isOwner) {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                            Text("Owner", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Text("ID: ${grant.userId}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    grant.permissions.forEach { perm ->
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(perm, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall)
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
            title = { Text("Grant Bot Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetUserId,
                        onValueChange = { targetUserId = it },
                        label = { Text("User ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Permissions:", style = MaterialTheme.typography.labelMedium)
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
                    }
                ) { Text("Grant Access") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (deletingGrantId != null) {
        val gid = deletingGrantId!!
        AlertDialog(
            onDismissRequest = { deletingGrantId = null },
            title = { Text("Revoke Access?") },
            text = { Text("Are you sure you want to remove this user grant? The user will lose permissions immediately.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { api.deleteBotUserGrant(botId, gid) }
                                .onSuccess {
                                    deletingGrantId = null
                                    refreshGrants()
                                }
                                .onFailure { onError(it.message ?: "Failed to revoke grant") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { deletingGrantId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BotContainerTab(api: MemohApi, botId: String, onOpenWorkspace: () -> Unit) {
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
        Text("Workspace Container", style = MaterialTheme.typography.titleMedium)
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        info?.let { json ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Status", json.optString("status").ifBlank { "Ready" })
                    InfoRow("Workdir", json.optString("workdir").ifBlank { "/data" })
                    InfoRow("Image", json.optString("image").ifBlank { "Default Sandbox" })
                }
            }
        }

        Button(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text("Open Workspace File Explorer")
        }
    }
}

@Composable
private fun BotMemoryTabShortcut(onOpenMemory: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bot Memory", style = MaterialTheme.typography.titleMedium)
        Text("Manage long-term memories, factual graph associations, ingestion runs, and compaction decay.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpenMemory, modifier = Modifier.fillMaxWidth()) {
            Text("Open Full Memory Manager")
        }
    }
}

@Composable
private fun BotPlaceholderTab(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("This capability is managed by the server backend. Configure advanced parameters via Web UI at app.memoh.net.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
