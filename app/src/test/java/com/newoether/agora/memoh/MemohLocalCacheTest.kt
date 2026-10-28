package com.newoether.agora.memoh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemohLocalCacheTest {
    private fun prefs() = ApplicationProvider.getApplicationContext<Application>()
        .getSharedPreferences("memoh_cache_test", 0).also { it.edit().clear().commit() }

    private fun bot(id: String, title: String) = MemohBot(
        id = id, title = title, name = title, displayName = title,
        avatarUrl = "https://example.test/a.png", timezone = "UTC", status = "ready",
    )

    @Test fun botsRoundTripWithoutLosingVisibleFields() {
        val store = prefs()
        val cache = MemohLocalCache(store).apply { rememberIdentity("server#alice#team-1") }
        cache.storeBots(listOf(bot("b1", "First"), bot("b2", "Second")))

        val restored = MemohLocalCache(store).apply { rememberIdentity("server#alice#team-1") }.bots()
        assertEquals(listOf("b1", "b2"), restored.map { it.id })
        assertEquals("First", restored.first().title)
        assertEquals("https://example.test/a.png", restored.first().avatarUrl)
    }

    @Test fun differentAccountsAndTeamsCannotReadThePreviousRows() {
        val store = prefs()
        val alice = MemohLocalCache(store).apply { rememberIdentity("server#alice#team-1") }
        alice.storeBots(listOf(bot("b1", "Alice bot")))
        alice.storeSessions("b1", listOf(MemohSession("s1", "Alice chat")))
        alice.storeModels(JSONObject().put("models", JSONArray().put(JSONObject().put("id", "m1"))))

        val otherAccount = MemohLocalCache(store).apply { rememberIdentity("server#bob#team-1") }
        val otherTeam = MemohLocalCache(store).apply { rememberIdentity("server#alice#team-2") }
        for (other in listOf(otherAccount, otherTeam)) {
            assertTrue(other.bots().isEmpty())
            assertTrue(other.sessions("b1").isEmpty())
            assertNull(other.models())
        }
        assertEquals("Alice bot", alice.bots().single().title)
        assertEquals("m1", alice.models()!!.getJSONArray("models").getJSONObject(0).getString("id"))
    }

    @Test fun noRowsAreReadOrWrittenUntilIdentityIsVerifiedOrAfterLogout() {
        val store = prefs()
        // The previous release used server/team-only keys. They cannot identify an account.
        store.edit().putString("cache_identity", "server#team-1")
            .putString("cache_bots:server#team-1", "[{\"id\":\"old\"}]").commit()
        val cache = MemohLocalCache(store)
        cache.storeBots(listOf(bot("unverified", "Unverified")))
        assertTrue(cache.bots().isEmpty())
        cache.rememberIdentity("server#alice#team-1")
        assertTrue(cache.bots().isEmpty())
        cache.storeBots(listOf(bot("alice", "Alice")))
        cache.forgetIdentity()
        assertTrue(cache.bots().isEmpty())
        cache.rememberIdentity("server#alice#team-1")
        assertEquals("alice", cache.bots().single().id)
    }

    @Test fun sessionsAreScopedPerBot() {
        val cache = MemohLocalCache(prefs()).apply { rememberIdentity("server#alice#team-1") }
        cache.storeSessions("b1", listOf(MemohSession("s1", "One")))
        cache.storeSessions("b2", listOf(MemohSession("s2", "Two"), MemohSession("s3", "Three")))
        assertEquals(listOf("s1"), cache.sessions("b1").map { it.id })
        assertEquals(listOf("s2", "s3"), cache.sessions("b2").map { it.id })
        assertTrue(cache.sessions("").isEmpty())
    }

    @Test fun corruptJsonAndWrongPreferenceTypesFallBackToEmptyCache() {
        val store = prefs()
        val identity = "server#alice#team-1"
        store.edit().putInt("cache_bots:v2:$identity", 123)
            .putString("cache_models:v2:$identity", "[]not json")
            .putString("cache_sessions:b1:v2:$identity", "{not json").commit()
        val cache = MemohLocalCache(store).apply { rememberIdentity(identity) }
        assertTrue(cache.bots().isEmpty())
        assertTrue(cache.sessions("b1").isEmpty())
        assertNull(cache.models())
        cache.storeBots(listOf(bot("ok", "Recovered")))
        assertEquals("ok", cache.bots().single().id)
    }

    @Test fun rowsWithoutAnIdAreDropped() {
        val store = prefs()
        val identity = "server#alice#team-1"
        store.edit().putString("cache_bots:v2:$identity", """[{"title":"no id"},{"id":"ok","title":"kept"}]""").commit()
        val cache = MemohLocalCache(store).apply { rememberIdentity(identity) }
        assertEquals(listOf("ok"), cache.bots().map { it.id })
    }
}
