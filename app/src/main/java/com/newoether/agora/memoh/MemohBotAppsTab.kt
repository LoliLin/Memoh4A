package com.newoether.agora.memoh

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemohBotAppsTab(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(0) } // 0 = Installed Apps, 1 = Workspace Skills
    var apps by remember { mutableStateOf<List<MemohAppItem>>(emptyList()) }
    var skills by remember { mutableStateOf<List<MemohSkillItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var uninstallCandidate by remember { mutableStateOf<MemohAppItem?>(null) }

    fun refresh() {
        loading = true
        error = ""
        scope.launch {
            val appsResult = runCatching { api.botApps(botId) }
            val skillsResult = runCatching { api.botSkills(botId) }

            apps = appsResult.getOrDefault(emptyList())
            skills = skillsResult.getOrDefault(emptyList())

            if (appsResult.isFailure && skillsResult.isFailure) {
                error = appsResult.exceptionOrNull()?.message ?: "Failed to load apps and skills"
            }
            loading = false
        }
    }

    LaunchedEffect(botId) {
        refresh()
    }

    Column(modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        // Section Selector & Action Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = {
                        haptics.selection()
                        selectedSection = 0
                    },
                    label = { Text("Apps (${apps.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = {
                        haptics.selection()
                        selectedSection = 1
                    },
                    label = { Text("Skills (${skills.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedSection == 0 && canManage) {
                    IconButton(
                        onClick = {
                            haptics.selection()
                            checkingUpdates = true
                            message = ""
                            error = ""
                            scope.launch {
                                runCatching { api.checkAppUpdates(botId) }
                                    .onSuccess {
                                        apps = it
                                        message = "Checked for app updates"
                                    }
                                    .onFailure {
                                        error = it.message ?: "Failed to check updates"
                                    }
                                checkingUpdates = false
                            }
                        },
                        enabled = !checkingUpdates
                    ) {
                        if (checkingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Check Updates")
                        }
                    }
                }
                IconButton(onClick = {
                    haptics.selection()
                    refresh()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
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

        if (loading && apps.isEmpty() && skills.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (selectedSection == 0) {
            // Installed Apps List
            if (apps.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Apps,
                    title = "No Installed Apps",
                    description = "Apps package specialized skill workflows, connectors, and tools for your bot. Installed packages from the Supermarket will be managed here."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.installationId.ifBlank { it.appId } }) { app ->
                        AppItemCard(
                            app = app,
                            canManage = canManage,
                            onUninstall = { uninstallCandidate = app }
                        )
                    }
                }
            }
        } else {
            // Workspace Skills List
            if (skills.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "No Workspace Skills",
                    description = "Skills are reusable agent instructions (SKILL.md) located in your bot's workspace directory. Custom skills will be listed here automatically."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skills, key = { it.sourcePath }) { skill ->
                        SkillItemCard(skill = skill)
                    }
                }
            }
        }
    }

    // Uninstall Confirmation Dialog
    uninstallCandidate?.let { app ->
        AlertDialog(
            onDismissRequest = { uninstallCandidate = null },
            title = { Text("Uninstall App") },
            text = { Text("Are you sure you want to remove '${app.name}'? Its packaged skills and associated resources will be removed from this workspace.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = app
                        uninstallCandidate = null
                        scope.launch {
                            runCatching { api.deleteApp(botId, toRemove.installationId) }
                                .onSuccess {
                                    haptics.confirm()
                                    message = "App '${toRemove.name}' uninstalled"
                                    refresh()
                                }
                                .onFailure {
                                    haptics.reject()
                                    error = it.message ?: "Failed to uninstall app"
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Uninstall") }
            },
            dismissButton = {
                OutlinedButton(onClick = { uninstallCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItemCard(
    app: MemohAppItem,
    canManage: Boolean,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (app.version.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${app.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (app.description.isNotBlank()) {
                        Text(
                            text = app.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                if (canManage && app.installationId.isNotBlank()) {
                    IconButton(onClick = onUninstall) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            // Status and Counts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusColor = when (app.status.lowercase()) {
                    "installed" -> MaterialTheme.colorScheme.primary
                    "failed" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = app.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }

                if (app.skillsCount > 0) {
                    Text(
                        text = "${app.skillsCount} skills",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (app.dependenciesCount > 0) {
                    Text(
                        text = "• ${app.dependenciesCount} deps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (app.skillNames.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    app.skillNames.forEach { name ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (app.lastError.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = app.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SkillItemCard(skill: MemohSkillItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (skill.managed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MANAGED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (skill.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (skill.sourcePath.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = skill.sourcePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
