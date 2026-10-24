package com.newoether.agora.memoh

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MemohCookiePersistenceTest {
    private val cloud = MemohEndpoints.CLOUD_ORIGIN.toHttpUrl()
    private fun prefs(): SharedPreferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("cookies-test", 0).also { it.edit().clear().commit() }
    // AndroidKeyStore is absent in Robolectric. The reversible codec verifies store ownership,
    // migration and sealing boundaries; production still uses the KeyStore AES-GCM envelope.
    private fun jar(store: SharedPreferences, origin: HttpUrl = cloud) = MemohCookieJar(
        prefs = store, origin = origin,
        sealer = { "SEALED1:" + it.reversed() },
        unsealer = { require(it.startsWith("SEALED1:")); it.removePrefix("SEALED1:").reversed() },
    )
    private fun cookie(origin: HttpUrl = cloud, value: String = "live-session-value", path: String = "/") =
        Cookie.Builder().name("session").value(value).hostOnlyDomain(origin.host).path(path)
            .expiresAt(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)).httpOnly().build()

    @Test
    fun `a stored session survives a new jar`() {
        val store = prefs()
        jar(store).saveFromResponse(cloud, listOf(cookie()))
        assertTrue(store.contains(MemohCookieJar.storageKey(cloud)))
        assertTrue(jar(store).hasSession())
        assertEquals(listOf("live-session-value"), jar(store).loadForRequest(cloud).map { it.value })
    }

    @Test
    fun `the stored cookie is sealed rather than plaintext`() {
        val store = prefs()
        jar(store).saveFromResponse(cloud, listOf(cookie()))
        val raw = store.getString(MemohCookieJar.storageKey(cloud), "").orEmpty()
        assertTrue(raw.startsWith("SEALED1:"))
        assertFalse(raw.contains("live-session-value"))
    }

    @Test
    fun `expired cookies are neither saved nor restored`() {
        val store = prefs()
        val expired = Cookie.Builder().name("session").value("stale").hostOnlyDomain(cloud.host)
            .path("/").expiresAt(System.currentTimeMillis() - 1000).build()
        jar(store).saveFromResponse(cloud, listOf(expired))
        assertFalse(jar(store).hasSession())
    }

    @Test
    fun `clearing a session also clears the persisted credential`() {
        val store = prefs()
        val current = jar(store)
        current.saveFromResponse(cloud, listOf(cookie()))
        current.clear()
        assertFalse(current.hasSession())
        assertFalse(jar(store).hasSession())
    }

    @Test
    fun `a corrupt envelope is discarded`() {
        val store = prefs()
        val key = MemohCookieJar.storageKey(cloud)
        store.edit().putString(key, "not-an-envelope").commit()
        assertFalse(jar(store).hasSession())
        assertFalse(store.contains(key))
    }

    @Test
    fun `origins on the same hostname but different ports never share cookies`() {
        val store = prefs()
        val first = "http://127.0.0.1:12001/".toHttpUrl()
        val second = "http://127.0.0.1:12002/".toHttpUrl()
        jar(store, first).saveFromResponse(first, listOf(cookie(first, "first")))
        assertFalse(jar(store, second).hasSession())
        jar(store, second).saveFromResponse(second, listOf(cookie(second, "second")))
        assertEquals("first", jar(store, first).loadForRequest(first).single().value)
        assertEquals("second", jar(store, second).loadForRequest(second).single().value)
        assertTrue(jar(store, first).loadForRequest(second).isEmpty())
    }

    @Test
    fun `legacy Cloud credentials migrate once without logging the user out`() {
        val store = prefs()
        val key = MemohCookieJar.storageKey(cloud)
        jar(store).saveFromResponse(cloud, listOf(cookie()))
        val sealed = store.getString(key, "")
        store.edit().remove(key).putString("cookies", sealed).commit()
        val other = "https://other.example/".toHttpUrl()
        assertFalse(jar(store, other).hasSession())
        assertTrue(store.contains("cookies"))
        assertTrue(jar(store).hasSession())
        assertTrue(store.contains(key))
        assertFalse(store.contains("cookies"))
    }

    @Test
    fun `retired logout cannot overwrite a newer login`() {
        val store = prefs()
        val old = jar(store)
        old.saveFromResponse(cloud, listOf(cookie(value = "old")))
        old.retire(clearPersisted = true)
        assertFalse(jar(store).hasSession())
        assertEquals("old", old.loadForRequest(cloud).single().value)
        val new = jar(store)
        new.saveFromResponse(cloud, listOf(cookie(value = "new")))
        old.saveFromResponse(cloud, listOf(cookie(value = "late-old")))
        old.clear()
        assertEquals("new", jar(store).loadForRequest(cloud).single().value)
    }

    @Test
    fun `an identity retired by reconfiguration cannot persist a late response`() {
        val store = prefs()
        val old = jar(store)
        old.saveFromResponse(cloud, listOf(cookie(value = "current")))
        old.retire()
        old.saveFromResponse(cloud, listOf(cookie(value = "stale")))
        assertEquals("current", jar(store).loadForRequest(cloud).single().value)
    }

    @Test
    fun `path scoped cookies survive restart and only match their path`() {
        val store = prefs()
        val auth = cloud.resolve("/api/v1/users/me")!!
        jar(store).saveFromResponse(auth, listOf(cookie(path = "/api/v1")))
        val restored = jar(store)
        assertTrue(restored.hasSession())
        assertEquals(1, restored.loadForRequest(auth).size)
        assertTrue(restored.loadForRequest(cloud.resolve("/other")!!).isEmpty())
    }
}
