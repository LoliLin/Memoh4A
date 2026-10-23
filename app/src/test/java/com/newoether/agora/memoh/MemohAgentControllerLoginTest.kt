package com.newoether.agora.memoh

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemohAgentControllerLoginTest {
    private fun api(): MemohApi = mockk {
        every { baseUrl } returns "https://example.com/api"
        every { token } returns "token"
        coEvery { agentProfiles() } returns emptyList()
    }

    private fun agent(id: String) = MemohAgent(id, id, "codex", true, true, "", mockk())

    @Test fun pollingReportsAuthorizationWithoutRefreshingMetadata() = runTest {
        val api = api()
        val controller = MemohAgentController(api)
        for (status in listOf("success", "authorized")) {
            coEvery { api.pollDeviceLogin("bot", "agent", "login") } returns MemohDeviceLoginResult(status)
            assertEquals(status, controller.pollDeviceLogin("bot", agent("agent"), "login").status)
        }
        coVerify(exactly = 0) { api.agents(any()) }
        coVerify(exactly = 0) { api.agentProfiles() }
    }

    @Test fun cancelledRefreshCannotPublishLateAgentsOrLoadProfiles() = runTest {
        val api = api()
        val late = CompletableDeferred<List<MemohAgent>>()
        coEvery { api.agents("old") } coAnswers { withContext(NonCancellable) { late.await() } }
        val controller = MemohAgentController(api)
        val request = launch { controller.refresh("old") }
        runCurrent()
        request.cancel()
        late.complete(listOf(agent("old-agent")))
        runCurrent()
        assertTrue(controller.agents.isEmpty())
        assertEquals("", controller.error)
        assertFalse(controller.loading)
        coVerify(exactly = 0) { api.agentProfiles() }
    }

    @Test fun olderBotRefreshCannotOverwriteNewerBotMetadata() = runTest {
        val api = api()
        val late = CompletableDeferred<List<MemohAgent>>()
        coEvery { api.agents("old") } coAnswers { late.await() }
        coEvery { api.agents("new") } returns listOf(agent("new-agent"))
        val controller = MemohAgentController(api)
        launch { controller.refresh("old") }
        runCurrent()
        controller.refresh("new")
        late.complete(listOf(agent("old-agent")))
        runCurrent()
        assertEquals(listOf("new-agent"), controller.agents.map { it.id })
        assertFalse(controller.loading)
        coVerify(exactly = 1) { api.agentProfiles() }
    }

    @Test fun signOutClearInvalidatesInFlightRefreshEvenIfSameApiIsReused() = runTest {
        val api = api()
        val late = CompletableDeferred<List<MemohAgent>>()
        coEvery { api.agents("bot") } coAnswers { late.await() }
        val controller = MemohAgentController(api)
        launch { controller.refresh("bot") }
        runCurrent()
        controller.clear()
        every { api.token } returns ""
        late.complete(listOf(agent("old-agent")))
        runCurrent()
        assertTrue(controller.agents.isEmpty())
        assertTrue(controller.profiles.isEmpty())
        assertFalse(controller.loading)
        assertEquals("", controller.error)
        coVerify(exactly = 0) { api.agentProfiles() }
    }
}
