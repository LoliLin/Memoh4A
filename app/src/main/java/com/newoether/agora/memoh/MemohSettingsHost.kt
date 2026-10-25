package com.newoether.agora.memoh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.newoether.agora.ui.settings.GuardedAnimatedContent
import com.newoether.agora.ui.settings.LocalSettingsScrollRetention
import com.newoether.agora.ui.settings.SettingsAppearancePage
import com.newoether.agora.ui.settings.SettingsCategoryHome
import com.newoether.agora.ui.settings.SettingsScrollRetention
import com.newoether.agora.ui.settings.SettingsTwoPaneMinWidth
import com.newoether.agora.ui.settings.rememberSettingsLazyListState

/**
 * Memoh's settings, rendered through Agora's own settings shell.
 *
 * The category home, collapsing large title, page transitions, scroll retention and the
 * two-pane split on wide screens are all shared. Only the page bodies that Memoh can actually
 * back with data are new; `SettingsAppearancePage` is reused verbatim because it reads the same
 * `SettingsRepository` Memoh already owns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemohSettingsHost(
    viewModel: MemohViewModel,
    onDismiss: () -> Unit,
    onOpenBotSettings: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollRetention = remember { SettingsScrollRetention() }
    val homeListState = rememberSettingsLazyListState()
    // Settings pages have no snackbar host of their own; a short toast is what Agora's own
    // appearance page expects to receive from this callback.
    val context = LocalContext.current
    val notify: (String) -> Unit = remember(context) {
        { message -> android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() }
    }

    BackHandler {
        if (selected == null) onDismiss() else selected = null
    }

    CompositionLocalProvider(LocalSettingsScrollRetention provides scrollRetention) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Exactly Agora's breakpoint: below it a pushed page replaces the home list, above it
            // the home list stays visible beside the page.
            val twoPane = maxWidth >= SettingsTwoPaneMinWidth
            if (twoPane) {
                MemohSettingsTwoPane(
                    viewModel = viewModel,
                    selected = selected ?: MemohSettingsCategory.SERVER,
                    onSelected = { selected = it },
                    onBack = onDismiss,
                    homeListState = homeListState,
                    onOpenBotSettings = onOpenBotSettings,
                    onNotice = notify,
                )
            } else {
                GuardedAnimatedContent(
                    targetState = selected,
                    forward = selected != null,
                ) { category ->
                    if (category == null) {
                        SettingsCategoryHome(
                            settingsGroups = memohSettingsGroups,
                            listState = homeListState,
                            onBack = onDismiss,
                            onCategorySelected = { selected = it },
                        )
                    } else {
                        MemohSettingsPage(
                            category = category,
                            viewModel = viewModel,
                            onBack = { selected = null },
                            onOpenBotSettings = onOpenBotSettings,
                            onNotice = notify,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MemohSettingsPage(
    category: String,
    viewModel: MemohViewModel,
    onBack: () -> Unit,
    onOpenBotSettings: () -> Unit,
    onNotice: (String) -> Unit = {},
) {
    when (category) {
        MemohSettingsCategory.APPEARANCE -> SettingsAppearancePage(
            settings = viewModel.settings,
            onSnackbar = onNotice,
            onBack = onBack,
        )
        MemohSettingsCategory.LANGUAGE -> MemohLanguagePage(
            settings = viewModel.settings,
            onBack = onBack,
            onRestartNeeded = viewModel::requestAppLanguageRestart,
        )
        MemohSettingsCategory.ACCOUNT -> MemohAccountSettingsPage(viewModel, onBack)
        MemohSettingsCategory.CHAT -> MemohChatBehaviorSettingsPage(viewModel, onBack)
        MemohSettingsCategory.BOT -> MemohBotSettingsPage(viewModel, onBack, onOpenBotSettings)
        MemohSettingsCategory.DIAGNOSTICS -> MemohDiagnosticsPage(viewModel, onBack, onNotice)
        else -> MemohServerSettingsPage(viewModel, onBack)
    }
}
