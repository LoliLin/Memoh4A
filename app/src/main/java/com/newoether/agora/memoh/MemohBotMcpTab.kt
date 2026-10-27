package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.heightIn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemohBotMcpTab(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiResources = LocalContext.current.resources
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<MemohMcpConnection>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var probingId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<MemohMcpConnection?>(null) }

    fun refresh() {
        loading = true
        error = ""
        scope.launch {
            runCatching { api.mcpList(botId) }
                .onSuccess {
                    servers = it
                    loading = false
                }
                .onFailure {
                    error = it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_load_mcp_servers)
                    loading = false
                }
        }
    }

    LaunchedEffect(botId) {
        refresh()
    }

    Column(modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        // Action Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = uiResources.getString(R.string.memoh_ui_mcp_servers_1_s, servers.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiResources.getString(R.string.memoh_ui_model_context_protocol_tools_resources),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.selection()
                    refresh()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = uiResources.getString(R.string.memoh_ui_refresh))
                }
                if (canManage) {
                    Button(
                        onClick = {
                            haptics.selection()
                            showAddDialog = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(uiResources.getString(R.string.memoh_ui_add))
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { refresh() }, enabled = !loading) {
                        Text(uiResources.getString(R.string.memoh_ui_refresh))
                    }
                }
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

        if (loading && servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (servers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uiResources.getString(R.string.memoh_ui_no_mcp_servers_connected),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiResources.getString(R.string.memoh_ui_model_context_protocol_connects_your_bot_to_external_tools_databases_and),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers, key = { it.id }) { server ->
                    McpServerCard(
                        server = server,
                        canManage = canManage,
                        isProbing = probingId == server.id,
                        onToggleActive = { active ->
                            haptics.selection()
                            scope.launch {
                                runCatching {
                                    api.updateMcpConnection(
                                        botId = botId,
                                        id = server.id,
                                        name = server.name,
                                        transport = server.transport,
                                        url = server.url,
                                        command = server.command,
                                        isActive = active
                                    )
                                }.onSuccess {
                                    refresh()
                                }.onFailure {
                                    error = it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_update_mcp_server)
                                }
                            }
                        },
                        onProbe = {
                            haptics.selection()
                            probingId = server.id
                            message = ""
                            error = ""
                            scope.launch {
                                runCatching { api.probeMcpConnection(botId, server.id) }
                                    .onSuccess {
                                        message = uiResources.getString(R.string.memoh_ui_server_1_s_probed_successfully, server.name)
                                        refresh()
                                    }
                                    .onFailure {
                                        error = uiResources.getString(R.string.memoh_ui_probe_failed_1_s, it.message)
                                    }
                                probingId = null
                            }
                        },
                        onDelete = {
                            deleteCandidate = server
                        }
                    )
                }
            }
        }
    }

    // Add Server Dialog
    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, transport, target ->
                showAddDialog = false
                loading = true
                scope.launch {
                    runCatching {
                        api.createMcpConnection(
                            botId = botId,
                            name = name,
                            transport = transport,
                            url = if (transport == "sse") target else "",
                            command = if (transport == "stdio") target else "",
                            isActive = true
                        )
                    }.onSuccess {
                        haptics.confirm()
                        message = uiResources.getString(R.string.memoh_ui_mcp_server_1_s_added, name)
                        refresh()
                    }.onFailure {
                        haptics.reject()
                        error = it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_add_mcp_server)
                        loading = false
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteCandidate?.let { server ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_mcp_server)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_are_you_sure_you_want_to_remove_1_s_tools_provided_by_this_server_will_n, server.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = server
                        deleteCandidate = null
                        scope.launch {
                            runCatching { api.deleteMcpConnection(botId, toDelete.id) }
                                .onSuccess {
                                    haptics.confirm()
                                    message = uiResources.getString(R.string.memoh_ui_mcp_server_1_s_deleted, toDelete.name)
                                    refresh()
                                }
                                .onFailure {
                                    haptics.reject()
                                    error = it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_delete_mcp_server)
                                }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(uiResources.getString(R.string.memoh_ui_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteCandidate = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun McpServerCard(
    server: MemohMcpConnection,
    canManage: Boolean,
    isProbing: Boolean,
    onToggleActive: (Boolean) -> Unit,
    onProbe: () -> Unit,
    onDelete: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    var expandedTools by remember { mutableStateOf(false) }

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
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        // Transport Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = server.transport.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (server.url.isNotBlank() || server.command.isNotBlank()) {
                        Text(
                            text = server.url.ifBlank { server.command },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (canManage) {
                    Switch(
                        checked = server.isActive,
                        onCheckedChange = onToggleActive,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Status and Last Probe Info
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (server.status.lowercase()) {
                        "ready", "connected", "active" -> MaterialTheme.colorScheme.primary
                        "error", "failed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = server.status.ifBlank { "ready" },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (server.lastProbedAt.isNotBlank()) {
                        Text(
                            text = uiResources.getString(R.string.memoh_ui_probed_1_s, server.lastProbedAt.take(19).replace('T', ' ')),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (server.statusMessage.isNotBlank()) {
                Text(
                    text = server.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Tools Section
            if (server.tools.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTools = !expandedTools }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiResources.getString(R.string.memoh_ui_tools_exposed_1_s, server.tools.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expandedTools) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(visible = expandedTools) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        server.tools.forEach { tool ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions
            if (canManage) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onProbe,
                        enabled = !isProbing
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(uiResources.getString(R.string.memoh_ui_probe_connection))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = uiResources.getString(R.string.memoh_ui_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, transport: String, target: String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("sse") }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_add_mcp_server)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_server_name)) },
                    placeholder = { Text(uiResources.getString(R.string.memoh_ui_e_g_filesystem_tools)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transport == "sse",
                        onClick = { transport = "sse" },
                        label = { Text(uiResources.getString(R.string.memoh_ui_sse_http)) }
                    )
                    FilterChip(
                        selected = transport == "stdio",
                        onClick = { transport = "stdio" },
                        label = { Text(uiResources.getString(R.string.memoh_ui_stdio_command)) }
                    )
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(if (transport == "sse") uiResources.getString(R.string.memoh_ui_server_sse_url) else uiResources.getString(R.string.memoh_ui_execution_command)) },
                    placeholder = {
                        Text(if (transport == "sse") "https://mcp.domain.com/sse" else "npx -y @modelcontextprotocol/...")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, transport, target) },
                enabled = name.isNotBlank() && target.isNotBlank()
            ) { Text(uiResources.getString(R.string.memoh_ui_add_server)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
        }
    )
}
