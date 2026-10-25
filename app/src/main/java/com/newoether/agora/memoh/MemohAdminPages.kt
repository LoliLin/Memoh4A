package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsGroupColumn
import com.newoether.agora.ui.settings.SettingsItem
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 供应商与模型管理。网页把这两屏放在团队设置里，缺了它们新设备装完就走不完
 * 「配好供应商 → 建模型 → 能聊天」这条最短路径。
 */
@Composable
internal fun MemohProvidersPage(viewModel: MemohViewModel, onBack: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val admin = remember(viewModel.api) { MemohAdminApi(viewModel.api) }
    var providers by remember { mutableStateOf<List<MemohProvider>>(emptyList()) }
    var models by remember { mutableStateOf<List<MemohAdminModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemohProvider?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MemohProvider?>(null) }
    var testing by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun reload() {
        loading = true
        error = ""
        try {
            providers = decodeProviders(admin.providers())
            models = decodeAdminModels(admin.models())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Cloud answers 403 for ordinary members; that is "managed by your admin", not a bug.
            error = if (failure is MemohHttpException && failure.status == 403)
                uiResources.getString(R.string.memoh_ui_providers_managed_by_admin)
            else failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    CollapsingSettingsScaffold(title = uiResources.getString(R.string.memoh_ui_providers_models), onBack = onBack) {
        SettingsGroupColumn {
            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(
                            headlineContent = { Text(error, color = MaterialTheme.colorScheme.error) },
                            trailingContent = {
                                TextButton(onClick = { scope.launch { reload() } }) {
                                    Text(uiResources.getString(R.string.memoh_ui_retry))
                                }
                            },
                        )
                    }),
                )
            }
            if (loading) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_loading),
                    items = listOf({
                        SettingsItem(headlineContent = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) })
                    }),
                )
            }
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_providers),
                items = buildList {
                    providers.forEach { provider ->
                        add {
                            SettingsItem(
                                headlineContent = {
                                    Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(provider.displayType)
                                            if (provider.baseUrl.isNotBlank()) append(" · ${provider.baseUrl}")
                                            if (!provider.hasApiKey) append(" · ${uiResources.getString(R.string.memoh_ui_no_key)}")
                                            if (provider.managed) append(" · ${uiResources.getString(R.string.memoh_ui_managed)}")
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (testing == provider.id) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            TextButton(onClick = {
                                                testing = provider.id
                                                testResult = null
                                                scope.launch {
                                                    val outcome = runCatching { admin.testProvider(provider.id) }
                                                    testing = ""
                                                    testResult = provider.id to (outcome.fold(
                                                        onSuccess = { uiResources.getString(R.string.memoh_ui_test_passed) },
                                                        onFailure = { it.message ?: uiResources.getString(R.string.memoh_ui_test_failed) },
                                                    ))
                                                }
                                            }) { Text(uiResources.getString(R.string.memoh_ui_test)) }
                                        }
                                        IconButton(onClick = { editing = provider }, enabled = !provider.managed) {
                                            Icon(Icons.Default.Edit, uiResources.getString(R.string.memoh_ui_edit))
                                        }
                                        IconButton(onClick = { deleting = provider }, enabled = !provider.managed) {
                                            Icon(
                                                Icons.Default.Delete,
                                                uiResources.getString(R.string.memoh_ui_remove),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    add {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_add_provider)) },
                            leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                },
            )
            testResult?.let { (providerId, message) ->
                val name = providers.firstOrNull { it.id == providerId }?.name.orEmpty()
                if (name.isNotBlank()) {
                    SettingsGroup(
                        title = uiResources.getString(R.string.memoh_ui_last_test),
                        items = listOf({
                            SettingsItem(headlineContent = { Text(name) }, supportingContent = { Text(message) })
                        }),
                    )
                }
            }
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_models),
                items = buildList {
                    val byProvider = providers.associateBy { it.id }
                    models.forEach { model ->
                        add {
                            SettingsItem(
                                headlineContent = { Text(model.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(model.modelId)
                                            byProvider[model.providerId]?.name?.let { append(" · $it") }
                                            if (model.contextWindow > 0) append(" · ${model.contextWindow} ctx")
                                            if (model.reasoningEfforts.isNotEmpty())
                                                append(" · ${model.reasoningEfforts.joinToString("/")}")
                                            if (!model.enabled) append(" · ${uiResources.getString(R.string.memoh_ui_disabled)}")
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = model.enabled,
                                            onCheckedChange = { next ->
                                                scope.launch {
                                                    runCatching {
                                                        admin.updateModel(model.id, JSONObject().put("enable", next))
                                                    }.onFailure {
                                                        error = it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
                                                    }.onSuccess { reload() }
                                                }
                                            },
                                        )
                                        IconButton(onClick = {
                                            scope.launch {
                                                runCatching { admin.deleteModel(model.id) }
                                                    .onFailure { error = it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error) }
                                                    .onSuccess { reload() }
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, uiResources.getString(R.string.memoh_ui_remove))
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (models.isEmpty() && !loading) {
                        add { SettingsItem(headlineContent = { Text(uiResources.getString(R.string.memoh_ui_no_models_configured)) }) }
                    }
                },
            )
        }
    }

    if (creating || editing != null) {
        MemohProviderEditor(
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, clientType, baseUrl, apiKey, enabled ->
                scope.launch {
                    val outcome = runCatching {
                        if (editing == null) {
                            admin.createProviderFromRequest(
                                providerRequestBody(name, clientType, baseUrl, apiKey, null, create = true)
                                    .put("enable", enabled),
                            )
                        } else {
                            admin.updateProviderFromRequest(
                                editing!!.id,
                                providerRequestBody(name, clientType, baseUrl, apiKey, editing, create = false)
                                    .put("enable", enabled),
                            )
                        }
                    }
                    creating = false
                    editing = null
                    outcome.onFailure { error = it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error) }
                    if (outcome.isSuccess) reload()
                }
            },
        )
    }

    deleting?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(uiResources.getString(R.string.memoh_ui_remove_provider)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_remove_provider_confirm, provider.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { admin.deleteProvider(provider.id) }
                            .onFailure { error = it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error) }
                            .onSuccess { reload() }
                    }
                }) { Text(uiResources.getString(R.string.memoh_ui_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(uiResources.getString(R.string.memoh_ui_cancel)) }
            },
        )
    }
}

