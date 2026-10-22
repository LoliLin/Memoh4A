package com.newoether.agora.memoh

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import kotlinx.coroutines.launch

@Composable
internal fun MemohBotAdvancedTab(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()
    var defaultEffect by remember { mutableStateOf("allow") }
    var starterPrompts by remember { mutableStateOf<List<MemohStarterPrompt>>(emptyList()) }
    var checks by remember { mutableStateOf<List<MemohBotCheck>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshingPrompts by remember { mutableStateOf(false) }
    var clearingLogs by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun refresh() {
        loading = true
        error = ""
        scope.launch {
            val effectResult = runCatching { api.botAclDefaultEffect(botId) }
            val promptsResult = runCatching { api.botStarterPrompts(botId) }
            val checksResult = runCatching { api.botChecks(botId) }

            defaultEffect = effectResult.getOrDefault("allow")
            starterPrompts = promptsResult.getOrDefault(emptyList())
            checks = checksResult.getOrDefault(emptyList())

            loading = false
        }
    }

    LaunchedEffect(botId) {
        refresh()
    }

    Column(modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Security policies, health checks & lifecycle defaults",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                haptics.selection()
                refresh()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (error.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (message.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (loading && starterPrompts.isEmpty() && checks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Section 1: Default ACL Effect
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Access Control Default Effect",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Policy decision applied when no explicit user or channel rule matches the conversation request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = defaultEffect.equals("allow", ignoreCase = true),
                                    onClick = {
                                        if (canManage && !defaultEffect.equals("allow", ignoreCase = true)) {
                                            haptics.selection()
                                            defaultEffect = "allow"
                                            scope.launch {
                                                runCatching { api.setBotAclDefaultEffect(botId, "allow") }
                                                    .onSuccess {
                                                        haptics.confirm()
                                                        message = "Default effect set to Allow"
                                                    }
                                                    .onFailure {
                                                        error = it.message ?: "Failed to set default effect"
                                                    }
                                            }
                                        }
                                    },
                                    enabled = canManage,
                                    label = { Text("Allow (Permissive)") }
                                )
                                FilterChip(
                                    selected = defaultEffect.equals("deny", ignoreCase = true),
                                    onClick = {
                                        if (canManage && !defaultEffect.equals("deny", ignoreCase = true)) {
                                            haptics.selection()
                                            defaultEffect = "deny"
                                            scope.launch {
                                                runCatching { api.setBotAclDefaultEffect(botId, "deny") }
                                                    .onSuccess {
                                                        haptics.confirm()
                                                        message = "Default effect set to Deny"
                                                    }
                                                    .onFailure {
                                                        error = it.message ?: "Failed to set default effect"
                                                    }
                                            }
                                        }
                                    },
                                    enabled = canManage,
                                    label = { Text("Deny (Strict Whitelist)") }
                                )
                            }
                        }
                    }
                }

                // Section 2: Starter Prompts
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Starter Prompts (${starterPrompts.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (canManage) {
                                    TextButton(
                                        onClick = {
                                            haptics.selection()
                                            refreshingPrompts = true
                                            scope.launch {
                                                runCatching {
                                                    api.refreshStarterPrompts(botId, starterPrompts.map { it.prompt })
                                                }.onSuccess {
                                                    starterPrompts = it
                                                    haptics.confirm()
                                                    message = "Starter prompts refreshed"
                                                }.onFailure {
                                                    error = it.message ?: "Failed to refresh prompts"
                                                }
                                                refreshingPrompts = false
                                            }
                                        },
                                        enabled = !refreshingPrompts
                                    ) {
                                        if (refreshingPrompts) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text("Regenerate")
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Greeting suggestions offered to users when starting a new session with this bot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (starterPrompts.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    starterPrompts.forEach { prompt ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = prompt.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = prompt.prompt,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Bot Health & Checks
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Bot Health Checks (${checks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Automated health checks evaluating workspace runtime readiness, model connectivity, and memory subsystem.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (checks.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "All systems operational. No active check alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    checks.forEach { check ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            val (icon, tint) = when (check.status.lowercase()) {
                                                "ok", "pass", "healthy" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                                                "warn", "warning" -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
                                                else -> Icons.Default.Error to MaterialTheme.colorScheme.error
                                            }
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = check.summary.ifBlank { check.id },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (check.subtitle.isNotBlank()) {
                                                    Text(
                                                        text = check.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (check.detail.isNotBlank()) {
                                                    Text(
                                                        text = check.detail,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = tint
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Maintenance (Compaction logs)
                if (canManage) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    text = "Maintenance",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Clear session compaction logs and audit traces for this bot.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showClearConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clear Compaction Logs")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Compaction Logs") },
            text = { Text("Are you sure you want to clear historical context compaction logs for this bot?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        clearingLogs = true
                        scope.launch {
                            runCatching { api.clearCompactionLogs(botId) }
                                .onSuccess {
                                    haptics.confirm()
                                    message = "Compaction logs cleared"
                                }
                                .onFailure {
                                    haptics.reject()
                                    error = it.message ?: "Failed to clear logs"
                                }
                            clearingLogs = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
