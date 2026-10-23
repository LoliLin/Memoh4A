package com.newoether.agora.memoh

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Memoh Cloud authenticates with an HttpOnly session cookie rather than a bearer token.
 *
 * A process-lifetime jar alone forced a fresh sign-in after every app restart, so live cookies
 * are also written to [prefs] — but only through [MemohTokenStore]'s AndroidKeyStore AES-GCM
 * envelope, never as plaintext. A cookie the server has already expired is never persisted.
 *
 * [sealer]/[unsealer] exist so the persistence path is testable: AndroidKeyStore does not exist
 * under Robolectric, and silently skipping the cipher would leave the real code unverified.
 */
internal class MemohCookieJar(
    private val prefs: SharedPreferences? = null,
    private val sealer: (String) -> String = MemohTokenStore::seal,
    private val unsealer: (String) -> String = MemohTokenStore::read,
) : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        var changed = false
        synchronized(lock) {
            for (cookie in cookies) {
                this.cookies.removeAll { existing ->
                    existing.name == cookie.name && existing.domain == cookie.domain &&
                        existing.path == cookie.path
                }
                // A server-side logout or expiry arrives as an already-expired cookie.
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
                changed = true
            }
        }
        if (changed) persist()
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

    fun clear() {
        synchronized(lock) { cookies.clear() }
        prefs?.edit()?.remove(KEY)?.commit()
    }

    /**
     * Cookies are stored as one sealed blob. OkHttp's Cookie serializer covers the value,
     * domain, path and expiry, so a restored session behaves like the original one.
     */
    private fun persist() {
        val store = prefs ?: return
        val live = synchronized(lock) { cookies.filter { it.expiresAt > System.currentTimeMillis() } }
        if (live.isEmpty()) {
            store.edit().remove(KEY).commit()
            return
        }
        val blob = live.joinToString("\n") { it.toString() }
        // commit() rather than apply(): a login that is not yet on disk is a login the user
        // repeats, and the write is a few hundred bytes on the request path.
        runCatching { store.edit().putString(KEY, sealer(blob)).commit() }
            .onFailure { failure ->
                // Never keep a plaintext fallback: an unsealed session is a stolen session.
                android.util.Log.w("MemohCookieJar", "Could not seal the session cookie", failure)
                store.edit().remove(KEY).commit()
            }
    }

    private fun restore() {
        val store = prefs ?: return
        val sealed = store.getString(KEY, "").orEmpty()
        if (sealed.isBlank()) return
        val plain = runCatching { unsealer(sealed) }.getOrNull()
        if (plain.isNullOrBlank()) {
            store.edit().remove(KEY).commit()
            return
        }
        val now = System.currentTimeMillis()
        val parsed = plain.lineSequence()
            .mapNotNull { Cookie.parse(HttpUrl.Builder().scheme("https").host(HOST).build(), it) }
            .filter { it.expiresAt > now }
            .toList()
        synchronized(lock) { cookies.addAll(parsed) }
    }

    private companion object {
        const val KEY = "cookies"
        // Only used as the parse anchor; Cookie.parse reads the domain from the cookie itself.
        const val HOST = "app.memoh.net"
    }
}
