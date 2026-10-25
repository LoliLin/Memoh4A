package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Session runtime controls from GET/PATCH runtime-controls: permission and planning modes,
 * the runtime-owned goal, and runtime commands. The server owns availability; unsupported
 * sections stay hidden instead of showing disabled rows.
 */
@Composable
internal fun MemohRuntimeControlsDialog(vm: MemohViewModel, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
    LaunchedEffect(vm.session?.id) { vm.refreshRuntimeControls() }
    val controls = vm.runtimeControlsJson
    val goal = vm.runtimeGoalJson
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiResources.getString(R.string.memoh_ui_runtime_controls)) },
        text = {
            if (controls == null) {
                Text(uiResources.getString(R.string.memoh_ui_loading_runtime_controls))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { ModeSection(vm, controls.optJSONObject("modes"), uiResources.getString(R.string.memoh_ui_permission_mode)) }
                    item { ModeSection(vm, controls.optJSONObject("plan_mode"), uiResources.getString(R.string.memoh_ui_plan_mode)) }
                    if (goal?.optJSONObject("goal") != null) {
                        item { GoalSection(vm, goal.optJSONObject("goal")!!) }
                    }
                    val commands = controls.optJSONArray("commands")
                    if (commands != null && commands.length() > 0) {
                        item {
                            Text(uiResources.getString(R.string.memoh_ui_runtime_commands),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        for (i in 0 until commands.length()) {
                            val cmd = commands.optJSONObject(i) ?: continue
                            item {
                                TextButton(
                                    onClick = { vm.runRuntimeCommand(cmd.optString("name")) },
                                    enabled = !vm.runtimeControlsBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(cmd.optString("running_text").ifBlank { cmd.optString("name") })
                                        val description = cmd.optString("description")
                                        if (description.isNotBlank())
                                            Text(description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            label = { Text(uiResources.getString(R.string.memoh_ui_run_command)) },
                            placeholder = { Text(uiResources.getString(R.string.memoh_ui_e_g_status)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            TextButton(
                                onClick = { vm.runRuntimeCommand(command.trim()); command = "" },
                                enabled = !vm.runtimeControlsBusy && command.isNotBlank(),
                            ) { Text(uiResources.getString(R.string.memoh_ui_run)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiResources.getString(R.string.memoh_ui_done)) } },
    )
}

@Composable
private fun ModeSection(vm: MemohViewModel, state: JSONObject?, title: String) {
    val uiResources = LocalContext.current.resources
    if (state == null || !state.optBoolean("supported", false)) return
    val current = state.optString("current_mode_id")
    val modes = state.optJSONArray("available_modes") ?: return
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        if (state.optBoolean("apply_on_next_turn", false)) {
            Text(uiResources.getString(R.string.memoh_ui_applies_on_the_next_turn),
                style = MaterialTheme.typography.bodySmall)
        }
        for (i in 0 until modes.length()) {
            val mode = modes.optJSONObject(i) ?: continue
            val id = mode.optString("id")
            TextButton(
                onClick = { vm.setRuntimeMode(id, plan = title == uiResources.getString(R.string.memoh_ui_plan_mode)) },
                enabled = !vm.runtimeControlsBusy && id != current,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = mode.optString("name").ifBlank { id }
                Text(if (id == current) "● $label" else label)
            }
        }
    }
}

@Composable
private fun GoalSection(vm: MemohViewModel, goal: JSONObject) {
    val uiResources = LocalContext.current.resources
    Column(Modifier.fillMaxWidth()) {
        Text(uiResources.getString(R.string.memoh_ui_goal), style = MaterialTheme.typography.labelMedium)
        Text(goal.optString("objective"), style = MaterialTheme.typography.bodyMedium)
        val status = goal.optString("status")
        val tokens = goal.optLong("tokens_used", -1L)
        val budget = goal.optLong("token_budget", -1L)
        if (status.isNotBlank()) Text(uiResources.getString(R.string.memoh_ui_status_1_s, status), style = MaterialTheme.typography.bodySmall)
        if (tokens >= 0) {
            Text(
                if (budget >= 0) uiResources.getString(R.string.memoh_ui_tokens_1_s_2_s, tokens, budget) else uiResources.getString(R.string.memoh_ui_tokens_1_s, tokens),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row {
            TextButton(onClick = vm::pauseRuntimeGoal, enabled = !vm.runtimeControlsBusy) {
                Text(uiResources.getString(R.string.memoh_ui_pause))
            }
            TextButton(onClick = vm::clearRuntimeGoal, enabled = !vm.runtimeControlsBusy) {
                Text(uiResources.getString(R.string.memoh_ui_clear))
            }
        }
    }
}
