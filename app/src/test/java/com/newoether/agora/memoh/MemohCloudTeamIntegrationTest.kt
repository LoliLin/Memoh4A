package com.newoether.agora.memoh

import android.net.Uri
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemohCloudTeamIntegrationTest {
    private suspend fun awaitState(predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }
    private fun withCloud(test: suspend (MemohViewModel, MemohCloudServer) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("memoh_native", 0)
        MemohCloudServer().use { server ->
            prefs.edit().clear().putString("server", server.gateway).commit()
            val vm = MemohViewModel(app)
            try { runBlocking { test(vm, server) } }
            finally { vm.logout(); prefs.edit().clear().commit() }
        }
        Dispatchers.resetMain()
    }
    private suspend fun login(vm: MemohViewModel) {
        vm.login.email = "user@example.com"
        vm.login.code = "123456"
        vm.loginWithCode()
        awaitState { vm.signedIn && !vm.teams.busy && vm.bot != null && !vm.loadingSessions }
    }

    @Test
    fun `real login bootstrap removes original missing team errors and file methods keep team ownership`() = withCloud { vm, server ->
        // Reproduce the former successful verification -> unscoped bots failure over real HTTP.
        val legacy = MemohApi().apply { configure(server.gateway) }
        legacy.verifyEmailCode("user@example.com", "123456")
        OkHttpClient().newCall(Request.Builder().url(server.origin + "/api/memoh/bots")
            .header("Cookie", "sid=user-1").build()).execute().use {
            assertEquals(400, it.code)
            assertTrue(it.body!!.string().contains("TEAM_REQUIRED"))
        }
        assertEquals(1, server.missingTeamErrors.get())
        legacy.clearIdentity()
        server.missingTeamErrors.set(0)
        server.requests.clear()

        login(vm)
        assertEquals("a", vm.teams.selectedId)
        assertFalse(vm.login.signingIn)
        assertEquals("", vm.error)
        awaitState { server.requests.any { it.path == "/api/v1/ws-tickets" } }
        val api = vm.workspaceApi
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val source = File.createTempFile("cloud-upload-", ".zip", RuntimeEnvironment.getApplication().cacheDir)
        val destination = File.createTempFile("cloud-download-", ".bin", RuntimeEnvironment.getApplication().cacheDir)
        try {
            source.writeText("archive bytes")
            val uri = Uri.fromFile(source)
            val out = Uri.fromFile(destination)
            api.getSession("shared", "chat")
            api.history("shared", "chat")
            api.listFiles("shared", "/data")
            assertEquals("hello", api.readFile("shared", "/data/a").text)
            api.writeFile("shared", "/data/a", "new", "r1")
            api.makeDirectory("shared", "/data/dir")
            api.renameEntry("shared", "/data/a", "/data/b")
            api.deleteEntry("shared", "/data/b")
            api.extractArchive("shared", "/data/a.zip")
            api.uploadFile("shared", "/data", uri, resolver, "a.zip")
            api.downloadFile("shared", "/data/a", out, resolver)
            assertEquals("downloaded bytes", destination.readText())
            api.downloadArchive("shared", listOf("/data"), out, resolver)
            api.botBackupSummary("shared")
            api.exportBotBackup("shared", out, resolver, emptyList(), "")
            api.previewBotBackup(uri, resolver, "new", "", emptyList(), "")
            api.importBotBackup(uri, resolver, "new", "", emptyList(), "")
            api.startDeviceLogin("shared", "agent")
            api.pollDeviceLogin("shared", "agent", "login")
            api.cancelDeviceLogin("shared", "agent", "login")
        } finally { source.delete(); destination.delete() }
        val discovery = server.requests.filter { it.path == "/api/v1/users/me" || it.path == "/api/v1/teams" }
        // verify-code identity, team bootstrap, and the post-sign-in profile read that supplies the
        // account avatar/display name. All three are deliberately team-less.
        assertEquals(4, discovery.size)
        assertTrue(discovery.all { it.team == null })
        val scoped = server.requests.filter { it.path.startsWith("/api/memoh/") || it.path == "/api/v1/ws-tickets" }
        assertTrue(scoped.size >= 20)
        assertTrue(scoped.all { it.team == "a" && it.cookie?.contains("sid=user-1") == true })
        assertEquals("Missing-team HTTP errors must fall from 1 to 0", 0, server.missingTeamErrors.get())
    }

    @Test
    fun `switching teams resets navigation and pending admission even with identical bot IDs`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Old chat", "agent"))
        assertTrue(vm.send("Pending message"))
        val history = vm.historyRevision
        val owner = vm.attachmentDraftOwner
        vm.teams.select("b")
        awaitState { vm.bot?.title == "b" && !vm.loadingSessions }
        assertNull(vm.session)
        assertNull(vm.pendingUserText)
        assertTrue(vm.pendingUserAttachments.isEmpty())
        assertTrue(vm.turns.isEmpty())
        assertTrue(vm.historyRevision > history)
        assertNotEquals(owner, vm.attachmentDraftOwner)
        assertEquals("", vm.agentController.selectedAgentId)
        assertEquals("", vm.runId)
        assertNull(vm.queueState)
        assertFalse(vm.queueBusy)
        assertEquals(0, server.missingTeamErrors.get())
    }

    @Test
    fun `late navigation from previous team cannot populate the new team`() = withCloud { vm, server ->
        login(vm)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.handler = { exchange, request ->
            if (request.path.endsWith("/sessions/slow")) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                runCatching { server.reply(exchange, """{"id":"slow","title":"Old team secret"}""") }
                true
            } else false
        }
        try {
            val navigation = vm.openSessionById("slow")!!
            awaitState { entered.count == 0L }
            vm.teams.select("b")
            release.countDown()
            withTimeout(10_000) { navigation.join() }
            awaitState { vm.bot?.title == "b" }
            assertNull(vm.session)
            assertFalse(vm.error.contains("Old team"))
        } finally { release.countDown() }
    }

    @Test
    fun `zero memberships stays signed in with no business requests and allows real team creation`() = withCloud { vm, server ->
        server.memberships = """{"teams":[]}"""
        vm.login.email = "user@example.com"; vm.login.code = "123456"; vm.loginWithCode()
        awaitState { vm.signedIn && !vm.teams.busy }
        assertEquals("", vm.teams.selectedId)
        assertNull(vm.bot)
        assertTrue(server.requests.none { it.path.startsWith("/api/memoh/") })
        vm.teams.create("New team", "")
        awaitState { vm.teams.selectedId == "new" && !vm.teams.busy && vm.bot != null }
        val creation = server.requests.single { it.path == "/api/v1/teams" && it.method == "POST" }
        assertNull(creation.team)
        assertEquals("New team", org.json.JSONObject(creation.body).getString("name"))
        assertEquals(0, server.missingTeamErrors.get())
    }
}
