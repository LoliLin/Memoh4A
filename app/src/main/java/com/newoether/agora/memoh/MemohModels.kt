package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class MemohHttpException(val status: Int, message: String) : IOException(message)
internal data class MemohBot(val id: String, val title: String)
internal data class MemohSession(val id: String, val title: String)
internal data class MemohSessionPage(val items: List<MemohSession>, val nextCursor: String?)

internal data class MemohBlock(
    val id: Int,
    val type: String,
    val text: String,
    val toolName: String = "",
    val toolRunning: Boolean = false,
    val approvalId: String = "",
    val canApprove: Boolean = false,
    val approvalOptions: List<MemohApprovalOption> = emptyList(),
)

internal data class MemohApprovalOption(val id: String, val name: String, val kind: String)
internal data class MemohTurn(
    val id: String,
    val role: String,
    val text: String = "",
    val blocks: List<MemohBlock> = emptyList(),
)

internal fun decodeTurn(obj: JSONObject): MemohTurn {
    val role = obj.optString("role")
    return MemohTurn(
        // Assistant and user turns may share turn_id. The row identity is unique.
        id = obj.optString("id").ifBlank {
            obj.optString("turn_id") + ":" + role
        },
        role = role,
        text = obj.optString("text"),
        blocks = decodeBlocks(obj.optJSONArray("messages")),
    )
}

internal fun decodeBlocks(array: JSONArray?): List<MemohBlock> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index) ?: return@mapNotNull null
        val kind = obj.optString("type")
        val approval = obj.optJSONObject("approval")
        val opts = approval?.optJSONArray("options")
        MemohBlock(
            id = obj.optInt("id", index),
            type = kind,
            text = when (kind) {
                "text", "reasoning", "error", "notice", "command" -> obj.optString("content")
                "tool" -> {
                    val progress = obj.optJSONArray("progress")
                    if (progress != null && progress.length() > 0) "Running · ${progress.length()} updates"
                    else if (obj.optBoolean("running")) "Running"
                    else "Completed"
                }
                else -> ""
            },
            toolName = obj.optString("name"),
            toolRunning = obj.optBoolean("running"),
            approvalId = approval?.optString("approval_id").orEmpty(),
            canApprove = approval?.optBoolean("can_approve") ?: false,
            approvalOptions = if (opts == null) emptyList() else (0 until opts.length()).mapNotNull { n ->
                val entry = opts.optJSONObject(n) ?: return@mapNotNull null
                MemohApprovalOption(entry.optString("id"), entry.optString("name"), entry.optString("kind"))
            },
        )
    }
}
