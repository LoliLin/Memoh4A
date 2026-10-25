package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics

@Composable
internal fun ReasoningBlock(block: MemohBlock) {
    val uiResources = LocalContext.current.resources
    var expanded by remember(block.id) { mutableStateOf(false) }
    val haptics = LocalAgoraHaptics.current
    val chevronAngle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "reasoningChevron")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptics.selection()
                expanded = !expanded
            }
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) uiResources.getString(R.string.memoh_ui_thinking_process) else uiResources.getString(R.string.memoh_ui_thinking_1_s_chars, block.text.length),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) uiResources.getString(R.string.memoh_ui_collapse) else uiResources.getString(R.string.memoh_ui_expand),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToolBlock(block: MemohBlock, vm: MemohViewModel) {
    val uiResources = LocalContext.current.resources
    var expanded by remember(block.id) { mutableStateOf(false) }
    val haptics = LocalAgoraHaptics.current
    val chevronAngle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "toolChevron")
    val hasDetails = block.toolInput.isNotBlank() || block.toolOutput.isNotBlank() || block.toolDiff.isNotBlank()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (block.canApprove) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = block.toolName.ifBlank { uiResources.getString(R.string.memoh_ui_tool_execution) },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (block.toolRunning) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        uiResources.getString(R.string.memoh_ui_running),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = uiResources.getString(R.string.memoh_ui_completed),
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        uiResources.getString(R.string.memoh_ui_done),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            if (block.text.isNotBlank() && block.text != uiResources.getString(R.string.memoh_ui_completed) && block.text != uiResources.getString(R.string.memoh_ui_running)) {
                Text(
                    text = block.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (hasDetails) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.selection()
                            expanded = !expanded
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) uiResources.getString(R.string.memoh_ui_hide_parameters_results) else uiResources.getString(R.string.memoh_ui_view_parameters_results),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(chevronAngle),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (block.toolInput.isNotBlank()) DetailField(uiResources.getString(R.string.memoh_ui_input_parameters), block.toolInput)
                        if (block.toolOutput.isNotBlank()) DetailField(uiResources.getString(R.string.memoh_ui_output_result), block.toolOutput)
                        if (block.toolDiff.isNotBlank()) DiffField(block.toolDiff)
                    }
                }
            }

            if (block.canApprove && block.approvalOptions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                uiResources.getString(R.string.memoh_ui_security_approval_required),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            block.approvalOptions.forEach { option ->
                                val isAllow = option.kind.contains("allow", ignoreCase = true)
                                if (isAllow) {
                                    Button(
                                        onClick = {
                                            haptics.confirm()
                                            vm.decide(block, option)
                                        },
                                        enabled = vm.controlsReady && block.approvalId !in vm.waitingForInteraction
                                    ) {
                                        Text(option.name.ifBlank { uiResources.getString(R.string.memoh_ui_allow) })
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.reject()
                                            vm.decide(block, option)
                                        },
                                        enabled = vm.controlsReady && block.approvalId !in vm.waitingForInteraction
                                    ) {
                                        Text(option.name.ifBlank { uiResources.getString(R.string.memoh_ui_deny) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (block.canRespond && block.questions.isNotEmpty()) {
                UserInputForm(block, vm)
            }
        }
    }
}

@Composable
private fun DetailField(label: String, content: String) {
    val uiResources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content.take(12000),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        if (content.length > 12000) {
            Text(
                uiResources.getString(R.string.memoh_ui_content_truncated_to_12_000_characters),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiffField(diff: String) {
    val uiResources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(uiResources.getString(R.string.memoh_ui_code_workspace_changes), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                diff.lines().take(300).forEach { line ->
                    val (bgColor, textColor) = when {
                        line.startsWith("+") -> Color(0x1F4CAF50) to Color(0xFF2E7D32)
                        line.startsWith("-") -> Color(0x1FF44336) to Color(0xFFC62828)
                        line.startsWith("@@") -> Color(0x1F2196F3) to Color(0xFF1565C0)
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInputForm(block: MemohBlock, vm: MemohViewModel) {
    val uiResources = LocalContext.current.resources
    var selections by remember(block.userInputId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var texts by remember(block.userInputId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val haptics = LocalAgoraHaptics.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiResources.getString(R.string.memoh_ui_information_required), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        for (question in block.questions) {
            Text(question.text, style = MaterialTheme.typography.bodyMedium)
            if (question.kind == "text") {
                OutlinedTextField(
                    value = texts[question.id].orEmpty(),
                    onValueChange = { texts = texts + (question.id to it) },
                    placeholder = { Text(question.placeholder) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                for (option in question.options) {
                    val selected = option.id in selections[question.id].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.selection()
                                val current = selections[question.id].orEmpty()
                                selections = selections + (question.id to
                                    if (question.kind == "single_select") setOf(option.id)
                                    else if (selected) current - option.id else current + option.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (question.kind == "single_select") RadioButton(selected = selected, onClick = null)
                        else Checkbox(checked = selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (question.allowCustom) {
                    OutlinedTextField(
                        value = texts[question.id].orEmpty(),
                        onValueChange = { value ->
                            texts = texts + (question.id to value)
                            if (value.isNotBlank()) selections = selections + (question.id to emptySet())
                        },
                        label = { Text(uiResources.getString(R.string.memoh_ui_custom_answer)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        val valid = block.questions.all { q ->
            !q.required || if (q.kind == "text") texts[q.id]?.isNotBlank() == true
            else selections[q.id]?.isNotEmpty() == true || (q.allowCustom && texts[q.id]?.isNotBlank() == true)
        }
        val canSend = vm.controlsReady && block.userInputId !in vm.waitingForInteraction
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    haptics.confirm()
                    vm.answerUserInput(block, selections, texts)
                },
                enabled = canSend && valid
            ) { Text(uiResources.getString(R.string.memoh_ui_submit_answers)) }
            TextButton(
                onClick = {
                    haptics.reject()
                    vm.answerUserInput(block, selections, texts, canceled = true)
                },
                enabled = canSend
            ) { Text(uiResources.getString(R.string.memoh_ui_cancel_request)) }
        }
    }
}
