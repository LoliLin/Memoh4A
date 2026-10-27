package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold
import com.newoether.agora.ui.settings.SettingsAddItem
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsGroupColumn
import com.newoether.agora.ui.settings.SettingsItem
import com.newoether.agora.ui.settings.rememberSettingsScrollState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class MemohNamedChoice(val id: String, val label: String, val detail: String = "")

/**
 * Bot settings the web console calls Interaction / Context & Memory / Multimedia.
 *
 * A phone shows them as one page of grouped rows: which model answers, how hard it thinks,
 * whether memory and live tool calls are on, and which providers serve search, fetch,
 * speech, transcription, images and video. Saving sends only the keys that changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemohBotModelSettingsPage(
    api: MemohApi,
    bot: MemohBot,
    canManage: Boolean,
    onBack: () -> Unit,
    onSaved: (MemohBotModelSettings) -> Unit,
) {
    val context = LocalContext.current
    val uiResources = context.resources
    val scope = rememberCoroutineScope()
    val admin = remember(api, api.scope) { MemohAdminApi(api) }
    val scrollState = rememberSettingsScrollState()

    var loading by remember(bot.id) { mutableStateOf(true) }
    var saving by remember(bot.id) { mutableStateOf(false) }
    var error by remember(bot.id) { mutableStateOf("") }
    var notice by remember(bot.id) { mutableStateOf("") }
    var retry by remember(bot.id) { mutableIntStateOf(0) }

    var original by remember(bot.id) { mutableStateOf(MemohBotModelSettings()) }
    var draft by remember(bot.id) { mutableStateOf(MemohBotModelSettings()) }
    var models by remember(bot.id) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var agents by remember(bot.id) { mutableStateOf<List<MemohAgent>>(emptyList()) }
    var profiles by remember(bot.id) { mutableStateOf<List<MemohAgentProfile>>(emptyList()) }
    var searchProviders by remember(bot.id) { mutableStateOf<List<MemohNamedChoice>>(emptyList()) }
    var fetchProviders by remember(bot.id) { mutableStateOf<List<MemohNamedChoice>>(emptyList()) }
    var speechModels by remember(bot.id) { mutableStateOf<List<MemohNamedChoice>>(emptyList()) }
    var transcriptionModels by remember(bot.id) { mutableStateOf<List<MemohNamedChoice>>(emptyList()) }
    var videoModels by remember(bot.id) { mutableStateOf<List<MemohNamedChoice>>(emptyList()) }

    var picking by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bot.id, retry) {
        loading = true
        error = ""
        try {
            // Settings decide what the rest of the page defaults to, so read them first.
            draft = decodeBotModelSettings(api.botSettings(bot.id))
            original = draft
            // Each of these can be absent on a restricted account; one failure must not
            // blank the whole page, so they are read independently.
            models = runCatching { memohModelsArray(api.request("/models")) }.getOrDefault(emptyList())
            agents = runCatching { api.agents(bot.id) }.getOrDefault(emptyList())
            profiles = runCatching { api.agentProfiles() }.getOrDefault(emptyList())
            searchProviders = runCatching {
                memohProviderChoicesFrom(admin.searchProviders(), enabledOnly = true)
            }.getOrDefault(emptyList())
            fetchProviders = runCatching {
                memohProviderChoicesFrom(admin.fetchProviders(), enabledOnly = true)
            }.getOrDefault(emptyList())
            speechModels = runCatching {
                memohVoiceModelChoices(admin.speechModels())
            }.getOrDefault(emptyList())
            transcriptionModels = runCatching {
                memohVoiceModelChoices(admin.transcriptionModels())
            }.getOrDefault(emptyList())
            videoModels = runCatching {
                memohVoiceModelChoices(admin.videoModels())
            }.getOrDefault(emptyList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    val chatModels = remember(models) { memohChatModelChoices(models) }
    val imageModels = remember(models) { memohImageModelChoices(models) }
    val agentChoices = remember(agents, profiles, draft.defaultBotAgentId) {
        memohDefaultAgentChoices(agents, profiles, draft.defaultBotAgentId)
    }
    val attempts = remember(models, draft.chatModelId) {
        memohModelEfforts(models, draft.chatModelId)
    }
    val resolvedAttempt = remember(models, draft.chatModelId, draft.reasoningEffort) {
        memohReconciledEffort(models, draft.chatModelId, draft.reasoningEffort)
    }
    val dirty = draft != original

    // The caller (`MemohBotSectionPage`) already owns the collapsing title bar, so this page
    // must not draw a second one; it only supplies the scrolling body.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
    ) {
            SettingsGroupColumn {
            if (loading) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(12.dp))
                                Text(uiResources.getString(R.string.memoh_ui_loading))
                            }
                        })
                    }),
                )
                return@SettingsGroupColumn
            }
            if (error.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(
                            headlineContent = { Text(error, color = MaterialTheme.colorScheme.error) },
                            trailingContent = {
                                TextButton(onClick = { retry++ }) {
                                    Text(uiResources.getString(R.string.memoh_ui_retry))
                                }
                            },
                        )
                    }),
                )
                return@SettingsGroupColumn
            }

            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_interaction),
                items = buildList {
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_default_agent),
                            value = agentChoices.firstOrNull { it.id == draft.defaultBotAgentId }?.label
                                ?: if (draft.defaultBotAgentId.isBlank()) uiResources.getString(R.string.memoh_ui_memoh_agent)
                                else uiResources.getString(R.string.memoh_ui_agent_unavailable),
                            enabled = canManage && !saving,
                        ) { picking = "agent" }
                    }
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_default_chat_model),
                            value = chatModels.firstOrNull { it.optString("id") == draft.chatModelId }
                                ?.let { it.optString("name").ifBlank { it.optString("model_id") } }
                                ?: if (draft.chatModelId.isBlank()) uiResources.getString(R.string.memoh_ui_not_configured)
                                else uiResources.getString(R.string.memoh_ui_model_unavailable),
                            enabled = canManage && !saving,
                        ) { picking = "chat" }
                    }
                    if (attempts.isNotEmpty()) add {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                uiResources.getString(R.string.memoh_ui_reasoning_effort),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                attempts.forEach { effort ->
                                    FilterChip(
                                        selected = resolvedAttempt == effort,
                                        enabled = canManage && !saving,
                                        onClick = { draft = draft.copy(reasoningEffort = effort) },
                                        label = { Text(memohEffortLabel(uiResources, effort)) },
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    add {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_show_tool_calls_in_im)) },
                            supportingContent = { Text(uiResources.getString(R.string.memoh_ui_show_tool_calls_in_im_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = draft.showToolCallsInIm,
                                    enabled = canManage && !saving,
                                    onCheckedChange = { draft = draft.copy(showToolCallsInIm = it) },
                                )
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                        )
                    }
                },
            )

            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_context_and_memory),
                items = buildList {
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_web_search),
                            value = providerLabel(searchProviders, draft.searchProviderId, uiResources),
                            enabled = canManage && !saving,
                        ) { picking = "search" }
                    }
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_web_fetch),
                            value = providerLabel(fetchProviders, draft.fetchProviderId, uiResources),
                            enabled = canManage && !saving,
                        ) { picking = "fetch" }
                    }
                    add {
                        SettingsItem(
                            headlineContent = { Text(uiResources.getString(R.string.memoh_ui_memory)) },
                            supportingContent = { Text(uiResources.getString(R.string.memoh_ui_memory_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = draft.memoryEnabled,
                                    enabled = canManage && !saving,
                                    onCheckedChange = { draft = draft.copy(memoryEnabled = it) },
                                )
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                        )
                    }
                },
            )

            SettingsGroup(
                title = uiResources.getString(R.string.memoh_ui_multimedia),
                items = buildList {
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_tts_model),
                            value = modelLabel(speechModels, draft.ttsModelId, uiResources),
                            enabled = canManage && !saving,
                        ) { picking = "tts" }
                    }
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_transcription_model),
                            value = modelLabel(transcriptionModels, draft.transcriptionModelId, uiResources),
                            enabled = canManage && !saving,
                        ) { picking = "transcription" }
                    }
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_image_model),
                            value = modelLabel(
                                imageModels.map { MemohNamedChoice(it.optString("id"), it.optString("name").ifBlank { it.optString("model_id") }) },
                                draft.imageModelId,
                                uiResources,
                            ),
                            enabled = canManage && !saving,
                        ) { picking = "image" }
                    }
                    add {
                        MemohSettingsChoice(
                            label = uiResources.getString(R.string.memoh_ui_video_model),
                            value = modelLabel(videoModels, draft.videoModelId, uiResources),
                            enabled = canManage && !saving,
                        ) { picking = "video" }
                    }
                },
            )

            if (draft.defaultBotAgentId.isNotBlank() &&
                agentChoices.none { it.id == draft.defaultBotAgentId && it.configured }) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    uiResources.getString(R.string.memoh_ui_default_agent_unavailable_hint),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        )
                    }),
                )
            }

            if (notice.isNotBlank()) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_status),
                    items = listOf({ SettingsItem(headlineContent = { Text(notice) }) }),
                )
            }

            if (canManage) {
                SettingsGroup(
                    title = uiResources.getString(R.string.memoh_ui_actions),
                    items = listOf({
                        SettingsItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (saving) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.size(8.dp))
                                    }
                                    Text(uiResources.getString(R.string.memoh_ui_save_settings))
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                                .clickable(enabled = !saving && dirty && agentUnavailable(agentChoices, draft).not()) {
                                    saving = true
                                    notice = ""
                                    scope.launch {
                                        try {
                                            // Only changed keys travel; the server reads an omitted
                                            // key as "keep" and "" as "clear".
                                            val body = memohBotModelSettingsPatch(original, draft)
                                            val agentBody = memohDefaultAgentPatch(
                                                original, draft,
                                                agents.firstOrNull { it.id == draft.defaultBotAgentId },
                                            )
                                            agentBody.keys().forEach { key -> body.put(key, agentBody.get(key)) }
                                            if (body.length() > 0) api.updateBotSettings(bot.id, body)
                                            original = draft
                                            onSaved(draft)
                                            notice = uiResources.getString(R.string.memoh_ui_saved)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = failure.message
                                                ?: uiResources.getString(R.string.memoh_ui_failed_to_update_bot_settings)
                                        } finally {
                                            saving = false
                                        }
                                    }
                                },
                        )
                    }),
                )
            }
        }
    }

    val options: List<MemohNamedChoice> = when (picking) {
        "agent" -> agentChoices.map { MemohNamedChoice(it.id, it.label) }
        "chat" -> chatModels.map { MemohNamedChoice(it.optString("id"), it.optString("name").ifBlank { it.optString("model_id") }) }
        "search" -> searchProviders
        "fetch" -> fetchProviders
        "tts" -> speechModels
        "transcription" -> transcriptionModels
        "image" -> imageModels.map { MemohNamedChoice(it.optString("id"), it.optString("name").ifBlank { it.optString("model_id") }) }
        "video" -> videoModels
        else -> emptyList()
    }
    val selection = when (picking) {
        "agent" -> draft.defaultBotAgentId
        "chat" -> draft.chatModelId
        "search" -> draft.searchProviderId
        "fetch" -> draft.fetchProviderId
        "tts" -> draft.ttsModelId
        "transcription" -> draft.transcriptionModelId
        "image" -> draft.imageModelId
        "video" -> draft.videoModelId
        else -> ""
    }
    val clearable = picking != "chat" && picking != "agent"
    if (picking != null) {
        MemohModelChoiceDialog(
            title = uiResources.getString(
                when (picking) {
                    "agent" -> R.string.memoh_ui_default_agent
                    "chat" -> R.string.memoh_ui_default_chat_model
                    "search" -> R.string.memoh_ui_web_search
                    "fetch" -> R.string.memoh_ui_web_fetch
                    "tts" -> R.string.memoh_ui_tts_model
                    "transcription" -> R.string.memoh_ui_transcription_model
                    "image" -> R.string.memoh_ui_image_model
                    else -> R.string.memoh_ui_video_model
                },
            ),
            choices = options,
            selected = selection,
            noneLabel = if (clearable) uiResources.getString(R.string.memoh_ui_none) else null,
            onDismiss = { picking = null },
            onSelected = { id ->
                draft = when (picking) {
                    "agent" -> draft.copy(defaultBotAgentId = id)
                    "chat" -> draft.copy(
                        chatModelId = id,
                        // Keep the tier valid for the newly chosen model.
                        reasoningEffort = memohReconciledEffort(models, id, draft.reasoningEffort)
                            .ifBlank { memohModelDefaultEffort(models, id) },
                    )
                    "search" -> draft.copy(searchProviderId = id)
                    "fetch" -> draft.copy(fetchProviderId = id)
                    "tts" -> draft.copy(ttsModelId = id)
                    "transcription" -> draft.copy(transcriptionModelId = id)
                    "image" -> draft.copy(imageModelId = id)
                    else -> draft.copy(videoModelId = id)
                }
                picking = null
            },
        )
    }
}

private fun agentUnavailable(
    choices: List<MemohDefaultAgentChoice>,
    draft: MemohBotModelSettings,
): Boolean = draft.defaultBotAgentId.isNotBlank() &&
    choices.none { it.id == draft.defaultBotAgentId && it.configured }

@Composable
private fun MemohSettingsChoice(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.Default.KeyboardArrowDown, null) },
        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun MemohModelChoiceDialog(
    title: String,
    choices: List<MemohNamedChoice>,
    selected: String,
    noneLabel: String?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val resources = LocalContext.current.resources
    var search by remember { mutableStateOf("") }
    val query = search.trim()
    val visible = remember(choices, query) {
        if (query.isBlank()) choices
        else choices.filter { it.label.contains(query, true) || it.detail.contains(query, true) || it.id.contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (choices.size > 8) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text(resources.getString(R.string.memoh_ui_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (noneLabel != null) item(key = "__none") {
                        SettingsItem(
                            headlineContent = { Text(noneLabel) },
                            modifier = Modifier.fillMaxWidth().selectable(selected = selected.isBlank()) {
                                onSelected("")
                            },
                        )
                    }
                    if (choices.isEmpty()) item(key = "__empty") {
                        Text(
                            resources.getString(R.string.memoh_ui_no_matching_choices),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    items(visible, key = { it.id }) { choice ->
                        SettingsItem(
                            headlineContent = { Text(choice.label.ifBlank { choice.id }) },
                            supportingContent = if (choice.detail.isBlank()) null else {{ Text(choice.detail) }},
                            modifier = Modifier.fillMaxWidth().selectable(selected = choice.id == selected) {
                                onSelected(choice.id)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(resources.getString(R.string.memoh_ui_cancel)) }
        },
    )
}

/** `/search-providers` and `/fetch-providers` list rows directly; disabled ones are skipped. */
internal fun memohProviderChoicesFrom(response: JSONObject, enabledOnly: Boolean): List<MemohNamedChoice> {
    val array = response.optJSONArray("items") ?: response.optJSONArray("data") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optString("id")
        if (id.isBlank()) return@mapNotNull null
        if (enabledOnly && !row.optBoolean("enable", true)) return@mapNotNull null
        MemohNamedChoice(id, row.optString("name").ifBlank { row.optString("provider") }.ifBlank { id })
    }.distinctBy { it.id }
}

