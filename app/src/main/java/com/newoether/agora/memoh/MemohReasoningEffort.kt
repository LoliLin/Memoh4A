package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import org.json.JSONObject

private val REASONING_EFFORT_LABELS = linkedMapOf(
    "low" to R.string.memoh_ui_reasoning_low,
    "medium" to R.string.memoh_ui_reasoning_medium,
    "high" to R.string.memoh_ui_reasoning_high,
    "xhigh" to R.string.memoh_ui_reasoning_xhigh,
    "max" to R.string.memoh_ui_reasoning_max,
)

/** Models advertise their own efforts; unknown values fall back to the web client's ladder. */
internal fun JSONObject.reasoningEfforts(): List<String> {
    val config = optJSONObject("config") ?: return emptyList()
    val declared = config.optJSONArray("reasoning_efforts") ?: return emptyList()
    return (0 until declared.length()).mapNotNull { declared.optString(it).takeIf(String::isNotBlank) }
        .filter { it in REASONING_EFFORT_LABELS }
}

internal fun JSONObject.reasoningThinkingToggle(): Boolean =
    optJSONObject("config")?.optString("thinking_mode") == "toggle"

/** Reasoning strength for the selected model, matching the web composer's submenu. */
@Composable
internal fun MemohReasoningEffortSelector(
    controller: MemohAgentController,
    currentModelId: String,
    onSelect: (effort: String, modelId: String) -> Unit,
) {
    val uiResources = LocalContext.current.resources
    val entries = controller.models?.optJSONArray("models") ?: return
    val model = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
        .firstOrNull { it.optString("id") == currentModelId || it.optString("model_id") == currentModelId }
        ?: return
    val efforts = model.reasoningEfforts()
    if (efforts.isEmpty()) return
    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    Text(
        stringResource(R.string.memoh_ui_reasoning_effort),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        efforts.forEach { effort ->
            val selected = controller.preferredReasoningEffort == effort ||
                (controller.preferredReasoningEffort.isBlank() && effort == efforts.first())
            androidx.compose.material3.FilterChip(
                selected = selected,
                onClick = { onSelect(effort, model.optString("id")) },
                label = { Text(uiResources.getString(REASONING_EFFORT_LABELS[effort] ?: R.string.memoh_ui_reasoning_medium)) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}
