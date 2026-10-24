package com.newoether.agora.memoh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemohRecoveryTest {
    private suspend fun awaitState(predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }
    private fun withCloud(test: suspend (MemohViewModel, MemohCloudServer) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("memoh_native", 0)
        try {
            MemohCloudServer().use { server ->
                prefs.edit().clear().putString("server", server.gateway).commit()
                val vm = MemohViewModel(app)
                try { runBlocking { test(vm, server) } }
                finally { vm.logout(); prefs.edit().clear().commit() }
            }
        } finally { Dispatchers.resetMain() }
    }
    private suspend fun login(vm: MemohViewModel) {
        vm.login.email = "user@example.com"
        vm.login.code = "123456"
        vm.loginWithCode()
        awaitState { vm.signedIn && !vm.teams.busy && vm.bot != null && !vm.loadingSessions }
    }
    private fun page(start: Int, count: Int): String = JSONObject().put("items", JSONArray().apply {
        repeat(count) { put(JSONObject().put("id", "m${start + it}").put("role", "user").put("text", "message")) }
    }).toString()

    @Test
    fun `a short initial history does not offer older messages`() = withCloud { vm, server ->
        server.handler = { exchange, request ->
            if (request.path.endsWith("/messages")) { server.reply(exchange, page(1, 1)); true } else false
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        awaitState { vm.turns.size == 1 }
        assertFalse(vm.olderMessagesAvailable)
        vm.loadOlderMessages()
        assertEquals(1, server.requests.count { it.path.endsWith("/messages") })
    }

    @Test
    fun `a short older page closes pagination after preserving both pages`() = withCloud { vm, server ->
        server.handler = { exchange, request ->
            if (request.path.endsWith("/messages")) {
                server.reply(exchange, if (request.query.orEmpty().contains("before_message_id=")) page(0, 3)
                    else page(3, HISTORY_PAGE_SIZE))
                true
            } else false
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        awaitState { vm.turns.size == HISTORY_PAGE_SIZE }
        assertTrue(vm.olderMessagesAvailable)
        vm.loadOlderMessages()
        awaitState { !vm.loadingOlderMessages && vm.turns.size == HISTORY_PAGE_SIZE + 3 }
        assertEquals("m0", vm.turns.first().id)
        assertEquals("m52", vm.turns.last().id)
        assertFalse(vm.olderMessagesAvailable)
        val requests = server.requests.count { it.path.endsWith("/messages") }
        vm.loadOlderMessages()
        assertEquals(requests, server.requests.count { it.path.endsWith("/messages") })
    }

    @Test
    fun `switching conversations releases an old pagination spinner and rejects late history`() = withCloud { vm, server ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val older = AtomicInteger()
        server.handler = { exchange, request ->
            if (request.path.endsWith("/messages")) {
                val before = request.query.orEmpty().contains("before_message_id=")
                if (before && older.incrementAndGet() == 1) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    runCatching { server.reply(exchange, page(999, 1)) }
                    finished.countDown()
                } else server.reply(exchange, if (before) page(0, 3) else page(3, HISTORY_PAGE_SIZE))
                true
            } else false
        }
        try {
            login(vm)
            vm.selectSession(MemohSession("chat", "Chat", "agent"))
            awaitState { vm.turns.size == HISTORY_PAGE_SIZE }
            vm.loadOlderMessages()
            awaitState { entered.count == 0L }
            assertTrue(vm.loadingOlderMessages)
            vm.newChat()
            assertFalse(vm.loadingOlderMessages)
            vm.selectSession(MemohSession("chat", "Chat", "agent"))
            awaitState { vm.turns.size == HISTORY_PAGE_SIZE }
            vm.loadOlderMessages()
            awaitState { !vm.loadingOlderMessages && vm.turns.size == HISTORY_PAGE_SIZE + 3 }
            release.countDown()
            awaitState { finished.count == 0L }
            assertFalse(vm.turns.any { it.id == "m999" })
            assertFalse(vm.loadingOlderMessages)
        } finally { release.countDown() }
    }

    @Test
    fun `an offline cookie restore can retry without asking for another login code`() = withCloud { vm, server ->
        awaitState { vm.login.method == MemohLoginMethod.EmailCode }
        val url = vm.workspaceApi.authUrl.toHttpUrl()
        vm.workspaceApi.scope.identity.cookies.saveFromResponse(url,
            listOf(Cookie.Builder().name("sid").value("restored-user").hostOnlyDomain(url.host).path("/").build()))
        server.handler = { exchange, request ->
            if (request.path == "/api/v1/users/me") {
                server.reply(exchange, """{"message":"offline"}""", 503); true
            } else false
        }
        vm.resumeCookieSession()
        assertFalse(vm.signedIn)
        assertFalse(vm.restoringSession)
        assertTrue(vm.sessionRestoreFailed)
        assertTrue(vm.workspaceApi.hasSessionCookie())
        server.handler = null
        vm.retrySessionRestore()
        awaitState { vm.signedIn && !vm.restoringSession && vm.bot != null }
        assertFalse(vm.sessionRestoreFailed)
        assertTrue(server.requests.none { it.path.endsWith("/email-code/verify") })
    }

    @Test
    fun `a rejected send restores its attachments as well as its text`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        val attachments = listOf(MemohAttachment(id = "upload", name = "file.txt", base64 = "aGk=", size = 2))
        assertTrue(vm.send("keep this draft", attachments))
        val invocation = vm.admissionInvocations.keys.single()
        val rejected = JSONObject().put("type", "run_rejected").put("session_id", "chat")
            .put("invocation_id", invocation).put("message", "try later")
        MemohViewModel::class.java.getDeclaredMethod("receive", JSONObject::class.java)
            .apply { isAccessible = true }.invoke(vm, rejected)
        assertEquals("keep this draft", vm.restoreDraft)
        assertEquals(attachments, vm.restoreAttachments)
        assertNull(vm.pendingUserText)
        assertTrue(vm.admissionInvocations.isEmpty())
    }
}