/** Speech, transcription and video rows share `id`/`name`/`model_id`. */
internal fun memohVoiceModelChoices(response: JSONObject): List<MemohNamedChoice> {
    val array = response.optJSONArray("items") ?: response.optJSONArray("data") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optString("id")
        if (id.isBlank()) return@mapNotNull null
        MemohNamedChoice(id, row.optString("name").ifBlank { row.optString("model_id") })
    }.distinctBy { it.id }
}

private fun providerLabel(
    choices: List<MemohNamedChoice>,
    current: String,
    resources: android.content.res.Resources,
): String = when {
    choices.any { it.id == current } -> choices.first { it.id == current }.label
    current.isBlank() -> resources.getString(R.string.memoh_ui_default)
    else -> resources.getString(R.string.memoh_ui_current_selection)
}

private fun modelLabel(
    choices: List<MemohNamedChoice>,
    current: String,
    resources: android.content.res.Resources,
): String = when {
    choices.any { it.id == current } -> choices.first { it.id == current }.label
    current.isBlank() -> resources.getString(R.string.memoh_ui_default)
    else -> resources.getString(R.string.memoh_ui_current_selection)
}

internal fun memohEffortLabel(resources: android.content.res.Resources, effort: String): String =
    resources.getString(
        when (effort) {
            "disable", "none" -> R.string.memoh_ui_reasoning_off
            "enabled" -> R.string.memoh_ui_reasoning_on
            "minimal" -> R.string.memoh_ui_reasoning_minimal
            "low" -> R.string.memoh_ui_reasoning_low
            "medium" -> R.string.memoh_ui_reasoning_medium
            "high" -> R.string.memoh_ui_reasoning_high
            "xhigh" -> R.string.memoh_ui_reasoning_xhigh
            "max" -> R.string.memoh_ui_reasoning_max
            else -> return effort
        },
    )
