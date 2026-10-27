package com.newoether.agora.memoh

import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.settings.CollapsingSettingsLazyScaffold
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsItem
import kotlinx.coroutines.launch

/** One Bot settings page. Section labels resolve from the active app language. */
internal data class MemohBotSection(
    val key: String,
    @androidx.annotation.StringRes val title: Int,
    @androidx.annotation.StringRes val description: Int,
)

internal object MemohBotSectionKey {
    const val GENERAL = "general"
    const val AGENTS = "agents"
    const val CHANNELS = "channels"
    const val RUNTIME = "runtime"
    const val CONTAINER = "container"
    const val MCP = "mcp"
    const val APPS = "apps"
    const val ACCESS = "access"
    const val ADVANCED = "advanced"
    const val BACKUP = "backup"
    const val MODELS = "models"
}

private val botIdentitySections = listOf(
    MemohBotSection(MemohBotSectionKey.GENERAL, R.string.memoh_ui_general, R.string.memoh_ui_title_display_name_timezone_and_avatar),
    MemohBotSection(MemohBotSectionKey.ADVANCED, R.string.memoh_ui_advanced, R.string.memoh_ui_access_defaults_health_checks_and_maintenance),
)

private val botCapabilitySections = listOf(
    MemohBotSection(MemohBotSectionKey.MODELS, R.string.memoh_ui_models_and_behavior, R.string.memoh_ui_default_model_reasoning_memory_and_media),
    MemohBotSection(MemohBotSectionKey.AGENTS, R.string.memoh_ui_agents, R.string.memoh_ui_sub_agents_this_bot_can_delegate_to),
    MemohBotSection(MemohBotSectionKey.CHANNELS, R.string.memoh_ui_channels, R.string.memoh_ui_telegram_discord_and_other_linked_chats),
    MemohBotSection(MemohBotSectionKey.RUNTIME, R.string.memoh_ui_runtime, R.string.memoh_ui_register_and_manage_remote_worker_nodes),
    MemohBotSection(MemohBotSectionKey.CONTAINER, R.string.memoh_ui_container, R.string.memoh_ui_workspace_files_and_shell_access),
    MemohBotSection(MemohBotSectionKey.MCP, R.string.memoh_ui_mcp, R.string.memoh_ui_connected_mcp_servers_and_their_tools),
    MemohBotSection(MemohBotSectionKey.APPS, R.string.memoh_ui_apps, R.string.memoh_ui_installed_apps_and_workspace_skills),
)

private val botAdministrationSections = listOf(
    MemohBotSection(MemohBotSectionKey.ACCESS, R.string.memoh_ui_access, R.string.memoh_ui_who_may_use_or_manage_this_bot),
    MemohBotSection(MemohBotSectionKey.BACKUP, R.string.memoh_ui_backup, R.string.memoh_ui_export_or_restore_the_bot_s_data),
)

/**
 * Bot settings as a real settings destination instead of an eleven-tab sheet.
 *
 * The home page groups every section the bot sheet used to cram into one scrollable tab row; each
 * section opens as its own collapsing-title page, exactly like the app's other settings pages.
 * Only the section bodies are shared with the old sheet, so the data logic stays single-sourced.
 */
@Composable
internal fun MemohBotSettingsScreen(
    api: MemohApi,
    bot: MemohBot,
    agentController: MemohAgentController,
    onBack: () -> Unit,
    onBotUpdated: (MemohBot) -> Unit,
    onBotDeleted: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onBackupExport: (List<String>, String) -> Unit,
    onBackupImport: () -> Unit,
    backupStatus: String,
) {
    val uiResources = LocalContext.current.resources
    var section by remember(bot.id) { mutableStateOf<String?>(null) }
    var currentBot by remember(bot.id) { mutableStateOf(bot) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }
    val haptics = LocalAgoraHaptics.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(bot) { if (bot != currentBot) currentBot = bot }
    // The sheet used to re-fetch on open; a settings page can be entered straight from the home
    // list, so refresh here and adopt whatever the server says.
    LaunchedEffect(bot.id) {
        runCatching { api.getBot(bot.id) }.onSuccess {
            currentBot = it
            onBotUpdated(it)
        }
    }
    LaunchedEffect(message) {
        if (message.isNotBlank()) haptics.selection()
    }

    val canManage = currentBot.canManage
    BackHandler(enabled = section != null) { section = null }
    val open = section
    if (open == null) {
        CollapsingSettingsLazyScaffold(
            title = currentBot.title.ifBlank { stringResource(R.string.memoh_settings_bot) },
            onBack = onBack,
        ) {
            item { BotStatusCard(currentBot, canManage, message, messageIsError) }
            item { Spacer(Modifier.height(8.dp)) }
            item { BotSectionGroup(uiResources.getString(R.string.memoh_ui_identity), botIdentitySections, canManage) { section = it } }
            item { Spacer(Modifier.height(16.dp)) }
            item { BotSectionGroup(uiResources.getString(R.string.memoh_ui_capabilities), botCapabilitySections, canManage) { section = it } }
            item { Spacer(Modifier.height(16.dp)) }
            item { BotSectionGroup(uiResources.getString(R.string.memoh_ui_administration), botAdministrationSections, canManage) { section = it } }
            item {
                Spacer(Modifier.height(24.dp))
                DestructiveBotAction(
                    canManage = canManage,
                    botTitle = currentBot.title,
                    onDelete = { deleting = true },
                )
            }
        }
    } else {
        MemohBotSectionPage(
            api = api,
            section = open,
            bot = currentBot,
            agentController = agentController,
            canManage = canManage,
            backupStatus = backupStatus,
            onBack = { section = null },
            onBotUpdated = {
                currentBot = it
                onBotUpdated(it)
            },
            onOpenWorkspace = onOpenWorkspace,
            onBackupExport = onBackupExport,
            onBackupImport = onBackupImport,
            onNotice = { text, isError ->
                message = text
                messageIsError = isError
            },
        )
    }

    if (deleting) {
        DeleteBotConfirmDialog(
            bot = currentBot,
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                scope.launch {
                    runCatching { api.deleteBot(currentBot.id) }
                        .onSuccess {
                            onBotDeleted()
                            onBack()
                        }
                        .onFailure {
                            message = it.message ?: uiResources.getString(R.string.memoh_ui_delete_failed)
                            messageIsError = true
                        }
                }
            },
        )
    }
}

