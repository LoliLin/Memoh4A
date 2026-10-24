package com.newoether.agora.memoh

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest

/** Encrypted, origin-bound cookies. Retired identities may never write back to the store. */
internal class MemohCookieJar(
    private val prefs: SharedPreferences? = null,
    private val sealer: (String) -> String = MemohTokenStore::seal,
    private val unsealer: (String) -> String = MemohTokenStore::read,
    private val origin: HttpUrl = MemohEndpoints.CLOUD_ORIGIN.toHttpUrl(),
) : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()
    private val key = storageKey(origin)
    private var retired = false

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            if (retired || !sameOrigin(url) || cookies.isEmpty()) return
            for (cookie in cookies) {
                if (!belongsToOrigin(cookie) && cookie.expiresAt > System.currentTimeMillis()) continue
                this.cookies.removeAll {
                    it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                }
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
            }
            persistLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (!sameOrigin(url)) return emptyList()
        if (cookies.removeAll { it.expiresAt <= System.currentTimeMillis() } && !retired) persistLocked()
        cookies.filter { it.matches(url) }
    }

    fun hasSession(): Boolean = synchronized(lock) {
        cookies.any { it.expiresAt > System.currentTimeMillis() && belongsToOrigin(it) }
    }

    fun clear() = synchronized(lock) {
        cookies.clear()
        if (!retired) prefs?.edit()?.remove(key)?.commit()
    }

    /** Keep in-memory cookies for the final logout request, but reject all late Set-Cookie. */
    fun retire(clearPersisted: Boolean = false) = synchronized(lock) {
        if (retired) return
        retired = true
        if (clearPersisted) prefs?.edit()?.remove(key)?.commit()
    }

    private fun belongsToOrigin(cookie: Cookie): Boolean =
        cookie.matches(origin.newBuilder().encodedPath(cookie.path).build())

    private fun sameOrigin(url: HttpUrl): Boolean =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port

    private fun persistLocked() {
        val store = prefs ?: return
        if (retired) return
        val live = cookies.filter { it.expiresAt > System.currentTimeMillis() }
        if (live.isEmpty()) {
            store.edit().remove(key).commit()
            return
        }
        runCatching {
            check(store.edit().putString(key, sealer(live.joinToString("\n"))).commit()) {
                "Could not persist the session cookie"
            }
        }.onFailure {
            com.newoether.agora.util.DebugLog.w("MemohCookieJar", "Could not persist the sealed session cookie")
            store.edit().remove(key).commit()
        }
    }

    private fun restore() = synchronized(lock) {
        val store = prefs ?: return
        // The old unscoped key belonged to the official Cloud. Never import it into another origin.
        val legacy = origin == MemohEndpoints.CLOUD_ORIGIN.toHttpUrl() && !store.contains(key) && store.contains("cookies")
        val source = if (legacy) "cookies" else key
        val sealed = store.getString(source, "").orEmpty()
        if (sealed.isBlank()) return
        val plain = runCatching { unsealer(sealed) }.getOrNull()
        if (plain.isNullOrBlank()) {
            store.edit().remove(source).commit()
            return
        }
        cookies += plain.lineSequence().mapNotNull { Cookie.parse(origin, it) }
            .filter { it.expiresAt > System.currentTimeMillis() && belongsToOrigin(it) }.toList()
        if (legacy) {
            persistLocked()
            store.edit().remove("cookies").commit()
        }
    }

    companion object {
        internal fun storageKey(origin: HttpUrl): String {
            val authority = "${origin.scheme}://${origin.host}:${origin.port}"
            val digest = MessageDigest.getInstance("SHA-256").digest(authority.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "cookies.$digest"
        }
    }
}
