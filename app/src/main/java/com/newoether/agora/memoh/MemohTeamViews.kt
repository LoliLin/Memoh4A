package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem

@Composable
internal fun MemohTeamPicker(teams: MemohTeamController) {
    val uiResources = LocalContext.current.resources
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = true }, enabled = !teams.busy) {
            Text(teams.current?.title ?: uiResources.getString(R.string.memoh_ui_choose_team))
        }
        AgoraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.items.forEach { team ->
                AgoraDropdownMenuItem(text = { Text(if (team.id == teams.selectedId) "✓ ${team.title}" else team.title) },
                    onClick = { expanded = false; teams.select(team.id) })
            }
            AgoraDropdownMenuItem(text = { Text(uiResources.getString(R.string.memoh_ui_refresh_teams)) }, onClick = { expanded = false; teams.load() })
            if (teams.creationAllowed) AgoraDropdownMenuItem(text = { Text(uiResources.getString(R.string.memoh_ui_create_team)) },
                onClick = { expanded = false; creating = true })
        }
    }
    if (creating) MemohCreateTeam(teams) { creating = false }
}

@Composable
internal fun MemohTeamSetup(vm: MemohViewModel) {
    val uiResources = LocalContext.current.resources
    var creating by remember { mutableStateOf(false) }
    // Same centered, width-capped column as the entry surface. The card is Agora's own
    // onboarding container: 28 dp corners on surfaceContainer, nothing brighter on top.
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(uiResources.getString(R.string.memoh_ui_memoh_teams), style = MaterialTheme.typography.headlineSmall)
                if (vm.teams.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else {
                    Text(
                        uiResources.getString(R.string.memoh_ui_no_team_is_selected_refresh_your_memberships_or_create_a_team_to_continu),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MemohTeamPicker(vm.teams)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm.teams::load, modifier = Modifier.weight(1f)) {
                            Text(uiResources.getString(R.string.memoh_ui_retry))
                        }
                        if (vm.teams.creationAllowed) Button(
                            onClick = { creating = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(uiResources.getString(R.string.memoh_ui_create_team)) }
                    }
                }
                if (vm.error.isNotBlank()) Text(
                    vm.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = vm::logout) { Text(uiResources.getString(R.string.memoh_ui_sign_out)) }
            }
        }
    }
    if (creating) MemohCreateTeam(vm.teams) { creating = false }
}

@Composable
private fun MemohCreateTeam(teams: MemohTeamController, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiResources.getString(R.string.memoh_ui_create_team)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(uiResources.getString(R.string.memoh_ui_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(slug, { slug = it }, label = { Text(uiResources.getString(R.string.memoh_ui_slug_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(onClick = { teams.create(name, slug); onDismiss() }, enabled = name.isNotBlank() && !teams.busy) { Text(uiResources.getString(R.string.memoh_ui_create)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_cancel)) } })
}
