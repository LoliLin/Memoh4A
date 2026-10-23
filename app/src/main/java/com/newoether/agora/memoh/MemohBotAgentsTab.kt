package com.newoether.agora.memoh

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BotAgentsTab(
    api: MemohApi,
    botId: String,
    controller: MemohAgentController,
    canManage: Boolean,
    onError: (String) -> Unit,
) {
    key(api, api.baseUrl, api.token, botId, controller) {
        BotAgentsContent(api, botId, controller, canManage, onError)
    }
}

@Composable
private fun BotAgentsContent(
    api: MemohApi,
    botId: String,
    controller: MemohAgentController,
    canManage: Boolean,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newAgentName by remember { mutableStateOf("") }
    var selectedOptionId by remember { mutableStateOf("codex") }
    var credentialAgent by remember { mutableStateOf<MemohAgent?>(null) }
    var credentialSecret by remember { mutableStateOf("") }
    var deletingAgent by remember { mutableStateOf<MemohAgent?>(null) }
    val deviceLogin = remember(api, botId, controller) {
        val baseUrl = api.baseUrl
        val token = api.token
        MemohDeviceLoginPoller(
            scope = scope,
            start = { api.startDeviceLogin(botId, it) },
            poll = { agentId, loginId -> api.pollDeviceLogin(botId, agentId, loginId) },
            cancel = { agentId, loginId -> api.cancelDeviceLogin(botId, agentId, loginId) },
            refresh = { controller.refresh(botId) },
            isIdentityCurrent = { api.baseUrl == baseUrl && api.token == token },
        )
    }
    val deviceLoginState by deviceLogin.state.collectAsState()
    DisposableEffect(deviceLogin) {
        onDispose { deviceLogin.dispose() }
    }

    LaunchedEffect(api, botId, controller) {
        try {
            controller.refresh(botId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.message ?: "Failed to refresh agents")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Bot Agents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (controller.loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
            if (canManage) {
                TextButton(onClick = { showCreateDialog = true }) { Text("+ New Agent") }
            }
        }

        if (controller.agents.isEmpty() && !controller.loading) {
            Text("No agents configured for this bot. Create an agent to handle coding or task delegation.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(controller.agents, key = { it.id }) { agent ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(agent.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Runtime: ${agent.runtime} | ID: ${agent.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = agent.enabled,
                                    enabled = canManage,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            runCatching { controller.setEnabled(botId, agent, enabled) }
                                                .onFailure { onError(it.message ?: "Failed to update agent status") }
                                        }
                                    }
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = if (agent.credentialConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        if (agent.credentialConnected) "Credential Connected" else "No Credential",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                if (canManage) {
                                    TextButton(onClick = {
                                        credentialAgent = agent
                                        credentialSecret = ""
                                    }) { Text("Credentials") }

                                    TextButton(onClick = { deletingAgent = agent }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newAgentName,
                        onValueChange = { newAgentName = it },
                        label = { Text("Agent Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Select Runtime:", style = MaterialTheme.typography.labelMedium)
                    controller.options.forEach { opt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedOptionId == opt.id,
                                onCheckedChange = { if (it) selectedOptionId = opt.id }
                            )
                            Column {
                                Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                                if (opt.description.isNotBlank()) {
                                    Text(opt.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newAgentName.isNotBlank() && selectedOptionId.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching { controller.create(botId, newAgentName, selectedOptionId) }
                                .onSuccess {
                                    showCreateDialog = false
                                    newAgentName = ""
                                }
                                .onFailure { onError(it.message ?: "Failed to create agent") }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (credentialAgent != null) {
        val agent = credentialAgent!!
        AlertDialog(
            onDismissRequest = { credentialAgent = null },
            title = { Text("Credentials for '${agent.name}'") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runtime: ${agent.runtime}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = credentialSecret,
                        onValueChange = { credentialSecret = it },
                        label = { Text("API Key / Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = credentialSecret.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching { api.saveAgentCredential(botId, agent, credentialSecret) }
                                        .onSuccess {
                                            credentialAgent = null
                                            controller.refresh(botId)
                                        }
                                        .onFailure { onError(it.message ?: "Failed to save credential") }
                                }
                            }
                        ) { Text("Save Key") }

                        if (agent.credentialConnected) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching { api.deleteAgentCredential(botId, agent.id) }
                                            .onSuccess {
                                                credentialAgent = null
                                                controller.refresh(botId)
                                            }
                                            .onFailure { onError(it.message ?: "Failed to delete credential") }
                                    }
                                }
                            ) { Text("Clear Key", color = MaterialTheme.colorScheme.error) }
                        }
                    }

                    if (agent.runtime == "codex" || agent.runtime == "claude-code") {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Or use Device Code Sign-In:", style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = {
                                deviceLogin.start(agent.id, agent.name)
                                credentialAgent = null
                            }
                        ) { Text("Start Device Code Sign-In") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { credentialAgent = null }) { Text("Close") }
            }
        )
    }

    deviceLoginState?.let { state ->
        val login = state.login
        LaunchedEffect(login?.loginId) {
            if (login != null && state.pending) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(login.verificationUrl)))
                }
            }
        }
        AlertDialog(
            onDismissRequest = { deviceLogin.close() },
            title = { Text("Device Sign-In: ${state.agentName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pending && login != null) {
                        Text("User Code:", style = MaterialTheme.typography.bodySmall)
                        Text(login.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Verification URL: ${login.verificationUrl}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                if (state.pending && login != null) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(login.verificationUrl)))
                        }
                    }) { Text("Open Browser") }
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceLogin.close() }) {
                    Text(if (state.pending) "Cancel" else "Close")
                }
            }
        )
    }

    if (deletingAgent != null) {
        val agent = deletingAgent!!
        AlertDialog(
            onDismissRequest = { deletingAgent = null },
            title = { Text("Delete Agent '${agent.name}'?") },
            text = { Text("This will remove this agent configuration from the bot. Active sessions using this agent will need a new agent assigned.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { controller.delete(botId, agent) }
                                .onSuccess { deletingAgent = null }
                                .onFailure { onError(it.message ?: "Failed to delete agent") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingAgent = null }) { Text("Cancel") }
            }
        )
    }
}
