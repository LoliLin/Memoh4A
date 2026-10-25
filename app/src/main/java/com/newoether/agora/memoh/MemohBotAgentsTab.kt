package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.heightIn

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
import com.newoether.agora.ui.components.rememberSecretVisible
import com.newoether.agora.ui.components.secretVisualTransformation
import com.newoether.agora.ui.components.SecretVisibilityToggle
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
    val uiResources = LocalContext.current.resources
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
            onError(e.message ?: uiResources.getString(R.string.memoh_ui_failed_to_refresh_agents))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(uiResources.getString(R.string.memoh_ui_bot_agents), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (controller.loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
            if (canManage) {
                TextButton(onClick = { showCreateDialog = true }) { Text(uiResources.getString(R.string.memoh_ui_new_agent)) }
            }
        }

        if (controller.agents.isEmpty() && !controller.loading) {
            Text(uiResources.getString(R.string.memoh_ui_no_agents_configured_for_this_bot_create_an_agent_to_handle_coding_or_ta),
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
                                    Text(uiResources.getString(R.string.memoh_ui_runtime_1_s_id_2_s, agent.runtime, agent.id),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = agent.enabled,
                                    enabled = canManage,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            runCatching { controller.setEnabled(botId, agent, enabled) }
                                                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_update_agent_status)) }
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
                                        if (agent.credentialConnected) uiResources.getString(R.string.memoh_ui_credential_connected) else uiResources.getString(R.string.memoh_ui_no_credential),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                if (canManage) {
                                    TextButton(onClick = {
                                        credentialAgent = agent
                                        credentialSecret = ""
                                    }) { Text(uiResources.getString(R.string.memoh_ui_credentials)) }

                                    TextButton(onClick = { deletingAgent = agent }) {
                                        Text(uiResources.getString(R.string.memoh_ui_delete), color = MaterialTheme.colorScheme.error)
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
            title = { Text(uiResources.getString(R.string.memoh_ui_create_agent)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newAgentName,
                        onValueChange = { newAgentName = it },
                        label = { Text(uiResources.getString(R.string.memoh_ui_agent_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(uiResources.getString(R.string.memoh_ui_select_runtime), style = MaterialTheme.typography.labelMedium)
                    controller.options.forEach { opt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedOptionId == opt.id,
                                onClick = { selectedOptionId = opt.id },
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
                                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_create_agent)) }
                        }
                    }
                ) { Text(uiResources.getString(R.string.memoh_ui_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            }
        )
    }

    if (credentialAgent != null) {
        val agent = credentialAgent!!
        var credentialVisible by rememberSecretVisible()
        AlertDialog(
            onDismissRequest = { credentialAgent = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_credentials_for_1_s, agent.name)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiResources.getString(R.string.memoh_ui_runtime_1_s, agent.runtime), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = credentialSecret,
                        onValueChange = { credentialSecret = it },
                        label = { Text(uiResources.getString(R.string.memoh_ui_api_key_token)) },
                        visualTransformation = secretVisualTransformation(credentialVisible),
                        trailingIcon = { SecretVisibilityToggle(credentialVisible) { credentialVisible = !credentialVisible } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
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
                                        .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_save_credential)) }
                                }
                            }
                        ) { Text(uiResources.getString(R.string.memoh_ui_save_key)) }

                        if (agent.credentialConnected) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching { api.deleteAgentCredential(botId, agent.id) }
                                            .onSuccess {
                                                credentialAgent = null
                                                controller.refresh(botId)
                                            }
                                            .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_delete_credential)) }
                                    }
                                }
                            ) { Text(uiResources.getString(R.string.memoh_ui_clear_key), color = MaterialTheme.colorScheme.error) }
                        }
                    }

                    if (agent.runtime == "codex" || agent.runtime == "claude-code") {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(uiResources.getString(R.string.memoh_ui_or_use_device_code_sign_in), style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = {
                                deviceLogin.start(agent.id, agent.name)
                                credentialAgent = null
                            }
                        ) { Text(uiResources.getString(R.string.memoh_ui_start_device_code_sign_in)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { credentialAgent = null }) { Text(uiResources.getString(R.string.memoh_ui_close)) }
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
            title = { Text(uiResources.getString(R.string.memoh_ui_device_sign_in_1_s, state.agentName)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pending && login != null) {
                        Text(uiResources.getString(R.string.memoh_ui_user_code), style = MaterialTheme.typography.bodySmall)
                        Text(login.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(uiResources.getString(R.string.memoh_ui_verification_url_1_s, login.verificationUrl), style = MaterialTheme.typography.bodySmall)
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
                    }) { Text(uiResources.getString(R.string.memoh_ui_open_browser)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceLogin.close() }) {
                    Text(if (state.pending) uiResources.getString(R.string.memoh_ui_cancel) else uiResources.getString(R.string.memoh_ui_close))
                }
            }
        )
    }

    if (deletingAgent != null) {
        val agent = deletingAgent!!
        AlertDialog(
            onDismissRequest = { deletingAgent = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_delete_agent_1_s, agent.name)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_this_will_remove_this_agent_configuration_from_the_bot_active_sessions_u)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { controller.delete(botId, agent) }
                                .onSuccess { deletingAgent = null }
                                .onFailure { onError(it.message ?: uiResources.getString(R.string.memoh_ui_failed_to_delete_agent)) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(uiResources.getString(R.string.memoh_ui_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingAgent = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            }
        )
    }
}
