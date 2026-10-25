package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.model.ToolCallDisplayModes
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsGroupColumn
import com.newoether.agora.ui.settings.SettingsItem

/** Server endpoint, team scope, current bot and the live socket state. */
@Composable
internal fun MemohServerSettingsPage(viewModel: MemohViewModel, onBack: () -> Unit) {
    var teamMenu by remember { mutableStateOf(false) }
    var botMenu by remember { mutableStateOf(false) }
    val teams = viewModel.teams

    CollapsingSettingsScaffold(title = stringResource(R.string.memoh_settings_server), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.memoh_settings_endpoint),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_endpoint)) },
                            supportingContent = {
                                Text(
                                    viewModel.api.authUrl.ifBlank {
                                        stringResource(R.string.memoh_settings_endpoint_desc)
                                    },
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_connection_state)) },
                            supportingContent = {
                                Text(
                                    listOf(
                                        viewModel.connectionSummary(),
                                        stringResource(R.string.memoh_settings_connection_state_desc),
                                    ).joinToString(" · "),
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    },
                ),
            )

            if (teams.enabled) {
                SettingsGroup(
                    title = stringResource(R.string.memoh_settings_team),
                    items = listOf({
                        SettingsItem(
                            headlineContent = { Text(teams.current?.title ?: stringResource(R.string.memoh_settings_choose_team)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_team_desc)) },
                            leadingContent = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                TextButton(onClick = { teamMenu = true }, enabled = !teams.busy) {
                                    Text(stringResource(R.string.memoh_settings_choose_team))
                                }
                            },
                            modifier = Modifier.clickable(enabled = !teams.busy) { teamMenu = true },
                        )
                    }),
                )
            }

            SettingsGroup(
                title = stringResource(R.string.memoh_settings_bot_current),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(viewModel.bot?.title ?: stringResource(R.string.memoh_settings_no_bots)) },
                        supportingContent = { Text(stringResource(R.string.memoh_settings_bot_current_desc)) },
                        leadingContent = { Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = if (viewModel.bots.size > 1) {
                            {
                                TextButton(onClick = { botMenu = true }) {
                                    Text(stringResource(R.string.memoh_settings_switch_bot))
                                }
                            }
                        } else {
                            null
                        },
                        modifier = if (viewModel.bots.size > 1) {
                            Modifier.clickable { botMenu = true }
                        } else {
                            Modifier
                        },
                    )
                }),
            )
        }
    }

    if (teamMenu) {
        AgoraDropdownMenu(expanded = true, onDismissRequest = { teamMenu = false }) {
            teams.items.forEach { team ->
                AgoraDropdownMenuItem(
                    text = { Text(if (team.id == teams.selectedId) "✓ ${team.title}" else team.title) },
                    onClick = { teamMenu = false; teams.select(team.id) },
                )
            }
            AgoraDropdownMenuItem(
                text = { Text(stringResource(R.string.memoh_settings_refresh_team)) },
                onClick = { teamMenu = false; teams.load() },
            )
        }
    }
    if (botMenu) {
        AgoraDropdownMenu(expanded = true, onDismissRequest = { botMenu = false }) {
            viewModel.bots.forEach { candidate ->
                AgoraDropdownMenuItem(
                    text = { Text(if (candidate.id == viewModel.bot?.id) "✓ ${candidate.title}" else candidate.title) },
                    leadingIcon = {
                        MemohBotAvatar(candidate, size = 22.dp)
                    },
                    onClick = { botMenu = false; viewModel.switchBotFromSettings(candidate) },
                )
            }
        }
    }
}

