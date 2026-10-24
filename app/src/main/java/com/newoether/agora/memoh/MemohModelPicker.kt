package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import org.json.JSONObject

/**
 * Chat-time model preference (web composer parity). The catalog comes from the selected
 * Agent's models endpoint; picking a model also offers its reasoning efforts. The server
 * stays authoritative — a settled preference is shown as the effective model.
 */
@Composable
internal fun MemohModelPicker(
    vm: MemohViewModel,
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
) {
    val controller = vm.agentController
    LaunchedEffect(controller.selectedAgentId, vm.bot?.id) {
        val botId = vm.bot?.id ?: return@LaunchedEffect
        if (controller.selectedAgentId.isNotBlank()) controller.loadModels(botId, controller.selectedAgentId)
    }
    val catalog = controller.models
    var effortMenuFor by remember { mutableStateOf("") }

    Box {
        TextButton(
            onClick = { setExpanded(!expanded) },
            enabled = controller.selectedAgentId.isNotBlank(),
        ) {
            val shown = controller.preferredModelId.ifBlank {
                controller.settledModelId.ifBlank { catalog?.optString("configured_model_id").orEmpty() }
            }
            Text(
                when {
                    controller.modelsLoading -> "模型加载中…"
                    shown.isBlank() -> "模型"
                    else -> shortenModel(shown)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AgoraDropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            val models = catalog?.optJSONArray("models")
            if (models == null || models.length() == 0) {
                AgoraDropdownMenuItem(text = { Text("没有可用模型") }, onClick = { setExpanded(false) })
            } else {
                AgoraDropdownMenuItem(
                    text = { Text("跟随 Bot 默认") },
                    onClick = { controller.setModelPreference("", ""); setExpanded(false) },
                )
                for (i in 0 until models.length()) {
                    val model = models.optJSONObject(i) ?: continue
                    val id = model.optString("id")
                    val label = model.optString("name").ifBlank { id }
                    val current = controller.preferredModelId.ifBlank { controller.settledModelId }
                    AgoraDropdownMenuItem(
                        text = {
                            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(if (id == current) "● $label" else label)
                                val description = model.optString("description")
                                if (description.isNotBlank())
                                    Text(description, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            val defaultEffort = model.optString("default_reasoning_effort")
                            controller.setModelPreference(id, defaultEffort)
                            effortMenuFor = ""
                            setExpanded(false)
                        },
                    )
                }
            }
        }
    }
}

internal fun shortenModel(id: String): String = id.substringAfterLast('/').take(24)
