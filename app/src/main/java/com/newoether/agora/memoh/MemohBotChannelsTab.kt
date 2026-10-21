package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class BotChannelItem(
    val platform: String,
    val enabled: Boolean,
    val webhookUrl: String = "",
    val secretConfigured: Boolean = false,
    val raw: JSONObject = JSONObject(),
)

private val SUPPORTED_PLATFORMS = listOf(
    "telegram" to "Telegram Bot",
    "discord" to "Discord Bot",
    "slack" to "Slack App",
    "webhook" to "Generic Webhook",
    "feishu" to "Feishu / Lark",
    "dingtalk" to "DingTalk"
)

@Composable
internal fun MemohBotChannelsTab(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<BotChannelItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var configurePlatform by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = ""
        scope.launch {
            runCatching { api.botChannels(botId) }
                .onSuccess { list ->
                    channels = list.map { obj ->
                        val platform = obj.optString("platform").ifBlank { obj.optString("id") }
                        BotChannelItem(
                            platform = platform,
                            enabled = obj.optBoolean("enabled", true),
                            webhookUrl = obj.optString("webhook_url"),
                            secretConfigured = obj.optBoolean("secret_configured", true),
                            raw = obj
                        )
                    }
                }
                .onFailure { error = it.message ?: "Failed to load channel configurations" }
            loading = false
        }
    }

    LaunchedEffect(botId) {
        refresh()
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Channels & Integrations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Connect this bot to messaging platforms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canManage) {
                Box {
                    Button(
                        onClick = {
                            haptics.selection()
                            showAddMenu = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        val configuredPlatforms = channels.map { it.platform }.toSet()
                        SUPPORTED_PLATFORMS.filterNot { it.first in configuredPlatforms }.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showAddMenu = false
                                    configurePlatform = key
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (loading && channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (error.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(error, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (channels.isEmpty() && !loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No channels configured yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canManage) {
                        Text("Tap '+ Add' above to integrate Telegram, Discord, or webhooks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(channels, key = { it.platform }) { item ->
                    ChannelCard(
                        channel = item,
                        canManage = canManage,
                        onToggle = { isChecked ->
                            haptics.toggle(isChecked)
                            scope.launch {
                                runCatching { api.updateBotChannelStatus(botId, item.platform, isChecked) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = it.message ?: "Failed to update channel status" }
                            }
                        },
                        onConfigure = { configurePlatform = item.platform },
                        onDelete = { deleteCandidate = item.platform }
                    )
                }
            }
        }
    }

    configurePlatform?.let { platform ->
        val existing = channels.find { it.platform == platform }
        var tokenInput by remember { mutableStateOf("") }
        var webhookInput by remember { mutableStateOf(existing?.webhookUrl.orEmpty()) }
        var saving by remember { mutableStateOf(false) }

        val label = SUPPORTED_PLATFORMS.find { it.first == platform }?.second ?: platform

        AlertDialog(
            onDismissRequest = { if (!saving) configurePlatform = null },
            title = { Text("Configure $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Bot Token / API Key") },
                        placeholder = { Text("Leave blank to keep existing") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webhookInput,
                        onValueChange = { webhookInput = it },
                        label = { Text("Webhook Endpoint URL (Optional)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saving = true
                        haptics.confirm()
                        scope.launch {
                            val payload = JSONObject()
                                .put("platform", platform)
                                .put("enabled", true)
                            if (tokenInput.isNotBlank()) payload.put("token", tokenInput.trim())
                            if (webhookInput.isNotBlank()) payload.put("webhook_url", webhookInput.trim())

                            runCatching { api.upsertBotChannel(botId, platform, payload) }
                                .onSuccess {
                                    configurePlatform = null
                                    refresh()
                                }
                                .onFailure { error = it.message ?: "Failed to save channel" }
                            saving = false
                        }
                    },
                    enabled = !saving
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { configurePlatform = null }, enabled = !saving) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteCandidate?.let { platform ->
        val label = SUPPORTED_PLATFORMS.find { it.first == platform }?.second ?: platform
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Disconnect $label?") },
            text = { Text("This bot will no longer receive or respond to messages from $label.") },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.destructiveConfirmed()
                        val target = platform
                        deleteCandidate = null
                        scope.launch {
                            runCatching { api.deleteBotChannel(botId, target) }
                                .onSuccess { refresh() }
                                .onFailure { error = it.message ?: "Failed to remove channel" }
                        }
                    }
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChannelCard(
    channel: BotChannelItem,
    canManage: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
) {
    val platformName = SUPPORTED_PLATFORMS.find { it.first == channel.platform }?.second ?: channel.platform

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(platformName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (channel.enabled) "Connected · Active" else "Connected · Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (channel.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canManage) {
                IconButton(onClick = onConfigure) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Switch(checked = channel.enabled, onCheckedChange = onToggle)
            }
        }
    }
}
