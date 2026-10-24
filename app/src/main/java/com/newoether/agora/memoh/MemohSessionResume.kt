package com.newoether.agora.memoh

import kotlinx.coroutines.CancellationException

/** Only an explicit rejection discards a saved cookie. Offline restoration remains retryable. */
internal suspend fun MemohViewModel.resumeCookieSession() {
    if (restoringSession) return
    val captured = api.scope
    if (!api.hasSessionCookie()) {
        sessionRestoreFailed = false
        probeLoginMethod()
        return
    }
    restoringSession = true
    sessionRestoreFailed = false
    try {
        val verified = try {
            api.probeIdentity()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            api.requireCurrent(captured)
            sessionRestoreFailed = true
            error = failure.message ?: "Could not restore the session. Retry when the server is available."
            return
        }
        api.requireCurrent(captured)
        if (verified) {
            signedIn = true
            error = ""
            teams.load()
        } else {
            api.clearSession()
            probeLoginMethod()
        }
    } finally {
        if (api.scope === captured) restoringSession = false
    }
}
