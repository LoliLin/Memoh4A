package com.newoether.agora.memoh

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bounds first acknowledgement even while offline; timeout does not prove server rejection. */
internal fun MemohViewModel.armAdmissionTimeout(invocation: String, draft: String) {
    admissionTimeoutJob?.cancel()
    admissionTimeoutJob = navigationScope.launch {
        delay(ADMISSION_TIMEOUT_MS)
        if (admissionInvocations.remove(invocation) != null) {
            // A mutation has no optimistic pending draft to restore; only plain sends do.
            if (pendingUserText != null && pendingUserText == draft) restorePendingDraft()
            error = if (pendingUserText != null || draft.isEmpty()) {
                "The server has not acknowledged this message within " +
                    "${ADMISSION_TIMEOUT_MS / 1000} seconds. The draft was restored. " +
                    "It may still have been accepted; check the conversation before sending again."
            } else {
                "The server has not acknowledged this edit within " +
                    "${ADMISSION_TIMEOUT_MS / 1000} seconds. " +
                    "It may still have been applied; check the conversation before retrying."
            }
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
