package com.newoether.agora.memoh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The offline cache exists so a cold start paints the last lists before the network answers.
 * It must never cross an identity or team boundary, and it must never be required to be
 * complete: a missing or corrupt entry simply means "nothing cached".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemohLocalCacheTest {
    private fun prefs() = ApplicationProvider.getApplicationContext<Application>()
        .getSharedPreferences("memoh_cache_test", 0).also { it.edit().clear().commit() }

    private fun bot(id: String, title: String) = MemohBot(
        id = id, title = title, name = title, displayName = title,
        avatarUrl = "https://example.test/a.png", timezone = "UTC", status = "ready",
    )

    @Test fun botsRoundTripWithoutLosingTheFieldsTheListDraws() {
        // One backing store for both instances: prefs() clears, so calling it twice would
        // erase what the first cache just wrote.
        val store = prefs()
        val cache = MemohLocalCache(store)
        cache.rememberIdentity("https://a#team-1")
        cache.storeBots(listOf(bot("b1", "First"), bot("b2", "Second")))

        val restored = MemohLocalCache(store).apply { rememberIdentity("https://a#team-1") }.bots()
        assertEquals(listOf("b1", "b2"), restored.map { it.id })
        assertEquals("First", restored.first().title)
        assertEquals("https://example.test/a.png", restored.first().avatarUrl)
    }

    @Test fun aDifferentTeamCannotReadThePreviousTeamsRows() {
        val cache = MemohLocalCache(prefs())
        cache.rememberIdentity("https://a#team-1")
        cache.storeBots(listOf(bot("b1", "Team one bot")))
        cache.storeSessions("b1", listOf(MemohSession("s1", "Team one chat")))

        // Same server, another team: nothing from team-1 may leak into the first frame.
        val other = MemohLocalCache(prefs()).apply { rememberIdentity("https://a#team-2") }
        assertTrue(other.bots().isEmpty())
        assertTrue(other.sessions("b1").isEmpty())
    }

    @Test fun sessionsAreScopedPerBot() {
        val cache = MemohLocalCache(prefs())
        cache.rememberIdentity("https://a#team-1")
        cache.storeSessions("b1", listOf(MemohSession("s1", "One")))
        cache.storeSessions("b2", listOf(MemohSession("s2", "Two"), MemohSession("s3", "Three")))

        assertEquals(listOf("s1"), cache.sessions("b1").map { it.id })
        assertEquals(listOf("s2", "s3"), cache.sessions("b2").map { it.id })
        assertTrue(cache.sessions("").isEmpty())
    }

    @Test fun corruptOrEmptyEntriesReadAsNothingRatherThanThrowing() {
        val prefs = prefs()
        prefs.edit().putString("cache_bots:", "{not json").apply()
        val cache = MemohLocalCache(prefs)
        cache.rememberIdentity("")
        assertTrue(cache.bots().isEmpty())
    }

    @Test fun theModelCatalogIsScopedToTheTeamAndSurvivesAParseFailure() {
        val cache = MemohLocalCache(prefs())
        cache.rememberIdentity("https://a#team-1")
        cache.storeModels(
            org.json.JSONObject().put("models", org.json.JSONArray().put(org.json.JSONObject().put("id", "m1"))),
        )
        assertEquals("m1", cache.models()!!.getJSONArray("models").getJSONObject(0).getString("id"))

        val other = MemohLocalCache(prefs()).apply { rememberIdentity("https://a#team-2") }
        assertNull(other.models())

        val broken = prefs().edit().putString("cache_models:", "[]not json").commit().let { MemohLocalCache(prefs()) }
        broken.rememberIdentity("")
        assertNull(broken.models())
    }

    @Test fun rowsWithoutAnIdAreDropped() {
        val prefs = prefs()
        prefs.edit().putString("cache_bots:", """[{"title":"no id"},{"id":"ok","title":"kept"}]""").apply()
        val cache = MemohLocalCache(prefs)
        assertEquals(listOf("ok"), cache.bots().map { it.id })
    }
}
