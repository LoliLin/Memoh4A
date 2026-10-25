package com.newoether.agora.memoh

/** Admission belongs to the bot/conversation that sent it, independently of the visible chat. */
internal data class MemohSendState(
    val botId: String,
    var sessionId: String?,
    val text: String?,
    val attachments: List<MemohAttachment> = emptyList(),
    val agentId: String = "",
    val navigationRevision: Int = -1,
    var accepted: Boolean = false,
    var failure: String = "",
    var draftDelivered: Boolean = false,
) {
    fun belongsTo(botId: String?, sessionId: String?): Boolean =
        this.botId == botId && this.sessionId == sessionId
}
