package com.newoether.agora.memoh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ReasoningBlock(block: MemohBlock) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    Column {
        Text(if (expanded) "Hide Thinking" else "Show Thinking",
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        if (expanded) Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp)) {
            Text(block.text, modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ToolBlock(block: MemohBlock, vm: MemohViewModel) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(block.toolName.ifBlank { "Tool" }, fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium)
            Text(block.text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            if (block.toolInput.isNotBlank() || block.toolOutput.isNotBlank() || block.toolDiff.isNotBlank()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide Details" else "Show Details")
                }
                if (expanded) {
                    if (block.toolInput.isNotBlank()) DetailField("Input", block.toolInput)
                    if (block.toolOutput.isNotBlank()) DetailField("Output", block.toolOutput)
                    if (block.toolDiff.isNotBlank()) DetailField("Diff", block.toolDiff)
                }
            }
            if (block.canApprove && block.approvalOptions.isNotEmpty()) {
                Text("Approval Required", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.approvalOptions.forEach { option ->
                        OutlinedButton(onClick = { vm.decide(block, option) },
                            enabled = vm.controlsReady && block.approvalId !in vm.waitingForInteraction) {
                            Text(option.name.ifBlank { option.kind.ifBlank { "Choose" } })
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
    Text(label, style = MaterialTheme.typography.labelMedium)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Text(content.take(12000), modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
    if (content.length > 12000) {
        Text("Preview truncated", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserInputForm(block: MemohBlock, vm: MemohViewModel) {
    var selections by remember(block.userInputId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var texts by remember(block.userInputId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Input Required", style = MaterialTheme.typography.titleSmall)
        for (question in block.questions) {
            Text(question.text, style = MaterialTheme.typography.bodyMedium)
            if (question.kind == "text") {
                OutlinedTextField(value = texts[question.id].orEmpty(),
                    onValueChange = { texts = texts + (question.id to it) },
                    placeholder = { Text(question.placeholder) },
                    modifier = Modifier.fillMaxWidth())
            } else {
                for (option in question.options) {
                    val selected = option.id in selections[question.id].orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val current = selections[question.id].orEmpty()
                            selections = selections + (question.id to
                                if (question.kind == "single_select") setOf(option.id)
                                else if (selected) current - option.id else current + option.id)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (question.kind == "single_select") RadioButton(selected = selected, onClick = null)
                        else Checkbox(checked = selected, onCheckedChange = null)
                        Column {
                            Text(option.label)
                            if (option.description.isNotBlank())
                                Text(option.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        val valid = block.questions.all { q ->
            !q.required || if (q.kind == "text") texts[q.id]?.isNotBlank() == true
            else selections[q.id]?.isNotEmpty() == true
        }
        val canSend = vm.controlsReady && block.userInputId !in vm.waitingForInteraction
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.answerUserInput(block, selections, texts) },
                enabled = canSend && valid) { Text("Submit Answers") }
            TextButton(onClick = { vm.answerUserInput(block, selections, texts, canceled = true) },
                enabled = canSend) { Text("Cancel Request") }
        }
    }
}
