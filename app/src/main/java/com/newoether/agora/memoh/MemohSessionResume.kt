package com.newoether.agora.memoh

import kotlinx.coroutines.CancellationException

/** Only an explicit rejection discards a saved cookie. Offline restoration remains retryable. */
internal suspend fun MemohViewModel.resumeCookieSession() {
    if (restoringSession) return
    val captured = api.scope
    if (!api.hasSessionCookie()) {
        sessionRestoreFailed = false
        // Nothing to restore: this is a first run, so the sign-in form is the right surface.
        sessionOnDisk = false
        probeLoginMethod()
        return
    }
    val restoreGeneration = ++sessionRestoreGeneration
    restoringSession = true
    sessionRestoreFailed = false
    try {
        val verified = try {
            api.probeIdentity()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            api.requireCurrent(captured)
            // Offline, not signed out: keep the splash and its retry rather than falling back
            // to a form whose fields the user would have to fill in again.
            sessionRestoreFailed = true
            sessionOnDisk = false
            error = failure.message ?: "Could not restore the session. Retry when the server is available."
            return
        }
        api.requireCurrent(captured)
        if (verified) {
            signedIn = true
            sessionOnDisk = false
            error = ""
            teams.load()
        } else {
            // The server explicitly rejected the cookie, so the form is next.
            api.clearSession()
            sessionOnDisk = false
            probeLoginMethod()
        }
    } finally {
        // Team selection can rebind the API scope during a valid restore; only a newer attempt or logout owns this flag now.
        if (restoreGeneration == sessionRestoreGeneration) restoringSession = false
        // Every branch above decided whether this launch is a return visit; the splash must
        // never outlive the check that justified it.
        if (restoreGeneration == sessionRestoreGeneration) sessionOnDisk = false
    }
}
