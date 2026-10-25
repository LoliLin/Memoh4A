package com.newoether.agora.memoh

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.components.CircularBackButton
import com.newoether.agora.ui.settings.SettingsCategory
import com.newoether.agora.ui.settings.SettingsContentMaxWidth
import com.newoether.agora.ui.settings.SettingsGroupData

internal val MemohNavigationPaneWidth = 400.dp
private val MemohNavigationPaneShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

/**
 * Wide-screen settings: the shared home list stays on the left with the selected page beside it.
 *
 * Agora's own `SettingsTwoPaneScreen` hard-wires `SettingsDestination`, which needs its
 * `ChatViewModel`; this mirrors its geometry and navigation-row styling while routing category
 * keys to Memoh's pages.
 */
@Composable
internal fun MemohSettingsTwoPane(
    viewModel: MemohViewModel,
    selected: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
    homeListState: LazyListState,
    onOpenBotSettings: () -> Unit,
    onNotice: (String) -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MemohSettingsNavigationPane(
            groups = memohSettingsGroups,
            listState = homeListState,
            selected = selected,
            onSelected = onSelected,
            onBack = onBack,
            modifier = Modifier
                .width(MemohNavigationPaneWidth)
                .fillMaxHeight()
                .clip(MemohNavigationPaneShape),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                MemohSettingsPage(
                    category = selected,
                    viewModel = viewModel,
                    onBack = onBack,
                    onOpenBotSettings = onOpenBotSettings,
                    onNotice = onNotice,
                )
            }
        }
    }
}

@Composable
private fun MemohSettingsNavigationPane(
    groups: List<SettingsGroupData>,
    listState: LazyListState,
    selected: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = statusBarTop + 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularBackButton(onClick = onBack, contentDescription = stringResource(R.string.back))
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                group.titleRes?.let { titleRes ->
                    item("header:$titleRes") {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                }
                group.items.forEach { category ->
                    item(category.key) {
                        MemohSettingsNavigationRow(
                            category = category,
                            selected = category.key == selected,
                            onClick = { onSelected(category.key) },
                        )
                    }
                }
            }
        }
    }
}

/** Same row geometry and selected treatment as Agora's navigation item. */
@Composable
private fun MemohSettingsNavigationRow(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    val primary = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val iconColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
    val supporting = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = shape, modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (category.iconRes != null) {
                Icon(painterResource(category.iconRes), null, modifier = Modifier.size(22.dp), tint = iconColor)
            } else {
                Icon(category.icon!!, null, modifier = Modifier.size(22.dp), tint = iconColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(category.titleRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = primary)
                Spacer(Modifier.height(3.dp))
                Text(stringResource(category.descriptionRes), style = MaterialTheme.typography.bodyMedium, color = supporting, maxLines = 1)
            }
        }
    }
}
