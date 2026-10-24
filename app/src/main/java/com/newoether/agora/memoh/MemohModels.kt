package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class MemohHttpException(val status: Int, message: String) : IOException(message)

/** Rows per history request. A page shorter than this is the start of the conversation. */
internal const val HISTORY_PAGE_SIZE = 50
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
    /** "new_session" (default) or "existing_session". */
    val runTarget: String = "new_session",
    /** Session reused by existing_session mode. */
    val targetSessionId: String = "",
    /** Native model override (models.id); empty means the bot default. */
    val modelId: String = "",
    /** BotAgent selected for new sessions; empty means the built-in runtime. */
    val botAgentId: String = "",
    val maxRunSeconds: Int = 0,
    val currentCalls: Int = 0,
    val maxCalls: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** One recorded run of a schedule. */
internal data class MemohScheduleLog(
    val id: String,
    val scheduleId: String,
    val sessionId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String,
    val errorCode: String,
    val errorMessage: String,
    val resultText: String,
)

/** Run records identify their conversation with session_id, not log_id. */
internal fun decodeScheduleSessions(response: JSONObject): MemohSessionPage {
    val entries = response.optJSONArray("items") ?: JSONArray()
    val sessions = (0 until entries.length()).mapNotNull { i ->
        val item = entries.optJSONObject(i) ?: return@mapNotNull null
        val id = item.optString("session_id").takeIf { it.isNotBlank() && it != "null" }
            ?: return@mapNotNull null
        MemohSession(id, item.optString("title").ifBlank { "Scheduled Run" })
    }.distinctBy { it.id }
    return MemohSessionPage(sessions, response.optString("next_cursor").takeIf { it.isNotBlank() })
}

internal data class MemohAttachment(
    val id: String = "",
    val name: String = "",
    val mime: String = "",
    val type: String = "",
    val size: Long = 0,
    val url: String = "",
    val base64: String = "",
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
    val attachments: List<MemohAttachment> = emptyList(),
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
    val attachments: List<MemohAttachment> = emptyList(),
    /** Server turn_id used by retry_message/edit_message; may differ from the row identity. */
    val turnId: String = "",
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

internal fun decodeAttachments(array: JSONArray?): List<MemohAttachment> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val id = item.optString("id").ifBlank { item.optString("storage_key") }
        val name = item.optString("name").ifBlank { "attachment" }
        val mime = item.optString("mime").ifBlank { "application/octet-stream" }
        val type = item.optString("type").ifBlank { if (mime.startsWith("image/")) "image" else "file" }
        MemohAttachment(
            id = id,
            name = name,
            mime = mime,
            type = type,
            size = item.optLong("size"),
            url = item.optString("url"),
            base64 = item.optString("base64"),
        )
    }
}

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
        attachments = decodeAttachments(obj.optJSONArray("attachments")),
        turnId = obj.optString("turn_id"),
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
            attachments = decodeAttachments(obj.optJSONArray("attachments")),
        )
    }
}
