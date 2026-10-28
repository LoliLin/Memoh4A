package com.newoether.agora.memoh

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the real launcher and Cloud login with populated cache and delayed server responses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemohCachedStartupTest {
    private lateinit var server: MemohCloudServer
    private val releaseBots = CountDownLatch(1)
    private val releaseSessions = CountDownLatch(1)

    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            server = MemohCloudServer()
            val app = RuntimeEnvironment.getApplication()
            val prefs = app.getSharedPreferences("memoh_native", 0)
            prefs.edit().clear().putString("server", server.gateway).commit()
            val auth = MemohEndpoints.fromInput(server.gateway).auth
            val identity = JSONArray().put(auth).put(server.userId).put("a").toString()
            val cache = MemohLocalCache(prefs).apply { rememberIdentity(identity) }
            cache.storeBots(listOf(MemohBot("shared", "Cached bot")))
            cache.storeSessions("shared", listOf(MemohSession("stale", "Deleted on server")))
            server.handler = { exchange, request ->
                when (request.path) {
                    "/api/memoh/bots" -> {
                        releaseBots.await(15, TimeUnit.SECONDS)
                        server.reply(exchange, """{"items":[{"id":"shared","name":"Current bot"}]}""")
                        true
                    }
                    "/api/memoh/bots/shared/sessions" -> {
                        releaseSessions.await(15, TimeUnit.SECONDS)
                        server.reply(exchange, """{"items":[{"id":"current","title":"Current chat"}]}""")
                        true
                    }
                    else -> false
                }
            }
        }

        override fun after() {
            releaseBots.countDown()
            releaseSessions.countDown()
            server.close()
            RuntimeEnvironment.getApplication().getSharedPreferences("memoh_native", 0).edit().clear().commit()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MemohActivity>()

    /** Polls a real-time condition while keeping the Compose/test looper idle. */
    private fun awaitState(timeoutMs: Long = 10_000, describe: () -> String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(50)
        }
        compose.waitForIdle()
        if (!condition()) throw AssertionError("Timed out after ${timeoutMs}ms: ${describe()}")
    }

    @Test fun cachedStartupWaitsForTheServerBeforeSelectingAndReplacesStaleSessions() {
        val vm = ViewModelProvider(compose.activity)[MemohViewModel::class.java]
        compose.runOnUiThread {
            vm.login.email = "user@example.com"
            vm.login.code = "123456"
            vm.loginWithCode()
        }
        try {
            awaitState(describe = {
                "signedIn=${vm.signedIn} error='${vm.error}' teams=${vm.teams.items.map { it.id }} " +
                    "selected=${vm.teams.selectedId} bots=${vm.bots.map { it.title }}"
            }) { vm.teams.selectedId == "a" && vm.bots.any { it.title == "Cached bot" } }
            assertNull("a cached Bot must not start a socket before verification", vm.bot)
            compose.onRoot().assertIsDisplayed()
            releaseBots.countDown()
            awaitState(describe = {
                "bot=${vm.bot?.id} sessions=${vm.sessions.map { it.id }}"
            }) { vm.bot?.id == "shared" && vm.sessions.any { it.id == "stale" } }
            releaseSessions.countDown()
            awaitState(describe = {
                "loading=${vm.loadingSessions} sessions=${vm.sessions.map { it.id }}"
            }) { !vm.loadingSessions && vm.sessions.any { it.id == "current" } }
            assertEquals(listOf("current"), vm.sessions.map { it.id })
            compose.onRoot().assertIsDisplayed()
        } finally {
            releaseBots.countDown()
            releaseSessions.countDown()
        }
    }
}
