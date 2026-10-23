package com.newoether.agora.memoh

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-acknowledgement timeout for sent messages, kept next to the delta merge in
 * MemohLiveTranscript.kt to respect the ViewModel's 800-line source gate.
 *
 * A message that never receives run_accepted/run_rejected within [ADMISSION_TIMEOUT_MS]
 * is treated as undelivered: the optimistic turn is dropped and the draft is restored.
 */
internal fun MemohViewModel.armAdmissionTimeout(invocation: String, draft: String) {
    admissionTimeoutJob?.cancel()
    admissionTimeoutJob = navigationScope.launch {
        delay(ADMISSION_TIMEOUT_MS)
        if (admissionInvocations.remove(invocation) != null) {
            if (pendingUserText == draft) {
                restoreDraft = pendingUserText
                pendingUserText = null; pendingUserAttachments = emptyList()
            }
            error = "The server did not acknowledge your message within " +
                "${ADMISSION_TIMEOUT_MS / 1000} seconds. It was restored to the composer."
        }
    }
}

internal fun MemohViewModel.cancelAdmissionTimeout() {
    admissionTimeoutJob?.cancel()
    admissionTimeoutJob = null
}

/** Records the server turn_id that confirms the optimistic user turn. */
internal fun MemohViewModel.recordPendingUserTurnId(turnId: String) {
    if (pendingUserTurnId == null && pendingUserText != null) pendingUserTurnId = turnId
}
