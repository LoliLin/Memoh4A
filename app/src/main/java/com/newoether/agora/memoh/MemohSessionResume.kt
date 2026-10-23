package com.newoether.agora.memoh

/**
 * Restores a Cloud session that outlived the previous process.
 *
 * Cloud authenticates with an HttpOnly cookie, which [MemohCookieJar] keeps sealed on disk. A
 * restored cookie proves nothing on its own — the server may have revoked it — so this asks
 * `/users/me` before skipping the sign-in form. An expired or rejected cookie is dropped so the
 * next start does not retry it.
 *
 * Kept as an extension beside [MemohViewModel] to hold that file inside the 800-line source gate.
 */
internal suspend fun MemohViewModel.resumeCookieSession() {
    if (!api.hasSessionCookie()) {
        probeLoginMethod()
        return
    }
    if (api.probeIdentity()) {
        signedIn = true
        error = ""
        teams.load()
    } else {
        api.clearSession()
        probeLoginMethod()
    }
}
