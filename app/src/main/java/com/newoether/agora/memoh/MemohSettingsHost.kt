package com.newoether.agora.memoh

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.settings.SettingsAppearancePage
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsGroupColumn
import com.newoether.agora.ui.settings.SettingsItem
import android.widget.Toast.LENGTH_SHORT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemohSettingsHost(
    viewModel: MemohViewModel,
    onDismiss: () -> Unit,
) {
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val showNotice: (String) -> Unit = remember(context) {
        { message -> Toast.makeText(context, message, LENGTH_SHORT).show() }
    }

    if (showAppearance) {
        BackHandler { showAppearance = false }
        SettingsAppearancePage(
            settings = viewModel.settings,
            onSnackbar = showNotice,
            onBack = { showAppearance = false },
        )
        return
    }

    BackHandler(onBack = onDismiss)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close settings")
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsGroupColumn {
                SettingsGroup(
                    title = "Personalization",
                    items = listOf({
                        SettingsItem(
                            modifier = Modifier.clickable { showAppearance = true },
                            headlineContent = { Text("Appearance") },
                            supportingContent = { Text("Theme, color, fonts and chat display") },
                            leadingContent = {
                                Icon(Icons.Default.Palette, contentDescription = null)
                            },
                        )
                    }),
                )
                SettingsGroup(
                    title = "Memoh connection",
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = { Text("Server") },
                                supportingContent = { Text(viewModel.api.authUrl) },
                            )
                        },
                        {
                            SettingsItem(
                                headlineContent = { Text("Team") },
                                supportingContent = { Text(viewModel.teams.current?.title ?: "—") },
                            )
                        },
                        {
                            SettingsItem(
                                headlineContent = { Text("Bot") },
                                supportingContent = { Text(viewModel.bot?.title ?: "—") },
                            )
                        },
                    ),
                )
                SettingsGroup(
                    title = "Account",
                    items = listOf({
                        SettingsItem(
                            modifier = Modifier.clickable {
                                viewModel.logout()
                                onDismiss()
                            },
                            headlineContent = {
                                Text("Sign out", color = MaterialTheme.colorScheme.error)
                            },
                        )
                    }),
                )
            }
        }
    }
}
