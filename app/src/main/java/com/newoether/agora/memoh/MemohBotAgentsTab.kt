package com.newoether.agora.memoh

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the device-code login is polled while waiting for the user. */
private const val DEVICE_LOGIN_POLL_MS = 2_500L

/** Give up on a device-code login after this long instead of polling forever. */
private const val DEVICE_LOGIN_TIMEOUT_MS = 15 * 60 * 1000L

/** Consecutive network failures tolerated before the login attempt is abandoned. */
private const val DEVICE_LOGIN_MAX_FAILURES = 8

@Composable
internal fun BotAgentsTab(
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
    var activeDeviceLogin by remember { mutableStateOf<Pair<MemohAgent, MemohDeviceLogin>?>(null) }
    var deviceLoginStatus by remember { mutableStateOf("") }

    LaunchedEffect(botId) {
        runCatching { controller.refresh(botId) }
            .onFailure { onError(it.message ?: "Failed to refresh agents") }
    }

    // Auto-polling for device code login.
    //
    // Bounded on purpose: the server is the only thing that knows when a login
    // really expired, but a poll that keeps failing (offline, revoked token,
    // server 5xx) would otherwise loop forever and keep the device awake. Past
    // the deadline stop polling and say so instead of pretending it is pending.
    LaunchedEffect(activeDeviceLogin) {
        val pair = activeDeviceLogin ?: return@LaunchedEffect
        val (agent, login) = pair
        deviceLoginStatus = "Waiting for authorization in browser…"
        var consecutiveFailures = 0
        val deadline = System.currentTimeMillis() + DEVICE_LOGIN_TIMEOUT_MS
        while (activeDeviceLogin != null) {
            if (System.currentTimeMillis() >= deadline) {
                deviceLoginStatus = "Timed out waiting for authorization. Start the login again."
                activeDeviceLogin = null
                break
            }
            delay(DEVICE_LOGIN_POLL_MS)
            try {
                val res = api.pollDeviceLogin(botId, agent.id, login.loginId)
                consecutiveFailures = 0
                if (res.status == "success" || res.status == "authorized") {
                    deviceLoginStatus = "Authorized successfully!"
                    controller.refresh(botId)
                    delay(1000)
                    activeDeviceLogin = null
                    break
                } else if (res.status == "expired" || res.status == "failed") {
                    deviceLoginStatus = "Authorization expired or failed: ${res.message}"
                    activeDeviceLogin = null
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A transient network error is worth retrying, but not forever,
                // and the user should see that it is failing rather than a
                // permanently cheerful "waiting" label.
                consecutiveFailures++
                deviceLoginStatus = if (consecutiveFailures >= DEVICE_LOGIN_MAX_FAILURES) {
                    "Cannot reach the server for authorization (${e.message ?: "network error"}). Start the login again."
                } else {
                    "Waiting for authorization… (retrying after a network error)"
                }
                if (consecutiveFailures >= DEVICE_LOGIN_MAX_FAILURES) {
                    activeDeviceLogin = null
                    break
                }
            }
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
                                scope.launch {
                                    runCatching { api.startDeviceLogin(botId, agent.id) }
                                        .onSuccess { login ->
                                            activeDeviceLogin = agent to login
                                            credentialAgent = null
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(login.verificationUrl))
                                                context.startActivity(intent)
                                            }
                                        }
                                        .onFailure { onError(it.message ?: "Device login failed to start") }
                                }
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

    if (activeDeviceLogin != null) {
        val (agent, login) = activeDeviceLogin!!
        AlertDialog(
            onDismissRequest = {
                scope.launch { runCatching { api.cancelDeviceLogin(botId, agent.id, login.loginId) } }
                activeDeviceLogin = null
            },
            title = { Text("Device Sign-In: ${agent.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User Code:", style = MaterialTheme.typography.bodySmall)
                    Text(login.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Verification URL: ${login.verificationUrl}", style = MaterialTheme.typography.bodySmall)
                    Text(deviceLoginStatus, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(login.verificationUrl))
                            context.startActivity(intent)
                        }
                    }
                ) { Text("Open Browser") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { runCatching { api.cancelDeviceLogin(botId, agent.id, login.loginId) } }
                        activeDeviceLogin = null
                    }
                ) { Text("Cancel") }
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
