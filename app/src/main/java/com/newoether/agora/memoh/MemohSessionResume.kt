package com.newoether.agora.memoh

import kotlinx.coroutines.CancellationException

/**
 * Restores a Cloud session that outlived the previous process.
 *
 * Cloud authenticates with an HttpOnly cookie, which [MemohCookieJar] keeps sealed on disk. A
 * restored cookie proves nothing on its own — the server may have revoked it — so this asks the
 * unscoped platform identity before skipping the sign-in form.
 *
 * Only an explicit rejection discards the credential. A transport failure or a server error is
 * reported and the cookie is kept, so starting the app offline does not cost the user a login.
 *
 * Kept as an extension beside [MemohViewModel] to hold that file inside the 800-line source gate.
 */
internal suspend fun MemohViewModel.resumeCookieSession() {
    val captured = api.scope
    if (!api.hasSessionCookie()) {
        probeLoginMethod()
        return
    }
    val verified = try {
        api.probeIdentity()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        api.requireCurrent(captured)
        error = failure.message ?: "Could not restore the session. Try again when the server is available."
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
}
