package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold

/** One bot settings section rendered inside the shared collapsing-title page shell. */
@Composable
internal fun MemohBotSectionPage(
    api: MemohApi,
    section: String,
    bot: MemohBot,
    agentController: MemohAgentController,
    canManage: Boolean,
    backupStatus: String,
    onBack: () -> Unit,
    onBotUpdated: (MemohBot) -> Unit,
    onOpenWorkspace: () -> Unit,
    onBackupExport: (List<String>, String) -> Unit,
    onBackupImport: () -> Unit,
    onDeleteRequested: () -> Unit,
    onNotice: (String, Boolean) -> Unit,
) {
    val title = when (section) {
        MemohBotSectionKey.GENERAL -> "General"
        MemohBotSectionKey.AGENTS -> "Agents"
        MemohBotSectionKey.CHANNELS -> "Channels"
        MemohBotSectionKey.RUNTIME -> "Runtime"
        MemohBotSectionKey.CONTAINER -> "Container"
        MemohBotSectionKey.MCP -> "MCP"
        MemohBotSectionKey.APPS -> "Apps"
        MemohBotSectionKey.ACCESS -> "Access"
        MemohBotSectionKey.ADVANCED -> "Advanced"
        MemohBotSectionKey.BACKUP -> "Backup"
        else -> stringResource(R.string.memoh_settings_bot)
    }

    CollapsingSettingsScaffold(title = title, onBack = onBack) {
        // Each tab body is a fillMaxSize Column/LazyColumn tuned for the old sheet; the shared
        // scaffold already supplies scrolling for page-level content, so give the section a
        // bounded column of its own and let its internal list scroll.
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Spacer(Modifier.height(4.dp))
            when (section) {
                MemohBotSectionKey.GENERAL -> BotGeneralSection(
                    api = api,
                    bot = bot,
                    canManage = canManage,
                    onBotUpdated = onBotUpdated,
                    onError = { onNotice(it, true) },
                )
                MemohBotSectionKey.AGENTS -> BotAgentsTab(
                    api = api,
                    botId = bot.id,
                    controller = agentController,
                    canManage = canManage,
                    onError = { onNotice(it, true) },
                )
                MemohBotSectionKey.CHANNELS -> MemohBotChannelsTab(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                )
                MemohBotSectionKey.RUNTIME -> MemohBotRuntimeTab(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                )
                MemohBotSectionKey.CONTAINER -> BotContainerSection(
                    api = api,
                    botId = bot.id,
                    onOpenWorkspace = onOpenWorkspace,
                )
                MemohBotSectionKey.MCP -> MemohBotMcpTab(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                )
                MemohBotSectionKey.APPS -> MemohBotAppsTab(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                )
                MemohBotSectionKey.ACCESS -> BotAccessSection(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                    onError = { onNotice(it, true) },
                )
                MemohBotSectionKey.ADVANCED -> MemohBotAdvancedTab(
                    api = api,
                    botId = bot.id,
                    canManage = canManage,
                )
                MemohBotSectionKey.BACKUP -> MemohBackupSettingsTab(
                    api = api,
                    bot = bot,
                    status = backupStatus,
                    canManage = canManage,
                    onExport = onBackupExport,
                    onChooseImport = onBackupImport,
                )
            }
        }
    }
}
