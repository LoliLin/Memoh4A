package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class MemohHttpException(val status: Int, message: String) : IOException(message)
internal data class MemohBot(
    val id: String,
    val title: String,
    val name: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val timezone: String = "",
    val status: String = "",
    val isActive: Boolean = true,
    val createdAt: String = "",
    val currentUserPermissions: List<String> = emptyList(),
) {
    val canManage: Boolean
        get() = currentUserPermissions.isEmpty() || currentUserPermissions.contains("manage")
}
internal data class MemohUserGrant(
    val id: String,
    val botId: String,
    val userId: String,
    val userDisplayName: String = "",
    val userUsername: String = "",
    val userAvatarUrl: String = "",
    val isOwner: Boolean = false,
    val permissions: List<String> = emptyList(),
)
internal data class MemohUserCandidate(
    val id: String,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
)
internal data class MemohSession(
    val id: String,
    val title: String,
    val botAgentId: String = "",
    val runtimeType: String = "",
    val type: String = "chat",
)
internal data class MemohAccount(
    val id: String, val username: String, val email: String, val role: String,
    val displayName: String, val avatarUrl: String, val timezone: String,
    val titleModelId: String, val isActive: Boolean,
)
internal data class MemohAgent(
    val id: String, val name: String, val runtime: String, val enabled: Boolean,
    val credentialConnected: Boolean, val dependencyId: String, val metadata: JSONObject,
)
internal data class MemohAgentProfile(val id: String, val name: String, val description: String)
internal data class MemohAgentOption(val id: String, val label: String, val runtime: String, val description: String = "")
internal data class MemohDeviceLogin(val loginId: String, val userCode: String, val verificationUrl: String, val expiresAt: String = "")
internal data class MemohDeviceLoginResult(val status: String, val message: String = "")
internal data class MemohSessionPage(val items: List<MemohSession>, val nextCursor: String?)
internal data class MemohQueueItem(
    val id: String, val type: String, val text: String, val status: String,
)
internal data class MemohQueueState(
    val steerSupported: Boolean, val items: List<MemohQueueItem>,
)
internal data class MemohFile(val name: String, val path: String, val isDirectory: Boolean)
internal data class MemohFileContent(val text: String, val revision: String)
internal data class MemohSchedule(
    val id: String,
    val name: String,
    val pattern: String,
    val command: String,
    val description: String,
    val enabled: Boolean,
)

internal data class MemohBlock(
    val id: Int,
    val type: String,
    val text: String,
    val toolName: String = "",
    val toolRunning: Boolean = false,
    val approvalId: String = "",
    val canApprove: Boolean = false,
    val approvalOptions: List<MemohApprovalOption> = emptyList(),
    val toolInput: String = "",
    val toolOutput: String = "",
    val toolDiff: String = "",
    val userInputId: String = "",
    val canRespond: Boolean = false,
    val questions: List<MemohQuestion> = emptyList(),
)

internal data class MemohApprovalOption(val id: String, val name: String, val kind: String)
internal data class MemohQuestionOption(val id: String, val label: String, val description: String)
internal data class MemohQuestion(
    val id: String,
    val text: String,
    val kind: String,
    val required: Boolean,
    val allowCustom: Boolean,
    val placeholder: String,
    val options: List<MemohQuestionOption>,
)
internal data class MemohTurn(
    val id: String,
    val role: String,
    val text: String = "",
    val blocks: List<MemohBlock> = emptyList(),
)

internal fun decodeAccount(obj: JSONObject): MemohAccount = MemohAccount(
    id = obj.optString("id"), username = obj.optString("username"), email = obj.optString("email"),
    role = obj.optString("role"), displayName = obj.optString("display_name"),
    avatarUrl = obj.optString("avatar_url"), timezone = obj.optString("timezone").ifBlank { "UTC" },
    titleModelId = obj.optString("title_model_id"),
    isActive = obj.optBoolean("is_active", true) && obj.optBoolean("principal_is_active", true)
        && obj.optBoolean("membership_is_active", true),
)

internal fun decodeAgent(obj: JSONObject): MemohAgent {
    val dependency = obj.optJSONObject("dependency")
    return MemohAgent(
        id = obj.optString("id"), name = obj.optString("name"), runtime = obj.optString("runtime"),
        enabled = obj.optBoolean("enabled", true),
        credentialConnected = obj.optString("agent_credential_id").isNotBlank(),
        dependencyId = dependency?.optString("dependency_id").orEmpty(),
        metadata = JSONObject(obj.optJSONObject("metadata")?.toString() ?: "{}"),
    )
}

internal fun decodeSession(obj: JSONObject): MemohSession = MemohSession(
    id = obj.optString("id"), title = obj.optString("title").ifBlank { "Untitled" },
    botAgentId = obj.optString("bot_agent_id"), runtimeType = obj.optString("runtime_type"),
    type = obj.optString("type").ifBlank { "chat" },
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
        val userInput = obj.optJSONObject("user_input")
        val questionArray = userInput?.optJSONArray("questions")
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
            toolInput = obj.opt("input")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(),
            toolOutput = obj.opt("output")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(),
            toolDiff = obj.optString("diff"),
            userInputId = userInput?.optString("user_input_id").orEmpty(),
            canRespond = userInput?.optBoolean("can_respond") ?: false,
            questions = if (questionArray == null) emptyList() else
                (0 until questionArray.length()).mapNotNull { q ->
                    val question = questionArray.optJSONObject(q) ?: return@mapNotNull null
                    val options = question.optJSONArray("options")
                    MemohQuestion(
                        id = question.optString("id"),
                        text = question.optString("text"),
                        kind = question.optString("kind"),
                        required = question.optBoolean("required"),
                        allowCustom = question.optBoolean("allow_custom"),
                        placeholder = question.optString("placeholder"),
                        options = if (options == null) emptyList() else (0 until options.length()).mapNotNull { i ->
                            val option = options.optJSONObject(i) ?: return@mapNotNull null
                            MemohQuestionOption(option.optString("id"), option.optString("label"),
                                option.optString("description"))
                        },
                    )
                },
        )
    }
}
