package com.newoether.agora.memoh

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import org.json.JSONObject

private val REASONING_EFFORT_LABELS = linkedMapOf(
    "disable" to R.string.memoh_ui_reasoning_off,
    "enabled" to R.string.memoh_ui_reasoning_on,
    "minimal" to R.string.memoh_ui_reasoning_minimal,
    "low" to R.string.memoh_ui_reasoning_low,
    "medium" to R.string.memoh_ui_reasoning_medium,
    "high" to R.string.memoh_ui_reasoning_high,
    "xhigh" to R.string.memoh_ui_reasoning_xhigh,
    "max" to R.string.memoh_ui_reasoning_max,
)

/** Prefer the server's resolved options; config is a compatibility fallback for older servers. */
internal fun JSONObject.reasoningEfforts(): List<String> {
    val resolved = optJSONObject("reasoning")
    if (resolved != null) {
        if (!resolved.optBoolean("supported")) return emptyList()
        val declared = resolved.optJSONArray("efforts")
        return buildList {
            if (resolved.optBoolean("can_disable")) add("disable")
            if (declared != null) for (i in 0 until declared.length()) {
                declared.optString(i).takeIf(String::isNotBlank)?.let { add(if (it == "none") "disable" else it) }
            }
        }.distinct()
    }
    val config = optJSONObject("config") ?: return emptyList()
    val declared = config.optJSONObject("reasoning")?.optJSONArray("efforts")
        ?: config.optJSONArray("reasoning_efforts") ?: return emptyList()
    return (0 until declared.length()).mapNotNull { declared.optString(it).takeIf(String::isNotBlank) }
        .map { if (it == "none") "disable" else it }
        .filter { it in REASONING_EFFORT_LABELS }.distinct()
}

internal fun JSONObject.reasoningDefault(): String {
    val resolved = optJSONObject("reasoning")
    if (resolved != null) {
        if (!resolved.optBoolean("supported")) return ""
        if (resolved.optBoolean("default_off") && resolved.optBoolean("can_disable")) return "disable"
        return resolved.optString("default_effort").takeIf { it in reasoningEfforts() }.orEmpty()
    }
    val configured = optString("default_reasoning_effort").let { if (it == "none") "disable" else it }
    return configured.takeIf { it in reasoningEfforts() }.orEmpty()
}

/** Match the web composer's model-switch reconciliation without inventing an override. */
internal fun JSONObject.reconcileComposerReasoningEffort(stored: String): String {
    if (stored.isBlank()) return ""
    val normalized = if (stored == "none") "disable" else stored
    val resolved = optJSONObject("reasoning")
    if (resolved != null && !resolved.optBoolean("supported")) return stored
    val efforts = reasoningEfforts()
    if (efforts.isEmpty() || normalized in efforts) return normalized
    // When off is no longer possible, the web uses the new model's active default.
    if (normalized == "disable" && resolved != null && !resolved.optBoolean("can_disable")) {
        return resolved.optString("default_effort").ifBlank { stored }
    }
    return reasoningDefault().ifBlank { stored }
}

internal fun JSONObject.reasoningThinkingToggle(): Boolean =
    optJSONObject("config")?.let { it.optJSONObject("reasoning")?.optBoolean("toggle") == true || it.optString("thinking_mode") == "toggle" } == true

/** Reasoning strength for the selected model, matching the web composer's submenu. */
@OptIn(ExperimentalLayoutApi::class)
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
    val stored = controller.preferredReasoningEffort.let { if (it == "none") "disable" else it }
    // A stored tier this model no longer offers shows the reconciled tier, not a stale chip.
    val displayed = model.reconcileComposerReasoningEffort(stored).takeIf { it in efforts }
        ?: model.reasoningDefault()
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        efforts.forEach { effort ->
            androidx.compose.material3.FilterChip(
                selected = displayed == effort,
                onClick = { onSelect(effort, model.optString("id")) },
                label = { Text(REASONING_EFFORT_LABELS[effort]?.let(uiResources::getString) ?: effort) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}
