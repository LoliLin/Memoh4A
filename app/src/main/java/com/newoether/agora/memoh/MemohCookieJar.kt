package com.newoether.agora.memoh

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Memoh Cloud authenticates with an HttpOnly session cookie, not a bearer token.
 *
 * Cookies are kept in memory only: the session is short-lived, it dies with the process, and
 * persisting it would put a live credential on disk outside the AndroidKeyStore envelope that
 * already protects password-server tokens.
 */
internal class MemohCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            for (cookie in cookies) {
                this.cookies.removeAll { existing ->
                    existing.name == cookie.name && existing.domain == cookie.domain &&
                        existing.path == cookie.path
                }
                // A server-side logout or expiry arrives as an already-expired cookie.
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val expired = cookies.filter { it.expiresAt <= now }
            if (expired.isNotEmpty()) cookies.removeAll(expired.toSet())
            return cookies.filter { it.matches(url) }
        }
    }

    /** True once the server has issued any live cookie for this jar. */
    fun hasSession(): Boolean = synchronized(lock) {
        cookies.any { it.expiresAt > System.currentTimeMillis() }
    }

    fun clear() = synchronized(lock) { cookies.clear() }
}
