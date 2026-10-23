package com.newoether.agora.memoh

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemohCloudIdentityTest {
    private suspend fun awaitState(predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }
    private suspend fun authenticated(server: MemohCloudServer): MemohApi = MemohApi().apply {
        configure(server.gateway)
        verifyEmailCode("user@example.com", "123456")
    }

    @Test
    fun `late verification cannot install old cookies into a replacement identity on the same origin`() = runBlocking {
        MemohCloudServer().use { server ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.handler = { exchange, request ->
                if (request.path.endsWith("/email-code/verify") && request.body.contains("old-code")) {
                    entered.countDown(); release.await(5, TimeUnit.SECONDS)
                    exchange.responseHeaders.add("Set-Cookie", "sid=old-user; Path=/; HttpOnly")
                    runCatching { server.reply(exchange, "{}") }
                    true
                } else false
            }
            val api = MemohApi().apply { configure(server.gateway) }
            try {
                val old = async(Dispatchers.IO) { runCatching { api.verifyEmailCode("old@example.com", "old-code") } }
                awaitState { entered.count == 0L }
                api.configure(server.gateway)
                server.userId = "new-user"
                api.verifyEmailCode("new@example.com", "new-code")
                api.selectTeam("b")
                release.countDown()
                assertTrue(withTimeout(10_000) { old.await() }.isFailure)
                api.bots()
                val last = server.requests.last { it.path == "/api/memoh/bots" }
                assertEquals("b", last.team)
                assertTrue(last.cookie!!.contains("sid=new-user"))
                assertFalse(last.cookie.contains("old-user"))
            } finally { release.countDown(); api.clearIdentity() }
        }
    }

    @Test
    fun `ticket and handshake carry captured team while platform discovery remains unscoped`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = authenticated(server)
            try {
                val blocked = runCatching { api.bots() }.exceptionOrNull()
                assertTrue(blocked is IllegalStateException)
                assertEquals(0, server.missingTeamErrors.get())
                api.platformProfile(); api.teams()
                api.selectTeam("b")
                val failed = CountDownLatch(1)
                val socket = api.socket("shared", object : WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failed.countDown() }
                })
                try {
                    awaitState { failed.count == 0L }
                    val ticket = server.requests.single { it.path == "/api/v1/ws-tickets" }
                    assertEquals("b", ticket.team)
                    assertEquals("POST", ticket.method)
                    val handshake = server.requests.single { it.path.endsWith("/web/ws") }
                    assertEquals("b", handshake.team)
                    assertTrue(handshake.query!!.contains("team_id=b"))
                    assertTrue(handshake.query.contains("ticket=ticket-b"))
                    assertTrue(server.requests.filter { it.path == "/api/v1/users/me" || it.path == "/api/v1/teams" }.all { it.team == null })
                    assertEquals(0, server.missingTeamErrors.get())
                } finally { socket.cancel() }
            } finally { api.clearIdentity() }
        }
    }

    @Test
    fun `old delayed probe cannot reset successful login and duplicate submission stays single`() = runBlocking {
        MemohCloudServer().use { probeServer -> MemohCloudServer().use { loginServer ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            probeServer.handler = { exchange, request ->
                if (request.path.endsWith("/auth/config")) {
                    entered.countDown(); release.await(5, TimeUnit.SECONDS)
                    runCatching { probeServer.reply(exchange, """{"login_method":"password"}""") }
                    finished.countDown(); true
                } else false
            }
            val api = MemohApi()
            val form = MemohLoginForm(probeServer.gateway)
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cloud-auth-test", 0)
            prefs.edit().clear().commit()
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
            var signedIn = 0
            var error = ""
            val signIn = MemohSignIn(api, prefs, scope, form, { error = it }, { signedIn++ })
            try {
                signIn.probe(probeServer.gateway)
                awaitState { entered.count == 0L }
                form.server = loginServer.gateway
                form.email = "user@example.com"
                signIn.signInWithCode("123456")
                assertTrue(form.signingIn)
                signIn.signInWithCode("123456")
                awaitState { signedIn == 1 && !form.signingIn }
                release.countDown()
                awaitState { finished.count == 0L }
                assertEquals(MemohLoginMethod.EmailCode, form.method)
                assertEquals(loginServer.origin + "/api/v1", api.authUrl)
                assertTrue(api.usesSessionCookie)
                assertEquals(1, loginServer.requests.count { it.path.endsWith("/email-code/verify") })
                assertEquals("", error)
                assertEquals(loginServer.gateway, prefs.getString("server", ""))
            } finally { release.countDown(); scope.cancel(); api.clearIdentity(); prefs.edit().clear().commit() }
        } }
    }

    @Test
    fun `dialog API bound to an old team refuses new requests after switching away and back`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = authenticated(server)
            try {
                api.selectTeam("a")
                val oldDialog = MemohApi(api)
                api.selectTeam("b")
                api.selectTeam("a")
                val before = server.requests.size
                assertTrue(runCatching { oldDialog.deleteEntry("shared", "/data/secret") }.isFailure)
                assertTrue(runCatching { oldDialog.startDeviceLogin("shared", "agent") }.isFailure)
                assertEquals(before, server.requests.size)
                assertEquals("a", api.bots().single().title)
            } finally { api.clearIdentity() }
        }
    }

    @Test
    fun `remembered team belongs to a platform identity and revoked membership falls back`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = authenticated(server)
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cloud-team-selection-test", 0)
            prefs.edit().clear().commit()
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
            var error = ""
            val teams = MemohTeamController(api, prefs, scope, {}, { error = it })
            try {
                teams.load(); awaitState { !teams.busy }
                teams.select("b")
                teams.reset()
                api.configure(server.gateway); api.verifyEmailCode("user@example.com", "code")
                teams.load(); awaitState { !teams.busy }
                assertEquals("b", teams.selectedId)
                teams.reset()
                api.configure(server.gateway); server.userId = "other-user"
                api.verifyEmailCode("other@example.com", "code")
                teams.load(); awaitState { !teams.busy }
                assertEquals("a", teams.selectedId)
                teams.select("b")
                teams.reset()
                api.configure(server.gateway); api.verifyEmailCode("other@example.com", "code")
                server.memberships = """{"teams":[{"team":{"team_id":"a","name":"Alpha"},"role":"TEAM_ROLE_MEMBER"}]}"""
                teams.load(); awaitState { !teams.busy }
                assertEquals("a", teams.selectedId)
                assertEquals("", error)
            } finally { scope.cancel(); api.clearIdentity(); prefs.edit().clear().commit() }
        }
    }

    @Test
    fun `sending and verifying email code retain the same challenge session`() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, request ->
                when {
                    request.path.endsWith("/email-code/send") -> {
                        exchange.responseHeaders.add("Set-Cookie", "challenge=pending; Path=/; HttpOnly")
                        server.reply(exchange, "{}"); true
                    }
                    request.path.endsWith("/email-code/verify") && request.cookie?.contains("challenge=pending") != true -> {
                        server.reply(exchange, """{"message":"Missing challenge"}""", 401); true
                    }
                    else -> false
                }
            }
            val api = MemohApi()
            val form = MemohLoginForm(server.gateway).apply { email = "user@example.com" }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cloud-auth-challenge-test", 0)
            var signedIn = false
            var error = ""
            val signIn = MemohSignIn(api, prefs, scope, form, { error = it }, { signedIn = true })
            try {
                signIn.sendCode(form.email)
                awaitState { form.codeSent && !form.sendingCode }
                signIn.signInWithCode("code")
                awaitState { !form.signingIn }
                assertTrue(signedIn)
                assertEquals("", error)
                assertTrue(server.requests.single { it.path.endsWith("/email-code/verify") }.cookie!!.contains("challenge=pending"))
            } finally { scope.cancel(); api.clearIdentity(); prefs.edit().clear().commit() }
        }
    }

    @Test
    fun `an edge cookie and HTTP 200 verification do not prove an authenticated identity`() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, request ->
                when {
                    request.path.endsWith("/email-code/verify") -> {
                        exchange.responseHeaders.add("Set-Cookie", "edge=challenge; Path=/; HttpOnly")
                        server.reply(exchange, "{}"); true
                    }
                    request.path == "/api/v1/users/me" -> {
                        server.reply(exchange, """{"message":"Unauthenticated"}""", 401); true
                    }
                    else -> false
                }
            }
            val api = MemohApi().apply { configure(server.gateway) }
            try {
                val failure = runCatching { api.verifyEmailCode("user@example.com", "code") }.exceptionOrNull()
                assertTrue(failure is MemohHttpException)
                assertFalse(api.usesSessionCookie)
                assertTrue(server.requests.none { it.path == "/api/v1/teams" || it.path.startsWith("/api/memoh/") })
            } finally { api.clearIdentity() }
        }
    }

    @Test
    fun `failed login always clears busy and permits retry`() = runBlocking {
        MemohCloudServer().use { server ->
            var fail = true
            server.handler = { exchange, request ->
                if (fail && request.path.endsWith("/email-code/verify")) {
                    server.reply(exchange, """{"message":"Invalid code"}""", 401); true
                } else false
            }
            val api = MemohApi()
            val form = MemohLoginForm(server.gateway).apply { email = "user@example.com" }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cloud-auth-retry-test", 0)
            var error = ""
            var signedIn = false
            val signIn = MemohSignIn(api, prefs, scope, form, { error = it }, { signedIn = true })
            try {
                signIn.signInWithCode("wrong")
                awaitState { !form.signingIn }
                assertFalse(signedIn)
                assertEquals("Invalid code", error)
                fail = false
                signIn.signInWithCode("correct")
                awaitState { signedIn && !form.signingIn }
                assertEquals("", error)
            } finally { scope.cancel(); api.clearIdentity(); prefs.edit().clear().commit() }
        }
    }
}
