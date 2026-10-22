package com.newoether.agora.memoh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import kotlinx.coroutines.launch

@Composable
internal fun MemohBotRuntimeTab(
    api: MemohApi,
    botId: String,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()
    var runtimes by remember { mutableStateOf<List<MemohRemoteRuntime>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var newlyCreatedKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteCandidate by remember { mutableStateOf<MemohRemoteRuntime?>(null) }

    fun refresh() {
        loading = true
        error = ""
        scope.launch {
            runCatching { api.remoteRuntimes() }
                .onSuccess {
                    runtimes = it
                    loading = false
                }
                .onFailure {
                    error = it.message ?: "Failed to load remote runtimes"
                    loading = false
                }
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
                    text = "Remote Runtime Workers (${runtimes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "External host nodes executing containerized bot sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.selection()
                    refresh()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                if (canManage) {
                    Button(
                        onClick = {
                            haptics.selection()
                            showRegisterDialog = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Register")
                    }
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

        if (loading && runtimes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Info Architecture Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hybrid Execution Architecture",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Bots can execute workloads within server-side cloud containers or offload commands to registered self-hosted worker nodes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (runtimes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No External Runtime Nodes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "By default, this bot runs in the standard managed container environment. Register a remote worker node if you want to run tasks on private infrastructure.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(runtimes, key = { it.id }) { runtime ->
                        RuntimeCard(
                            runtime = runtime,
                            canManage = canManage,
                            onRevoke = { deleteCandidate = runtime }
                        )
                    }
                }
            }
        }
    }

    // Register Dialog
    if (showRegisterDialog) {
        var nodeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register Remote Runtime") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Generate credentials for a worker node running the Memoh daemon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nodeName,
                        onValueChange = { nodeName = it },
                        label = { Text("Worker Node Name") },
                        placeholder = { Text("e.g. dev-workstation-01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = nodeName.trim()
                        showRegisterDialog = false
                        loading = true
                        scope.launch {
                            runCatching { api.createRemoteRuntime(name) }
                                .onSuccess { created ->
                                    haptics.confirm()
                                    newlyCreatedKey = created.name to created.key
                                    refresh()
                                }
                                .onFailure {
                                    haptics.reject()
                                    error = it.message ?: "Failed to register runtime"
                                    loading = false
                                }
                        }
                    },
                    enabled = nodeName.isNotBlank()
                ) { Text("Generate Token") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Newly Created Key Dialog
    newlyCreatedKey?.let { (name, key) ->
        AlertDialog(
            onDismissRequest = { newlyCreatedKey = null },
            title = { Text("Runtime Key Generated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Save this key now. It will authenticate the daemon on '$name'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = key.ifBlank { "Registered successfully (key stored on server)" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (key.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Memoh Runtime Key", key))
                        haptics.confirm()
                    }
                    newlyCreatedKey = null
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy & Close")
                }
            }
        )
    }

    // Revoke Confirmation Dialog
    deleteCandidate?.let { runtime ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Revoke Runtime Node") },
            text = { Text("Are you sure you want to revoke credentials for '${runtime.name}' (${runtime.hostname.ifBlank { "Node" }})?") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = runtime
                        deleteCandidate = null
                        scope.launch {
                            runCatching { api.deleteRemoteRuntime(toDelete.id) }
                                .onSuccess {
                                    haptics.confirm()
                                    message = "Runtime '${toDelete.name}' revoked"
                                    refresh()
                                }
                                .onFailure {
                                    haptics.reject()
                                    error = it.message ?: "Failed to revoke runtime"
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Revoke") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RuntimeCard(
    runtime: MemohRemoteRuntime,
    canManage: Boolean,
    onRevoke: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = runtime.name.ifBlank { runtime.hostname },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (runtime.hostname.isNotBlank()) {
                            Text(
                                text = runtime.hostname,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusText, statusColor) = if (runtime.online) {
                        "ONLINE" to MaterialTheme.colorScheme.primary
                    } else {
                        "OFFLINE" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    if (canManage) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onRevoke) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Revoke",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (runtime.os.isNotBlank() || runtime.arch.isNotBlank()) {
                    Text(
                        text = "${runtime.os} / ${runtime.arch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (runtime.clientVersion.isNotBlank()) {
                    Text(
                        text = "• v${runtime.clientVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (runtime.createdAt.isNotBlank()) {
                    Text(
                        text = "• Added ${runtime.createdAt.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (runtime.workspaceBase.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Base: ${runtime.workspaceBase}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