/** Signed-in identity and the one destructive session action. */
@Composable
internal fun MemohAccountSettingsPage(viewModel: MemohViewModel, onBack: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }

    CollapsingSettingsScaffold(title = stringResource(R.string.memoh_settings_account), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.memoh_settings_account),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    listOf(viewModel.profileDisplayName, viewModel.login.username)
                                        .firstOrNull(String::isNotBlank)
                                        ?: stringResource(R.string.memoh_settings_sign_in_identity_desc),
                                )
                            },
                            leadingContent = {
                                if (viewModel.profileAvatarUrl.isNotBlank()) {
                                    MemohAvatarImage(
                                        url = viewModel.profileAvatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = {
                                Text(stringResource(R.string.memoh_settings_sign_out), color = MaterialTheme.colorScheme.error)
                            },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_sign_out_desc)) },
                            modifier = Modifier.clickable { confirmSignOut = true },
                        )
                    },
                ),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.memoh_settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.memoh_settings_sign_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.logout()
                    onBack()
                }) {
                    Text(stringResource(R.string.memoh_settings_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Preferences that change how the transcript behaves while you read it. */
@Composable
internal fun MemohChatBehaviorSettingsPage(viewModel: MemohViewModel, onBack: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val settings = viewModel.settings
    val stickToBottom by settings.stickToBottom.collectAsState()
    val hapticsEnabled by settings.hapticsEnabled.collectAsState()
    val reduceMotion by settings.reduceMotion.collectAsState()
    val blurEffects by settings.blurEffectsEnabled.collectAsState()
    val toolCallMode by settings.toolCallDisplayMode.collectAsState()

    CollapsingSettingsScaffold(title = stringResource(R.string.memoh_settings_chat), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.memoh_settings_chat),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.stick_to_bottom)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_stick_to_bottom_desc)) },
                            leadingContent = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(checked = stickToBottom, onCheckedChange = settings::setStickToBottom)
                            },
                            modifier = Modifier.clickable { settings.setStickToBottom(!stickToBottom) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_haptics)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_haptics_desc)) },
                            leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(checked = hapticsEnabled, onCheckedChange = settings::setHapticsEnabled)
                            },
                            modifier = Modifier.clickable { settings.setHapticsEnabled(!hapticsEnabled) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_reduce_motion)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_reduce_motion_desc)) },
                            leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(checked = reduceMotion, onCheckedChange = settings::setReduceMotion)
                            },
                            modifier = Modifier.clickable { settings.setReduceMotion(!reduceMotion) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_blur_effects)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_blur_effects_desc)) },
                            leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(checked = blurEffects, onCheckedChange = settings::setBlurEffectsEnabled)
                            },
                            modifier = Modifier.clickable { settings.setBlurEffectsEnabled(!blurEffects) },
                        )
                    },
                    {
                        // Tool cards dominate a long agent transcript, so the display mode sits
                        // next to the other reading preferences instead of only in Appearance.
                        var modeMenu by remember { mutableStateOf(false) }
                        val normalized = ToolCallDisplayModes.normalize(toolCallMode)
                        val label = when (normalized) {
                            ToolCallDisplayModes.COMPACT -> uiResources.getString(R.string.memoh_ui_compact)
                            ToolCallDisplayModes.TIMELINE -> uiResources.getString(R.string.memoh_ui_timeline)
                            else -> uiResources.getString(R.string.memoh_ui_grouped_timeline)
                        }
                        Box {
                            SettingsItem(
                                headlineContent = { Text(uiResources.getString(R.string.memoh_ui_tool_calls)) },
                                supportingContent = { Text(uiResources.getString(R.string.memoh_ui_how_tool_steps_are_grouped_in_the_transcript)) },
                                leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                },
                                modifier = Modifier.clickable { modeMenu = true },
                            )
                            AgoraDropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                                listOf(
                                    ToolCallDisplayModes.GROUPED_TIMELINE to uiResources.getString(R.string.memoh_ui_grouped_timeline),
                                    ToolCallDisplayModes.TIMELINE to uiResources.getString(R.string.memoh_ui_timeline),
                                    ToolCallDisplayModes.COMPACT to uiResources.getString(R.string.memoh_ui_compact),
                                ).forEach { (mode, modeLabel) ->
                                    AgoraDropdownMenuItem(
                                        text = { Text(if (mode == normalized) "✓ $modeLabel" else modeLabel) },
                                        onClick = { modeMenu = false; settings.setToolCallDisplayMode(mode) },
                                    )
                                }
                            }
                        }
                    },
                ),
            )
        }
    }
}

/** Entry point for the bot's own settings; the bot sheet already owns every field. */
@Composable
internal fun MemohBotSettingsPage(
    viewModel: MemohViewModel,
    onBack: () -> Unit,
    onOpenBotSettings: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val bot = viewModel.bot
    CollapsingSettingsScaffold(title = stringResource(R.string.memoh_settings_bot), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.memoh_settings_bot_current),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(bot?.title ?: stringResource(R.string.memoh_settings_no_bots)) },
                        supportingContent = { Text(stringResource(R.string.memoh_settings_bot_desc)) },
                        leadingContent = { Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = if (bot != null) {
                            { TextButton(onClick = onOpenBotSettings) { Text(uiResources.getString(R.string.memoh_ui_open)) } }
                        } else {
                            null
                        },
                        modifier = if (bot != null) Modifier.clickable(onClick = onOpenBotSettings) else Modifier,
                    )
                }),
            )
        }
    }
}

