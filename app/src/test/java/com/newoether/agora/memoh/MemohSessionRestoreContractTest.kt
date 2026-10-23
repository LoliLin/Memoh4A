package com.newoether.agora.memoh

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A restored Cloud cookie must be checked through the unscoped platform identity.
 *
 * Regression: [probeIdentity] used to call `account()`, which is a team-scoped business request.
 * With no team selected the server answered `TEAM_REQUIRED`, every launch treated a perfectly
 * valid cookie as expired, cleared it and demanded a fresh sign-in. The same failure left
 * `cookieAuthenticated` false, so teams never loaded and the socket never connected — the chat
 * showed "Reconnecting…" forever.
 */
@RunWith(RobolectricTestRunner::class)
class MemohSessionRestoreContractTest {

    private fun seedCookie(api: MemohApi, value: String) {
        val url = api.authUrl.toHttpUrl()
        api.scope.identity.cookies.saveFromResponse(
            url,
            listOf(
                okhttp3.Cookie.Builder().name("sid").value(value)
                    .hostOnlyDomain(url.host).path("/").build(),
            ),
        )
    }

    @Test
    fun `restore probes the platform identity and never the team-scoped gateway`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply { configure(server.gateway) }
            seedCookie(api, "restored-user")
            server.userId = "restored-user"

            assertTrue("a valid restored cookie must verify", api.probeIdentity())

            val paths = server.requests.map { it.path }
            assertTrue(
                "platform identity was not consulted: $paths",
                paths.contains("/api/v1/users/me"),
            )
            assertFalse(
                "the team-scoped business gateway must not be probed before a team exists: $paths",
                paths.any { it.startsWith("/api/memoh/") },
            )
            assertEquals(
                "a missing-team request was issued during restore",
                0,
                server.missingTeamErrors.get(),
            )
            assertTrue("restored session did not become a cookie session", api.usesSessionCookie)
        }
    }

    @Test
    fun `a rejected restore reports false without clearing offline`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply { configure(server.gateway) }
            seedCookie(api, "restored-user")
            server.handler = { exchange, _ -> server.reply(exchange, """{"message":"nope"}""", 401); true }

            assertFalse("a 401 must not verify", api.probeIdentity())
            assertTrue(
                "the caller, not the probe, owns clearing an explicitly rejected credential",
                api.hasSessionCookie(),
            )
        }
    }

    @Test
    fun `a server failure during restore keeps the cookie for the next launch`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply { configure(server.gateway) }
            seedCookie(api, "restored-user")
            server.handler = { exchange, _ -> server.reply(exchange, """{"message":"down"}""", 503); true }

            val outcome = runCatching { api.probeIdentity() }
            assertTrue("a 5xx is not an authentication verdict", outcome.isFailure)
            assertTrue(
                "being offline must not cost the user their session",
                api.hasSessionCookie(),
            )
        }
    }
}