@Composable
private fun BotSectionGroup(
    title: String,
    sections: List<MemohBotSection>,
    canManage: Boolean,
    onOpen: (String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    SettingsGroup(
        title = title,
        items = sections.map { section ->
            {
                SettingsItem(
                    headlineContent = { Text(stringResource(section.title)) },
                    supportingContent = { Text(stringResource(section.description)) },
                    leadingContent = {
                        Icon(botSectionIcon(section.key), null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = if (!canManage) {
                        { Text(uiResources.getString(R.string.memoh_ui_read_only), style = MaterialTheme.typography.labelSmall) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onOpen(section.key) },
                )
            }
        },
    )
}

private fun botSectionIcon(key: String) = when (key) {
    MemohBotSectionKey.AGENTS -> Icons.Default.SmartToy
    MemohBotSectionKey.CHANNELS -> Icons.Default.Public
    MemohBotSectionKey.RUNTIME -> Icons.Default.Memory
    MemohBotSectionKey.CONTAINER -> Icons.Default.FolderOpen
    MemohBotSectionKey.MCP -> Icons.Default.Extension
    MemohBotSectionKey.ACCESS -> Icons.Default.Person
    MemohBotSectionKey.BACKUP -> Icons.Default.Save
    else -> Icons.Default.Tune
}

@Composable
private fun BotStatusCard(
    bot: MemohBot,
    canManage: Boolean,
    message: String,
    messageIsError: Boolean,
) {
    val uiResources = LocalContext.current.resources
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(bot.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            uiResources.getString(R.string.memoh_ui_id_1_s, bot.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledFact(uiResources.getString(R.string.memoh_ui_status), bot.status.ifBlank { if (bot.isActive) uiResources.getString(R.string.memoh_ui_active) else uiResources.getString(R.string.memoh_ui_inactive) })
            LabeledFact(uiResources.getString(R.string.memoh_ui_timezone), bot.timezone.ifBlank { uiResources.getString(R.string.memoh_ui_system) })
        }
        if (!canManage) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.memoh_bot_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LabeledFact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DestructiveBotAction(canManage: Boolean, botTitle: String, onDelete: () -> Unit) {
    val uiResources = LocalContext.current.resources
    if (!canManage) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(uiResources.getString(R.string.memoh_ui_delete_bot), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            Text(
                uiResources.getString(R.string.memoh_ui_removes_1_s_and_everything_the_server_keeps_for_it, botTitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(6.dp))
            Text(uiResources.getString(R.string.memoh_ui_delete))
        }
    }
}

/**
 * Second confirmation for an irreversible, server-wide delete: the word has to be typed, and the
 * confirm button stays disabled until it matches exactly.
 */
@Composable
internal fun DeleteBotConfirmDialog(
    bot: MemohBot,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember(bot.id) { mutableStateOf("") }
    val matches = typed == DELETION_CONFIRM_WORD

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memoh_bot_delete_title, bot.title)) },
        text = {
            Column {
                Text(stringResource(R.string.memoh_bot_delete_body))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(DELETION_CONFIRM_WORD) },
                    placeholder = { Text(DELETION_CONFIRM_WORD) },
                    isError = typed.isNotEmpty() && !matches,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
            ) {
                Text(
                    stringResource(R.string.memoh_bot_delete_confirm),
                    color = if (matches) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The exact word the delete dialog requires. Shared with tests. */
internal const val DELETION_CONFIRM_WORD = "Delete"