/** Build identity and live connection facts, with a copyable report for bug reports. */
@Composable
internal fun MemohDiagnosticsPage(
    viewModel: MemohViewModel,
    onBack: () -> Unit,
    onNotice: (String) -> Unit = {},
) {
    val uiResources = LocalContext.current.resources
    val context = LocalContext.current
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val version = packageInfo?.versionName.orEmpty().ifBlank { "?" }
    val versionCode = packageInfo?.let {
        @Suppress("DEPRECATION")
        it.versionCode
    } ?: 0
    CollapsingSettingsScaffold(title = stringResource(R.string.memoh_settings_diagnostics), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.memoh_settings_diagnostics),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_version)) },
                            supportingContent = { Text("v$version ($versionCode)") },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_package)) },
                            supportingContent = { Text(context.packageName) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_endpoint_auth)) },
                            supportingContent = { Text(viewModel.api.authUrl) },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_cookie_session)) },
                            supportingContent = {
                                Text(
                                    if (viewModel.api.usesSessionCookie) {
                                        stringResource(R.string.memoh_settings_enabled)
                                    } else {
                                        stringResource(R.string.memoh_settings_disabled)
                                    },
                                )
                            },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_history_state)) },
                            supportingContent = {
                                Text(
                                    viewModel.session?.let {
                                        if (viewModel.olderMessagesAvailable) {
                                            stringResource(R.string.memoh_settings_older_available)
                                        } else {
                                            stringResource(R.string.memoh_settings_history_complete)
                                        }
                                    } ?: stringResource(R.string.memoh_settings_history_state_desc),
                                )
                            },
                        )
                    },
                    {
                        // Everything a bug report needs, in one clipboard write, instead of asking
                        // the user to transcribe four rows by hand.
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.memoh_settings_copy_diagnostics)) },
                            supportingContent = { Text(stringResource(R.string.memoh_settings_package)) },
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        uiResources.getString(R.string.memoh_ui_memoh_diagnostics),
                                        buildDiagnosticsReport(
                                            version = version,
                                            versionCode = versionCode,
                                            packageName = context.packageName,
                                            authUrl = viewModel.api.authUrl,
                                            cookieSession = viewModel.api.usesSessionCookie,
                                            connection = viewModel.connectionSummary(),
                                            bot = viewModel.bot?.title.orEmpty(),
                                            team = viewModel.teams.current?.title.orEmpty(),
                                            olderPagesAvailable = viewModel.olderMessagesAvailable,
                                        ),
                                    ),
                                )
                                onNotice(context.getString(R.string.memoh_settings_copied))
                            },
                        )
                    },
                ),
            )
        }
    }
}

/** Stable, human-readable report shape so a pasted log is skimmable and never leaks a token. */
internal fun buildDiagnosticsReport(
    version: String,
    versionCode: Int,
    packageName: String,
    authUrl: String,
    cookieSession: Boolean,
    connection: String,
    bot: String,
    team: String,
    olderPagesAvailable: Boolean,
): String = buildString {
    appendLine("Memoh client")
    appendLine("version: $version ($versionCode)")
    appendLine("package: $packageName")
    appendLine("auth endpoint: $authUrl")
    appendLine("cookie session: $cookieSession")
    appendLine("connection: $connection")
    appendLine("bot: ${bot.ifBlank { "—" }}")
    appendLine("team: ${team.ifBlank { "—" }}")
    append("older pages available: $olderPagesAvailable")
}

/** Language picker. Agora's own page needs a ChatViewModel, so this reuses its list and setter. */
@Composable
internal fun MemohLanguagePage(
    settings: com.newoether.agora.data.repository.SettingsRepository,
    onBack: () -> Unit,
    onRestartNeeded: () -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val current by settings.appLanguage.collectAsState()
    var selected by remember(current) { mutableStateOf(current) }
    val options = remember {
        listOf(
            "system" to uiResources.getString(R.string.memoh_ui_system_default),
            "en" to "English",
            "zh" to "简体中文",
            "zh-Hant" to "繁體中文",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "ru" to "Русский",
            "pt-BR" to "Português (Brasil)",
            "ja" to "日本語",
            "ko" to "한국어",
            "ar" to "العربية",
            "vi" to "Tiếng Việt",
        )
    }

    CollapsingSettingsScaffold(title = stringResource(R.string.language_title), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.language_title),
                items = options.map { (code, label) ->
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = code
                                    if (code != current) {
                                        settings.setAppLanguage(code)
                                        onRestartNeeded()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            RadioButton(selected = selected == code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected == code) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
