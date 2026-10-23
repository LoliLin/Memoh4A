package com.newoether.agora.memoh

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/**
 * The Cloud session is an HttpOnly cookie, so it must survive a process restart without ever
 * being written as plaintext. These cover the jar in isolation: a fresh jar over the same
 * preferences must see the previous session, and nothing readable may reach the store.
 */
@RunWith(RobolectricTestRunner::class)
class MemohCookiePersistenceTest {
    private val https = HttpUrl.Builder().scheme("https").host("app.memoh.net").build()

    private fun prefs(): SharedPreferences =
        RuntimeEnvironment.getApplication().getSharedPreferences("cookies-test", 0).also { it.edit().clear().commit() }

    /**
     * AndroidKeyStore does not exist under Robolectric, so the cipher is injected. This stand-in
     * has to behave like the real envelope in the two ways that matter: the output must not carry
     * the plaintext, and unsealing anything it did not produce must fail rather than echo input.
     */
    private fun sealingJar(store: SharedPreferences) = MemohCookieJar(
        prefs = store,
        sealer = { plain ->
            // Reversible scrambling plus a marker, so the stored blob never contains the value.
            val marker = "SEALED1:"
            marker + plain.reversed().toByteArray().joinToString("") { "%02x".format(it) }
        },
        unsealer = { sealed ->
            require(sealed.startsWith("SEALED1:")) { "Not a sealed envelope" }
            sealed.removePrefix("SEALED1:").chunked(2).map { it.toInt(16).toByte() }
                .toByteArray().reversed().toByteArray().decodeToString()
        },
    )

    private fun sessionCookie(ttlHours: Long = 24): Cookie = Cookie.Builder()
        .name("session")
        .value("live-session-value")
        .domain("app.memoh.net")
        .path("/")
        .expiresAt(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(ttlHours))
        .httpOnly()
        .build()

    @Test
    fun `a stored session survives a new jar`() {
        val store = prefs()
        sealingJar(store).saveFromResponse(https, listOf(sessionCookie()))
        assertTrue("cookie must be written to the store", store.contains("cookies"))

        val restored = sealingJar(store)
        assertTrue("restored jar must report a live session", restored.hasSession())
        assertEquals(
            listOf("live-session-value"),
            restored.loadForRequest(https).map { it.value },
        )
    }

    @Test
    fun `the stored cookie is sealed rather than plaintext`() {
        val store = prefs()
        sealingJar(store).saveFromResponse(https, listOf(sessionCookie()))

        val raw = store.getString("cookies", "").orEmpty()
        assertTrue(raw.isNotEmpty())
        assertNotEquals("live-session-value", raw)
        assertFalse("the session value must not be readable on disk", raw.contains("live-session-value"))
    }

    @Test
    fun `an expired cookie is neither saved nor restored`() {
        val store = prefs()
        val expired = Cookie.Builder()
            .name("session").value("stale").domain("app.memoh.net").path("/")
            .expiresAt(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
            .build()

        sealingJar(store).saveFromResponse(https, listOf(expired)).also { RuntimeEnvironment.getApplication().getSharedPreferences("cookies-test", 0).edit().commit() }
        assertFalse("an already-expired cookie must not create a session", sealingJar(store).hasSession())
    }

    @Test
    fun `signing out clears the stored session`() {
        val store = prefs()
        val jar = sealingJar(store)
        jar.saveFromResponse(https, listOf(sessionCookie()))
        assertTrue(jar.hasSession())

        jar.clear()
        assertFalse(jar.hasSession())
        assertFalse("a new process must not resume a cleared session", sealingJar(store).hasSession())
        assertFalse(store.contains("cookies"))
    }

    @Test
    fun `a corrupt envelope is dropped instead of crashing`() {
        val store = prefs()
        store.edit().putString("cookies", "not-a-sealed-envelope").commit()

        val jar = sealingJar(store)
        assertFalse(jar.hasSession())
        assertFalse("an unreadable envelope must be discarded", store.contains("cookies"))
    }
}
