package com.newoether.agora.memoh

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import com.newoether.agora.R
import com.newoether.agora.ui.settings.SettingsCategory
import com.newoether.agora.ui.settings.SettingsGroupData

internal object MemohSettingsCategory {
    const val SERVER = "memoh:server"
    const val ACCOUNT = "memoh:account"
    const val APPEARANCE = "appearance"
    const val LANGUAGE = "memoh:language"
    const val CHAT = "memoh:chat"
    const val BOT = "memoh:bot"
    const val DIAGNOSTICS = "memoh:diagnostics"
}

/**
 * Memoh's settings home in Agora's own [SettingsGroupData] shape, so the shared category list,
 * collapsing title and page transitions render it with zero new navigation code.
 *
 * Only pages whose data exists in this build are listed. Agora-only categories (providers, models,
 * MCP, shell sandboxes, local runtime…) are absent on purpose: the Memoh server owns those and this
 * client has no local store that could honour those settings.
 */
internal val memohSettingsGroups: List<SettingsGroupData> = listOf(
    SettingsGroupData(
        titleRes = R.string.memoh_settings_group_connection,
        items = listOf(
            SettingsCategory(
                MemohSettingsCategory.SERVER,
                R.string.memoh_settings_server,
                R.string.memoh_settings_server_desc,
                Icons.Default.Cloud,
            ),
            SettingsCategory(
                MemohSettingsCategory.ACCOUNT,
                R.string.memoh_settings_account,
                R.string.memoh_settings_account_desc,
                Icons.Default.Person,
            ),
        ),
    ),
    SettingsGroupData(
        titleRes = R.string.memoh_settings_group_appearance,
        items = listOf(
            SettingsCategory(
                MemohSettingsCategory.APPEARANCE,
                R.string.settings_appearance,
                R.string.settings_appearance_desc,
                Icons.Default.Palette,
            ),
            SettingsCategory(
                MemohSettingsCategory.LANGUAGE,
                R.string.language_title,
                R.string.language_desc,
                Icons.Default.Translate,
            ),
        ),
    ),
    SettingsGroupData(
        titleRes = R.string.memoh_settings_group_responses,
        items = listOf(
            SettingsCategory(
                MemohSettingsCategory.CHAT,
                R.string.memoh_settings_chat,
                R.string.memoh_settings_chat_desc,
                Icons.AutoMirrored.Filled.Chat,
            ),
            SettingsCategory(
                MemohSettingsCategory.BOT,
                R.string.memoh_settings_bot,
                R.string.memoh_settings_bot_desc,
                Icons.Default.SmartToy,
            ),
        ),
    ),
    SettingsGroupData(
        titleRes = R.string.settings_group_about,
        items = listOf(
            SettingsCategory(
                MemohSettingsCategory.DIAGNOSTICS,
                R.string.memoh_settings_diagnostics,
                R.string.memoh_settings_diagnostics_desc,
                Icons.Default.Info,
            ),
        ),
    ),
)

/** True for a category this build can open; the shared list only ever emits these. */
internal fun isMemohSettingsCategory(key: String): Boolean =
    memohSettingsGroups.any { group -> group.items.any { it.key == key } }
