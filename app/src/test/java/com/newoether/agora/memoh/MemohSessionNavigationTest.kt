package com.newoether.agora.memoh

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemohSessionNavigationTest {
    private fun withNavigation(test: suspend (MemohViewModel, CountDownLatch, CountDownLatch, CountDownLatch) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("memoh_native", 0).edit().clear().commit()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val response = when {
                path == "/auth/login" -> """{"access_token":"test-token"}"""
                path == "/bots" -> """{"items":[{"id":"bot","name":"Bot"}]}"""
                path.endsWith("/sessions/slow") -> {
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    """{"id":"slow","title":"Slow","bot_agent_id":"slow-agent"}"""
                }
                path.endsWith("/sessions/fast") -> """{"id":"fast","title":"Fast","bot_agent_id":"server-agent","runtime_type":"codex"}"""
                else -> """{"items":[]}"""
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            if (path.endsWith("/sessions/slow")) finished.countDown()
        }
        server.start()
        val vm = MemohViewModel(application)
        try {
            runBlocking {
                // Navigation tests start after authentication; token encryption belongs to AndroidKeyStore.
                @Suppress("UNCHECKED_CAST")
                val signedIn = MemohViewModel::class.java.getDeclaredField("signedIn\$delegate")
                    .apply { isAccessible = true }.get(vm) as androidx.compose.runtime.MutableState<Boolean>
                signedIn.value = true
                vm.workspaceApi.configure("http://127.0.0.1:${server.address.port}")
                vm.selectBot(MemohBot("bot", "Bot"))
                awaitState { !vm.loadingSessions && !vm.agentController.loading }
                test(vm, entered, release, finished)
            }
        } finally {
            release.countDown()
            vm.logout()
            server.stop(0)
            executor.shutdownNow()
            application.getSharedPreferences("memoh_native", 0).edit().clear().commit()
            Dispatchers.resetMain()
        }
    }

    private suspend fun awaitState(predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }

    @Test
    fun `send rejects attachment count file and draft limits without consuming the draft`() = withNavigation { vm, _, _, _ ->
        val revision = vm.attachmentDraftRevision
        val invalidDrafts = listOf(
            List(11) { MemohAttachment(size = 0) },
            listOf(MemohAttachment(size = MAX_ATTACHMENT_BYTES + 1)),
            listOf(MemohAttachment(size = MAX_ATTACHMENT_BYTES),
                MemohAttachment(size = MAX_ATTACHMENT_BYTES), MemohAttachment(size = 1)),
        )
        for (attachments in invalidDrafts) {
            assertTrue(!vm.send("keep this draft", attachments))
            assertTrue(vm.error.isNotBlank())
            assertNull(vm.pendingUserText)
            assertTrue(vm.pendingUserAttachments.isEmpty())
            assertEquals(revision, vm.attachmentDraftRevision)
        }
    }

    @Test
    fun `latest navigation wins and keeps server Agent metadata`() = withNavigation { vm, entered, release, finished ->
        val slow = vm.openSessionById("slow")
        awaitState { entered.count == 0L }
        vm.openSessionById("fast")
        awaitState { vm.session?.id == "fast" }
        release.countDown()
        awaitState { finished.count == 0L }
        withTimeout(10_000) { slow?.join() }
        assertEquals("fast", vm.session?.id)
        assertEquals("server-agent", vm.agentController.selectedAgentId)
        assertEquals("codex", vm.session?.runtimeType)
    }

    @Test
    fun `starting a new chat rejects an outstanding detail response`() = withNavigation { vm, entered, release, finished ->
        val slow = vm.openSessionById("slow")
        awaitState { entered.count == 0L }
        vm.newChat()
        release.countDown()
        awaitState { finished.count == 0L }
        withTimeout(10_000) { slow?.join() }
        assertNull(vm.session)
    }

    @Test
    fun `switching bots rejects an outstanding detail response`() = withNavigation { vm, entered, release, finished ->
        val slow = vm.openSessionById("slow")
        awaitState { entered.count == 0L }
        vm.selectBot(MemohBot("other-bot", "Other"))
        release.countDown()
        awaitState { finished.count == 0L }
        withTimeout(10_000) { slow?.join() }
        assertEquals("other-bot", vm.bot?.id)
        assertNull(vm.session)
    }

    @Test
    fun `logout rejects an outstanding detail response`() = withNavigation { vm, entered, release, finished ->
        val slow = vm.openSessionById("slow")
        awaitState { entered.count == 0L }
        vm.logout()
        release.countDown()
        awaitState { finished.count == 0L }
        withTimeout(10_000) { slow?.join() }
        assertNull(vm.session)
        assertNull(vm.bot)
    }

    @Test
    fun `attachment ownership changes when leaving and returning to the same chat`() = withNavigation { vm, _, _, _ ->
        vm.openSessionById("fast")?.join()
        val owner = vm.attachmentDraftOwner
        val session = vm.session
        assertNotNull(owner)
        vm.newChat()
        vm.selectSession(session)
        assertEquals("fast", vm.session?.id)
        assertNotEquals(owner, vm.attachmentDraftOwner)

        vm.newChat()
        val newChatOwner = vm.attachmentDraftOwner
        vm.newChat()
        assertNotEquals(newChatOwner, vm.attachmentDraftOwner)
    }

    @Test
    fun `attachment ownership changes when leaving and returning to the same bot`() = withNavigation { vm, _, _, _ ->
        val bot = vm.bot!!
        val owner = vm.attachmentDraftOwner
        vm.selectBot(MemohBot("other-bot", "Other"))
        vm.selectBot(bot)
        assertNotEquals(owner, vm.attachmentDraftOwner)
    }

    @Test
    fun `attachment ownership does not survive logout and a new login`() = withNavigation { vm, _, _, _ ->
        val bot = vm.bot!!
        val owner = vm.attachmentDraftOwner
        vm.logout()
        assertNull(vm.attachmentDraftOwner)
        @Suppress("UNCHECKED_CAST")
        val signedIn = MemohViewModel::class.java.getDeclaredField("signedIn\$delegate")
            .apply { isAccessible = true }.get(vm) as androidx.compose.runtime.MutableState<Boolean>
        signedIn.value = true
        vm.selectBot(bot)
        assertNotEquals(owner, vm.attachmentDraftOwner)
    }

    @Test
    fun `sending invalidates an outstanding attachment selection`() = withNavigation { vm, _, _, _ ->
        vm.openSessionById("fast")?.join()
        val owner = vm.attachmentDraftOwner
        assertTrue(vm.send("hello"))
        assertNotEquals(owner, vm.attachmentDraftOwner)
    }

    @Test
    fun `cancelled navigation leaves the selected conversation intact`() = withNavigation { vm, entered, release, finished ->
        vm.openSessionById("fast")?.join()
        val slow = vm.openSessionById("slow")
        awaitState { entered.count == 0L }
        slow?.cancel()
        release.countDown()
        awaitState { finished.count == 0L }
        withTimeout(10_000) { slow?.join() }
        assertEquals("fast", vm.session?.id)
    }
}
