package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.components.providerIcon
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold
import com.newoether.agora.ui.settings.GuardedAnimatedContent
import com.newoether.agora.ui.settings.SettingsAddItem
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsGroupColumn
import com.newoether.agora.ui.settings.SettingsItem
import com.newoether.agora.ui.settings.rememberSettingsScrollState
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 供应商与模型的团队配置。结构照 Agora 的 SettingsProviderPage：
 * 列表页只读，点行进详情页（GuardedAnimatedContent 二级面板），新增走独立页。
 * 之前把增删改都塞进一个 AlertDialog，和 Agora 的配置界面完全不是一回事。
 */
@Composable
internal fun MemohProvidersPage(viewModel: MemohViewModel, onBack: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val selectedTeamId = viewModel.teams.selectedId
    val apiScope = viewModel.api.scope
    val admin = remember(viewModel.api, apiScope, selectedTeamId) { MemohAdminApi(viewModel.api) }
    var providers by remember(admin) { mutableStateOf<List<MemohProvider>>(emptyList()) }
    var models by remember(admin) { mutableStateOf<List<MemohAdminModel>>(emptyList()) }
    var loading by remember(admin) { mutableStateOf(true) }
    var error by remember(admin) { mutableStateOf("") }
    var selectedProviderId by rememberSaveable(admin) { mutableStateOf<String?>(null) }
    var adding by rememberSaveable(admin) { mutableStateOf(false) }
    val scrollState = rememberSettingsScrollState()

    suspend fun reload() {
        loading = true
        error = ""
        try {
            providers = decodeProviders(admin.providers())
            models = decodeAdminModels(admin.models())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = if (failure is MemohHttpException && failure.status == 403)
                uiResources.getString(R.string.memoh_ui_providers_managed_by_admin)
            else failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(admin) { reload() }

    val selected = providers.firstOrNull { it.id == selectedProviderId }
    Box(Modifier.fillMaxSize()) {
        GuardedAnimatedContent(
            targetState = selectedProviderId to adding,
            forward = selectedProviderId != null || adding,
        ) { (providerId, isAdding) ->
            when {
                isAdding -> MemohSecondaryPane {
                    MemohNewProviderPage(
                        admin = admin,
                        onBack = { adding = false },
                        onCreated = { adding = false; scope.launch { reload() } },
                    )
                }
                providerId != null -> {
                    val provider = providers.firstOrNull { it.id == providerId }
                    if (provider == null) {
                        // The list refreshed under us (deleted elsewhere): fall back to the list.
                        LaunchedEffect(providerId) { selectedProviderId = null }
                    } else {
                        MemohSecondaryPane {
                            MemohProviderDetailPage(
                                admin = admin,
                                provider = provider,
                                models = models.filter { it.providerId == provider.id },
                                onBack = { selectedProviderId = null },
                                onChanged = { scope.launch { reload() } },
                            )
                        }
                    }
                }
                else -> CollapsingSettingsScaffold(
                    title = uiResources.getString(R.string.memoh_ui_providers_models),
                    onBack = onBack,
                    scrollState = scrollState,
                ) {
                    SettingsGroupColumn {
                        if (error.isNotBlank()) {
                            SettingsGroup(
                                title = uiResources.getString(R.string.memoh_ui_status),
                                items = listOf({
                                    SettingsItem(
                                        headlineContent = {
                                            Text(error, color = MaterialTheme.colorScheme.error)
                                        },
                                        trailingContent = {
                                            TextButton(onClick = { scope.launch { reload() } }) {
                                                Text(uiResources.getString(R.string.memoh_ui_retry))
                                            }
                                        },
                                    )
                                }),
                            )
                        }

                        SettingsGroup(
                            title = uiResources.getString(R.string.memoh_ui_providers),
                            items = buildList {
                                if (loading && providers.isEmpty()) {
                                    add {
                                        SettingsItem(
                                            headlineContent = {
                                                CircularProgressIndicator(
                                                    Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            },
                                        )
                                    }
                                }
                                providers.forEach { provider ->
                                    add {
                                        val configured = provider.hasApiKey || provider.baseUrl.isNotBlank()
                                        val tint = if (configured) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        SettingsItem(
                                            headlineContent = { Text(provider.name, fontWeight = FontWeight.Medium) },
                                            supportingContent = {
                                                Text(
                                                    provider.baseUrl.ifBlank {
                                                        uiResources.getString(R.string.memoh_ui_not_configured)
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingContent = {
                                                val iconRes = providerIcon(provider.name)
                                                if (iconRes != 0) {
                                                    Icon(
                                                        painterResource(iconRes),
                                                        null,
                                                        tint = tint,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Cloud,
                                                        null,
                                                        tint = tint,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                StreamBadge(
                                                    text = provider.displayType,
                                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            modifier = Modifier.clickable { selectedProviderId = provider.id },
                                        )
                                    }
                                }
                                add {
                                    SettingsAddItem(
                                        label = uiResources.getString(R.string.memoh_ui_add_provider),
                                        onClick = { adding = true },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Agora's own secondary-pane wrapper is module-internal, so provide the same local it sets:
 * a pushed page keeps its Back affordance visible even inside a two-pane shell.
 */
@Composable
private fun MemohSecondaryPane(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.newoether.agora.ui.settings.LocalSettingsPaneBackButtonVisible provides true,
        content = content,
    )
}

/** Small rounded label, matching the protocol badges Agora puts on provider rows. */
@Composable
private fun StreamBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(shape = RoundedCornerShape(4.dp), color = container, contentColor = content) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** One provider's own page: connectivity, models under it, and its own destructive action. */
@Composable
internal fun MemohProviderDetailPage(
    admin: MemohAdminApi,
    provider: MemohProvider,
    models: List<MemohAdminModel>,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf("") }
    var testFailed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingModel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scrollState = rememberSettingsScrollState()

    CollapsingSettingsScaffold(title = provider.name, onBack = onBack, scrollState = scrollState) {
        SettingsGroupColumn {
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_connection),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_api_key)) },
                            supportingContent = {
                                Text(
                                    if (provider.hasApiKey) uiResources.getString(R.string.memoh_ui_key_configured)
                                    else uiResources.getString(R.string.memoh_ui_not_configured),
                                )
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_base_url)) },
                            supportingContent = {
                                Text(
                                    provider.baseUrl.ifBlank {
                                        uiResources.getString(R.string.memoh_ui_not_configured)
                                    },
                                )
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_test_connection)) },
                            supportingContent = {
                                Text(
                                    when {
                                        testMessage.isNotBlank() -> testMessage
                                        else -> uiResources.getString(R.string.memoh_ui_test_connection_desc)
                                    },
                                    color = if (testFailed) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (testing) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = {
                                        testing = true
                                        scope.launch {
                                            try {
                                                val status = memohConnectivityStatus(admin.testProvider(provider.id))
                                                testFailed = status == MemohConnectivityStatus.FAILED
                                                testMessage = uiResources.getString(when (status) {
                                                    MemohConnectivityStatus.PASSED -> R.string.memoh_ui_test_passed
                                                    MemohConnectivityStatus.FAILED -> R.string.memoh_ui_test_failed
                                                    MemohConnectivityStatus.UNVERIFIED -> R.string.memoh_ui_test_unverified
                                                })
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                testFailed = true
                                                testMessage = failure.message
                                                    ?: uiResources.getString(R.string.memoh_ui_test_failed)
                                            } finally {
                                                testing = false
                                            }
                                        }
                                    }) { Text(uiResources.getString(R.string.memoh_ui_test)) }
                                }
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                        )
                    },
                ),
            )

            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_models),
                items = buildList {
                    if (models.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        uiResources.getString(R.string.memoh_ui_no_models_configured),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.heightIn(min = 64.dp),
                            )
                        }
                    }
                    models.forEach { model ->
                        var showMenu by remember(model.id) { mutableStateOf(false) }
                        var modelTesting by remember(model.id) { mutableStateOf(false) }
                        var modelTestMessage by remember(model.id) { mutableStateOf("") }
                        var modelTestFailed by remember(model.id) { mutableStateOf(false) }
                        add {
                            SettingsItem(
                                headlineContent = { Text(model.label, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(model.modelId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (model.contextWindow > 0) {
                                                StreamBadge(
                                                    text = "ctx ${formatTokens(model.contextWindow)}",
                                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            model.reasoningEfforts.firstOrNull()?.let { effort ->
                                                StreamBadge(
                                                    text = effort,
                                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                                                )
                                            }
                                            if (!model.enabled) {
                                                StreamBadge(
                                                    text = uiResources.getString(R.string.memoh_ui_disabled),
                                                    container = MaterialTheme.colorScheme.errorContainer,
                                                    content = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            }
                                        }
                                        if (modelTestMessage.isNotBlank()) {
                                            Text(
                                                modelTestMessage,
                                                color = if (modelTestFailed) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Box {
                                        if (modelTesting) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(
                                                onClick = { showMenu = true },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    uiResources.getString(R.string.memoh_ui_options),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                        AgoraDropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 16.dp,
                                        ) {
                                            AgoraDropdownMenuItem(
                                                text = { Text(uiResources.getString(R.string.memoh_ui_test_connection)) },
                                                leadingIcon = { Icon(Icons.Default.Cloud, null) },
                                                onClick = {
                                                    showMenu = false
                                                    modelTesting = true
                                                    scope.launch {
                                                        try {
                                                            val status = memohConnectivityStatus(admin.testModel(model.id))
                                                            modelTestFailed = status == MemohConnectivityStatus.FAILED
                                                            modelTestMessage = uiResources.getString(when (status) {
                                                                MemohConnectivityStatus.PASSED -> R.string.memoh_ui_test_passed
                                                                MemohConnectivityStatus.FAILED -> R.string.memoh_ui_test_failed
                                                                MemohConnectivityStatus.UNVERIFIED -> R.string.memoh_ui_test_unverified
                                                            })
                                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                            throw cancelled
                                                        } catch (failure: Exception) {
                                                            modelTestFailed = true
                                                            modelTestMessage = failure.message
                                                                ?: uiResources.getString(R.string.memoh_ui_test_failed)
                                                        } finally {
                                                            modelTesting = false
                                                        }
                                                    }
                                                },
                                            )
                                            AgoraDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (model.enabled) uiResources.getString(R.string.memoh_ui_disable)
                                                        else uiResources.getString(R.string.memoh_ui_enable),
                                                    )
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                onClick = {
                                                    showMenu = false
                                                    scope.launch {
                                                        runCatching {
                                                            admin.updateModel(
                                                                model.id,
                                                                JSONObject().put("enable", !model.enabled),
                                                            )
                                                        }.onFailure { error = it.message.orEmpty() }
                                                            .onSuccess { onChanged() }
                                                    }
                                                },
                                            )
                                            AgoraDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        uiResources.getString(R.string.memoh_ui_remove),
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    scope.launch {
                                                        runCatching { admin.deleteModel(model.id) }
                                                            .onFailure { error = it.message.orEmpty() }
                                                            .onSuccess { onChanged() }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 64.dp),
                            )
                        }
                    }
                    add {
                        SettingsAddItem(
                            label = uiResources.getString(R.string.models_add_custom),
                            onClick = { addingModel = true },
                        )
                    }
                },
            )

            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_provider_actions),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_edit_provider)) },
                            leadingContent = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                                .clickable { editing = true },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    uiResources.getString(R.string.memoh_ui_remove_provider),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            supportingContent = {
                                Text(uiResources.getString(R.string.memoh_ui_remove_provider_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            modifier = Modifier
                                .heightIn(min = 64.dp)
                                .clickable { deleting = true },
                        )
                    },
                ),
            )

            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(
                            headlineContent = {
                                Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        )
                    }),
                )
            }
        }
    }

    if (addingModel) {
        MemohCreateModelDialog(
            admin = admin,
            provider = provider,
            onDismiss = { addingModel = false },
            onCreated = { addingModel = false; onChanged() },
        )
    }

    if (editing) {
        MemohProviderFormPage(
            title = uiResources.getString(R.string.memoh_ui_edit_provider),
            existing = provider,
            onBack = { editing = false },
            onSave = { name, clientType, baseUrl, apiKey ->
                scope.launch {
                    runCatching {
                        admin.updateProviderFromRequest(
                            provider.id,
                            providerRequestBody(name, clientType, baseUrl, apiKey, provider, create = false),
                        )
                    }.onFailure { error = it.message.orEmpty() }
                        .onSuccess { editing = false; onChanged() }
                }
            },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(uiResources.getString(R.string.memoh_ui_remove_provider)) },
            text = { Text(uiResources.getString(R.string.memoh_ui_remove_provider_confirm, provider.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        runCatching { admin.deleteProvider(provider.id) }
                            .onFailure { error = it.message.orEmpty() }
                            .onSuccess { onBack(); onChanged() }
                    }
                }) {
                    Text(uiResources.getString(R.string.memoh_ui_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(uiResources.getString(R.string.memoh_ui_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemohCreateModelDialog(
    admin: MemohAdminApi,
    provider: MemohProvider,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var modelId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiResources.getString(R.string.models_add_custom), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text(uiResources.getString(R.string.model_id_label)) },
                    singleLine = true,
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(uiResources.getString(R.string.models_alias_hint)) },
                    singleLine = true,
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && modelId.isNotBlank(), onClick = {
                saving = true
                error = ""
                scope.launch {
                    try {
                        admin.createModel(modelRequestBody(
                            providerId = provider.id,
                            modelId = modelId,
                            name = name.ifBlank { modelId.trim() },
                            enabled = true,
                            reasoningEfforts = emptyList(),
                            contextWindow = 0,
                            create = true,
                        ))
                        onCreated()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
                    } finally {
                        saving = false
                    }
                }
            }) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(uiResources.getString(R.string.add))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(uiResources.getString(R.string.provider_cancel))
            }
        },
    )
}

/** New-provider page: full settings page, not a dialog, so the form gets room and a title bar. */
@Composable
private fun MemohNewProviderPage(admin: MemohAdminApi, onBack: () -> Unit, onCreated: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    MemohProviderFormPage(
        title = uiResources.getString(R.string.memoh_ui_add_provider),
        existing = null,
        saving = saving,
        error = error,
        onBack = onBack,
        onSave = { name, clientType, baseUrl, apiKey ->
            saving = true
            error = ""
            scope.launch {
                runCatching {
                    admin.createProviderFromRequest(
                        providerRequestBody(name, clientType, baseUrl, apiKey, null, create = true),
                    )
                }.onFailure { error = it.message ?: uiResources.getString(R.string.memoh_ui_unknown_error) }
                    .onSuccess { onCreated() }
                saving = false
            }
        },
    )
}

@Composable
private fun MemohProviderFormPage(
    title: String,
    existing: MemohProvider?,
    onBack: () -> Unit,
    onSave: (name: String, clientType: String, baseUrl: String, apiKey: String) -> Unit,
    saving: Boolean = false,
    error: String = "",
) {
    val uiResources = LocalContext.current.resources
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var clientType by remember { mutableStateOf(existing?.clientType.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    val scrollState = rememberSettingsScrollState()

    CollapsingSettingsScaffold(title = title, onBack = onBack, scrollState = scrollState) {
        SettingsGroupColumn {
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_identity),
                items = listOf(
                    {
                        FieldRow(
                            value = name,
                            onValueChange = { name = it },
                            label = uiResources.getString(R.string.memoh_ui_name),
                        )
                    },
                    {
                        FieldRow(
                            value = clientType,
                            onValueChange = { clientType = it },
                            label = uiResources.getString(R.string.memoh_ui_client_type),
                            enabled = existing == null,
                        )
                    },
                ),
            )
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_credentials),
                items = listOf(
                    {
                        FieldRow(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = uiResources.getString(R.string.memoh_ui_base_url),
                        )
                    },
                    {
                        FieldRow(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = if (existing?.hasApiKey == true) {
                                uiResources.getString(R.string.memoh_ui_api_key_keep)
                            } else {
                                uiResources.getString(R.string.memoh_ui_api_key)
                            },
                            secret = true,
                        )
                    },
                ),
            )
            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(
                            headlineContent = {
                                Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        )
                    }),
                )
            }
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_actions),
                items = listOf({
                    SettingsItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (saving) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(uiResources.getString(R.string.memoh_ui_save))
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(enabled = !saving && name.isNotBlank() &&
                                (existing != null || clientType.isNotBlank())) {
                                onSave(name, clientType, baseUrl, apiKey)
                            },
                    )
                }),
            )
        }
    }
}

