package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = true }, enabled = !teams.busy) {
            Text(teams.current?.title ?: "Choose team")
        }
        AgoraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.items.forEach { team ->
                AgoraDropdownMenuItem(text = { Text(if (team.id == teams.selectedId) "✓ ${team.title}" else team.title) },
                    onClick = { expanded = false; teams.select(team.id) })
            }
            AgoraDropdownMenuItem(text = { Text("Refresh teams") }, onClick = { expanded = false; teams.load() })
            if (teams.creationAllowed) AgoraDropdownMenuItem(text = { Text("Create team") },
                onClick = { expanded = false; creating = true })
        }
    }
    if (creating) MemohCreateTeam(teams) { creating = false }
}

@Composable
internal fun MemohTeamSetup(vm: MemohViewModel) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Memoh teams", style = MaterialTheme.typography.headlineSmall)
        if (vm.teams.busy) CircularProgressIndicator()
        else {
            Text("No team is selected. Refresh your memberships or create a team to continue.")
            MemohTeamPicker(vm.teams)
            Button(onClick = vm.teams::load) { Text("Retry") }
            if (vm.teams.creationAllowed) Button(onClick = { creating = true }) { Text("Create team") }
        }
        if (vm.error.isNotBlank()) Text(vm.error, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = vm::logout) { Text("Sign out") }
    }
    if (creating) MemohCreateTeam(vm.teams) { creating = false }
}

@Composable
private fun MemohCreateTeam(teams: MemohTeamController, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create team") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(slug, { slug = it }, label = { Text("Slug (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(onClick = { teams.create(name, slug); onDismiss() }, enabled = name.isNotBlank() && !teams.busy) { Text("Create") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