@Composable
private fun MemohProviderEditor(
    existing: MemohProvider?,
    onDismiss: () -> Unit,
    onSave: (name: String, clientType: String, baseUrl: String, apiKey: String, enabled: Boolean) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var clientType by remember { mutableStateOf(existing?.clientType.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) uiResources.getString(R.string.memoh_ui_add_provider)
                else uiResources.getString(R.string.memoh_ui_edit_provider),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clientType,
                    onValueChange = { clientType = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_client_type)) },
                    singleLine = true,
                    enabled = existing == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(uiResources.getString(R.string.memoh_ui_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (existing?.hasApiKey == true) uiResources.getString(R.string.memoh_ui_api_key_keep)
                            else uiResources.getString(R.string.memoh_ui_api_key),
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(uiResources.getString(R.string.memoh_ui_enabled))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, clientType, baseUrl, apiKey, enabled) },
                enabled = name.isNotBlank() && (existing != null || clientType.isNotBlank()),
            ) { Text(uiResources.getString(R.string.memoh_ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } },
    )
}

/** Token usage: the web shows a per-day stack; a phone shows the same numbers as rows. */
@Composable
internal fun MemohUsagePage(viewModel: MemohViewModel, onBack: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val admin = remember(viewModel.api) { MemohAdminApi(viewModel.api) }
    var days by remember { mutableStateOf<List<MemohTokenUsageDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        error = ""
        val botId = viewModel.bot?.id
        if (botId.isNullOrBlank()) {
            error = uiResources.getString(R.string.memoh_ui_no_accessible_bots_yet)
            loading = false
            return
        }
        try {
            days = decodeTokenUsage(viewModel.api.tokenUsage(botId))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(viewModel.bot?.id) { reload() }

    CollapsingSettingsScaffold(title = uiResources.getString(R.string.memoh_ui_usage), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_bot_usage),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(viewModel.bot?.title ?: uiResources.getString(R.string.memoh_settings_no_bots)) },
                        supportingContent = {
                            Text(
                                if (loading) uiResources.getString(R.string.memoh_ui_loading)
                                else uiResources.getString(R.string.memoh_ui_days_recorded, days.size),
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                                Text(uiResources.getString(R.string.memoh_ui_refresh))
                            }
                        },
                    )
                }),
            )
            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(headlineContent = { Text(error, color = MaterialTheme.colorScheme.error) })
                    }),
                )
            }
            if (days.isNotEmpty()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_daily_tokens),
                    items = days.take(30).map { day ->
                        {
                            SettingsItem(
                                headlineContent = { Text(day.date) },
                                supportingContent = {
                                    Text(
                                        day.channels.entries.joinToString(" · ") { (channel, tokens) ->
                                            "${channelLabel(channel)} ${formatTokens(tokens)}"
                                        }.ifBlank { uiResources.getString(R.string.memoh_ui_no_usage) },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Text(formatTokens(day.total), fontWeight = FontWeight.SemiBold)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun channelLabel(channel: String): String = when (channel) {
    "chat" -> "Chat"
    "discuss" -> "Discuss"
    "schedule" -> "Schedule"
    "memory" -> "Memory"
    "acp_agent" -> "ACP"
    else -> channel
}

internal fun formatTokens(tokens: Long): String = when {
    tokens <= 0 -> "0"
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0)
    else -> String.format(java.util.Locale.US, "%.2fM", tokens / 1_000_000.0)
}
