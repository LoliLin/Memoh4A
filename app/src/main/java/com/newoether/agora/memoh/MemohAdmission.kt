package com.newoether.agora.memoh

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Each outstanding invocation has its own deadline; navigation never cancels another chat's send. */
internal fun MemohViewModel.armAdmissionTimeout(invocation: String, draft: String) {
    admissionTimeoutJobs.remove(invocation)?.cancel()
    admissionTimeoutJobs[invocation] = navigationScope.launch {
        delay(ADMISSION_TIMEOUT_MS)
        if (admissionInvocations.containsKey(invocation)) {
            val mutation = admissionInvocations[invocation]?.optString("type") != "message"
            val message = if (mutation) {
                "The server has not acknowledged this edit within ${ADMISSION_TIMEOUT_MS / 1000} seconds. " +
                    "It may still have been applied; check the conversation before retrying."
            } else {
                "The server has not acknowledged this message within ${ADMISSION_TIMEOUT_MS / 1000} seconds. " +
                    "The draft was restored. It may still have been accepted; check the conversation before sending again."
            }
            failAdmission(invocation, message)
        }
    }
}

internal fun MemohViewModel.cancelAdmissionTimeout(invocation: String? = null) {
    if (invocation != null) admissionTimeoutJobs.remove(invocation)?.cancel()
    else {
        admissionTimeoutJobs.values.toList().forEach { it.cancel() }
        admissionTimeoutJobs.clear()
    }
}

/** Records the server turn_id that confirms the optimistic user turn. */
internal fun MemohViewModel.recordPendingUserTurnId(turnId: String, echoedText: String) {
    if (turnId.isBlank() || echoedText.isBlank() || pendingUserTurnId != null || pendingUserText != echoedText) return
    val matchingSend = sendStates.values.lastOrNull {
        it.belongsTo(bot?.id, session?.id) && it.accepted && it.serverRunId.isNotBlank() &&
            it.serverRunId == runId && it.text == echoedText
    } ?: return
    matchingSend.serverTurnId = turnId
    pendingUserTurnId = turnId
}
