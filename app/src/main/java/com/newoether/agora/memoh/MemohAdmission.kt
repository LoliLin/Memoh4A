package com.newoether.agora.memoh

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bounds first acknowledgement even while offline; timeout does not prove server rejection. */
internal fun MemohViewModel.armAdmissionTimeout(invocation: String, draft: String) {
    admissionTimeoutJob?.cancel()
    admissionTimeoutJob = navigationScope.launch {
        delay(ADMISSION_TIMEOUT_MS)
        if (admissionInvocations.remove(invocation) != null) {
            if (pendingUserText == draft) restorePendingDraft()
            error = "The server has not acknowledged this message within " +
                "${ADMISSION_TIMEOUT_MS / 1000} seconds. The draft was restored. " +
                "It may still have been accepted; check the conversation before sending again."
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
