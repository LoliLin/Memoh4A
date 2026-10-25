package com.newoether.agora.memoh

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.settings.CollapsingSettingsTitleBar
import com.newoether.agora.ui.settings.SettingsBarHeight
import com.newoether.agora.ui.settings.SettingsContentMaxWidth
import com.newoether.agora.ui.settings.SettingsTitleAreaHeight
import com.newoether.agora.ui.settings.settingsTitleTravel

/** Reuse the docked settings header, giving each section's own scroll container a finite viewport. */
@Composable
internal fun MemohBotSectionFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .widthIn(max = SettingsContentMaxWidth)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(top = statusBarTop + SettingsBarHeight, start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) { content() }
        androidx.compose.runtime.CompositionLocalProvider(
            com.newoether.agora.ui.settings.LocalSettingsPaneBackButtonVisible provides true,
        ) {
            CollapsingSettingsTitleBar(
                title = title,
                backDescription = stringResource(R.string.back),
                fraction = 1f,
                statusBarTop = statusBarTop,
                titleAreaHeight = SettingsTitleAreaHeight,
                titleTravel = settingsTitleTravel,
                onBack = onBack,
            )
        }
    }
}

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
    onNotice: (String, Boolean) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val title = when (section) {
        MemohBotSectionKey.GENERAL -> uiResources.getString(R.string.memoh_ui_general)
        MemohBotSectionKey.AGENTS -> uiResources.getString(R.string.memoh_ui_agents)
        MemohBotSectionKey.CHANNELS -> uiResources.getString(R.string.memoh_ui_channels)
        MemohBotSectionKey.RUNTIME -> uiResources.getString(R.string.memoh_ui_runtime)
        MemohBotSectionKey.CONTAINER -> uiResources.getString(R.string.memoh_ui_container)
        MemohBotSectionKey.MCP -> uiResources.getString(R.string.memoh_ui_mcp)
        MemohBotSectionKey.APPS -> uiResources.getString(R.string.memoh_ui_apps)
        MemohBotSectionKey.ACCESS -> uiResources.getString(R.string.memoh_ui_access)
        MemohBotSectionKey.ADVANCED -> uiResources.getString(R.string.memoh_ui_advanced)
        MemohBotSectionKey.BACKUP -> uiResources.getString(R.string.memoh_ui_backup)
        else -> stringResource(R.string.memoh_settings_bot)
    }
    var notice by remember(section, bot.id) { mutableStateOf("") }
    val reportError: (String) -> Unit = { notice = it; onNotice(it, true) }
    MemohBotSectionFrame(title, onBack) {
        Column(Modifier.fillMaxSize()) {
            if (notice.isNotBlank()) Text(
                notice, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (section) {
                    MemohBotSectionKey.GENERAL -> BotGeneralSection(api, bot, canManage, onBotUpdated, reportError)
                    MemohBotSectionKey.AGENTS -> BotAgentsTab(api, bot.id, agentController, canManage, reportError)
                    MemohBotSectionKey.CHANNELS -> MemohBotChannelsTab(api, bot.id, canManage)
                    MemohBotSectionKey.RUNTIME -> MemohBotRuntimeTab(api, bot.id, canManage)
                    MemohBotSectionKey.CONTAINER -> BotContainerSection(api, bot.id, onOpenWorkspace)
                    MemohBotSectionKey.MCP -> MemohBotMcpTab(api, bot.id, canManage)
                    MemohBotSectionKey.APPS -> MemohBotAppsTab(api, bot.id, canManage)
                    MemohBotSectionKey.ACCESS -> BotAccessSection(api, bot.id, canManage, reportError)
                    MemohBotSectionKey.ADVANCED -> MemohBotAdvancedTab(api, bot.id, canManage)
                    MemohBotSectionKey.BACKUP -> MemohBackupSettingsTab(api, bot, backupStatus, canManage, onBackupExport, onBackupImport)
                }
            }
        }
    }
}