/** A settings row that is a text field, keeping the row's own padding instead of a bare dialog. */
@Composable
private fun FieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    secret: Boolean = false,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Token usage: the web shows a per-day stack; a phone shows the same numbers as rows. */
@Composable
internal fun MemohUsagePage(viewModel: MemohViewModel, onBack: () -> Unit) {
    val uiResources = LocalContext.current.resources
    var rangeDays by rememberSaveable { mutableStateOf(30) }
    val botId = viewModel.bot?.id
    var days by remember(botId, rangeDays) { mutableStateOf<List<MemohTokenUsageDay>>(emptyList()) }
    var models by remember(botId, rangeDays) { mutableStateOf<List<MemohModelUsage>>(emptyList()) }
    var loading by remember(botId, rangeDays) { mutableStateOf(true) }
    var error by remember(botId, rangeDays) { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var rangeMenu by remember { mutableStateOf(false) }
    val scrollState = rememberSettingsScrollState()

    LaunchedEffect(botId, rangeDays, refresh) {
        loading = true
        error = ""
        days = emptyList()
        models = emptyList()
        if (botId.isNullOrBlank()) {
            error = uiResources.getString(R.string.memoh_ui_no_accessible_bots_yet)
            loading = false
            return@LaunchedEffect
        }
        val range = memohUsageRange(rangeDays)
        try {
            val response = viewModel.api.tokenUsage(botId, range.from, range.to)
            days = decodeTokenUsage(response)
            models = decodeModelUsage(response)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    CollapsingSettingsScaffold(
        title = uiResources.getString(R.string.memoh_ui_usage),
        onBack = onBack,
        scrollState = scrollState,
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_bot_usage),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = {
                                Text(viewModel.bot?.title ?: uiResources.getString(R.string.memoh_settings_no_bots))
                            },
                            supportingContent = {
                                Text(
                                    if (loading) uiResources.getString(R.string.memoh_ui_loading)
                                    else uiResources.getString(R.string.memoh_ui_days_recorded, days.size),
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { refresh++ }, enabled = !loading) {
                                    Text(uiResources.getString(R.string.memoh_ui_refresh))
                                }
                            },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_range)) },
                            supportingContent = { Text("UTC") },
                            trailingContent = {
                                Box {
                                    TextButton(onClick = { rangeMenu = true }) {
                                        Text(uiResources.getString(R.string.memoh_ui_range_desc, rangeDays))
                                    }
                                    AgoraDropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                                        listOf(7, 30, 90).forEach { option ->
                                            AgoraDropdownMenuItem(
                                                text = { Text(uiResources.getString(R.string.memoh_ui_range_desc, option)) },
                                                onClick = { rangeDays = option; rangeMenu = false },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    },
                ),
            )
            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(headlineContent = { Text(error, color = MaterialTheme.colorScheme.error) })
                    }),
                )
            }
            if (models.isNotEmpty()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_by_model),
                    items = models.map { model ->
                        {
                            SettingsItem(
                                headlineContent = {
                                    Text(model.modelName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            model.providerName.takeIf { it.isNotBlank() },
                                            uiResources.getString(R.string.input_tokens_with_label, formatTokens(model.input)),
                                            uiResources.getString(R.string.output_tokens_with_label, formatTokens(model.output)),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Text(formatTokens(model.total), fontWeight = FontWeight.SemiBold)
                                },
                            )
                        }
                    },
                )
            }
            if (days.isNotEmpty()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_daily_tokens),
                    items = days.map { day ->
                        {
                            SettingsItem(
                                headlineContent = { Text(day.date) },
                                supportingContent = {
                                    Text(
                                        day.channels.entries.joinToString(" · ") { (channel, usage) ->
                                            "${channelLabel(channel)} ${formatTokens(usage.total)}"
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
            } else if (!loading && error.isBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_daily_tokens),
                    items = listOf({
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    uiResources.getString(R.string.memoh_ui_no_usage),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        )
                    }),
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
