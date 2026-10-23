package com.newoether.agora.memoh

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemohDeviceLoginPollerTest {
    private val login = MemohDeviceLogin("login", "CODE", "https://example.com/device")

    private fun TestScope.poller(
        start: suspend (String) -> MemohDeviceLogin = { login },
        poll: suspend (String, String) -> MemohDeviceLoginResult = { _, _ -> MemohDeviceLoginResult("pending") },
        cancel: suspend (String, String) -> Unit = { _, _ -> },
        refresh: suspend () -> Unit = {},
        current: () -> Boolean = { true },
    ) = MemohDeviceLoginPoller(this, start, poll, cancel, refresh, current, { testScheduler.currentTime })

    @Test fun successAliasesStayVisibleAndRefreshOnlyOnce() = runTest {
        for (status in listOf("success", "authorized")) {
            var polls = 0
            var refreshes = 0
            var cancels = 0
            val helper = poller(
                poll = { _, _ -> polls++; MemohDeviceLoginResult(status) },
                refresh = { refreshes++ },
                cancel = { _, _ -> cancels++ },
            )
            helper.start("agent", "Agent")
            runCurrent()
            advanceTimeBy(2_499)
            assertEquals(0, polls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(DeviceLoginPhase.Authorized, helper.state.value?.phase)
            advanceTimeBy(60_000)
            assertEquals(DeviceLoginPhase.Authorized, helper.state.value?.phase)
            assertFalse(helper.state.value!!.pending)
            assertEquals(1, polls)
            assertEquals(1, refreshes)
            helper.close()
            runCurrent()
            assertNull(helper.state.value)
            assertEquals(0, cancels)
        }
    }

    @Test fun refreshFailureCannotTurnAuthorizationIntoPollingFailure() = runTest {
        var polls = 0
        val helper = poller(
            poll = { _, _ -> polls++; MemohDeviceLoginResult("success") },
            refresh = { throw IOException("catalog offline") },
        )
        helper.start("agent", "Agent")
        advanceUntilIdle()
        assertEquals(DeviceLoginPhase.Authorized, helper.state.value?.phase)
        assertTrue(helper.state.value!!.message.contains("Could not refresh agents: catalog offline"))
        assertEquals(1, polls)
    }

    @Test fun serverFailuresRemainVisibleWithoutNullOrBlankMessageArtifacts() = runTest {
        for ((status, phase) in listOf("expired" to DeviceLoginPhase.Expired, "failed" to DeviceLoginPhase.Failed)) {
            val helper = poller(poll = { _, _ -> MemohDeviceLoginResult(status, "Server detail") })
            helper.start("agent", "Agent")
            advanceUntilIdle()
            assertEquals(phase, helper.state.value?.phase)
            assertTrue(helper.state.value!!.message.endsWith("Server detail"))
            helper.close()
        }
        val helper = poller(poll = { _, _ -> MemohDeviceLoginResult("failed") })
        helper.start("agent", "Agent")
        advanceUntilIdle()
        assertEquals("Authorization failed.", helper.state.value?.message)
    }

    @Test fun localTimeoutStopsAtFifteenMinutesAndStaysVisible() = runTest {
        var polls = 0
        val helper = poller(poll = { _, _ -> polls++; MemohDeviceLoginResult("pending") })
        helper.start("agent", "Agent")
        runCurrent()
        advanceTimeBy(900_000)
        runCurrent()
        assertEquals(DeviceLoginPhase.TimedOut, helper.state.value?.phase)
        assertEquals(359, polls)
        advanceTimeBy(60_000)
        assertEquals(359, polls)
    }

    @Test fun eightConsecutiveFailuresStopAndSuccessfulPendingResetsRetryCount() = runTest {
        var polls = 0
        val helper = poller(poll = { _, _ ->
            polls++
            if (polls == 8) MemohDeviceLoginResult("pending") else throw IOException("offline")
        })
        helper.start("agent", "Agent")
        runCurrent()
        advanceTimeBy(17_500)
        runCurrent()
        assertEquals(DeviceLoginPhase.Pending, helper.state.value?.phase)
        assertTrue(helper.state.value!!.message.contains("retrying"))
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals("Waiting for authorization in browser…", helper.state.value?.message)
        advanceUntilIdle()
        assertEquals(16, polls)
        assertEquals(DeviceLoginPhase.Unreachable, helper.state.value?.phase)
    }

    @Test fun closingPendingCancelsPollAndCallsServerWithOriginalIds() = runTest {
        var pollCancelled = false
        val cancelled = mutableListOf<Pair<String, String>>()
        val helper = poller(
            poll = { _, _ -> try { awaitCancellation() } finally { pollCancelled = true } },
            cancel = { agentId, loginId -> cancelled += agentId to loginId },
        )
        helper.start("agent", "Agent")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        helper.close()
        helper.close()
        runCurrent()
        assertTrue(pollCancelled)
        assertNull(helper.state.value)
        assertEquals(listOf("agent" to "login"), cancelled)
    }

    @Test fun lateCancelledPollCannotOverwriteReplacementAttemptOrRefresh() = runTest {
        val late = CompletableDeferred<MemohDeviceLoginResult>()
        var refreshes = 0
        val helper = poller(
            poll = { agentId, _ ->
                if (agentId == "old") withContext(NonCancellable) { late.await() }
                else MemohDeviceLoginResult("failed", "new outcome")
            },
            refresh = { refreshes++ },
        )
        helper.start("old", "Old")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        helper.close()
        helper.start("new", "New")
        runCurrent()
        late.complete(MemohDeviceLoginResult("success"))
        advanceUntilIdle()
        assertEquals("new", helper.state.value?.agentId)
        assertEquals(DeviceLoginPhase.Failed, helper.state.value?.phase)
        assertEquals(0, refreshes)
    }

    @Test fun closingStartingAttemptCancelsRequestAndIgnoresLateStart() = runTest {
        val late = CompletableDeferred<MemohDeviceLogin>()
        var polls = 0
        val helper = poller(
            start = { withContext(NonCancellable) { late.await() } },
            poll = { _, _ -> polls++; MemohDeviceLoginResult("success") },
        )
        helper.start("agent", "Agent")
        runCurrent()
        assertEquals(DeviceLoginPhase.Starting, helper.state.value?.phase)
        helper.close()
        late.complete(login)
        advanceUntilIdle()
        assertNull(helper.state.value)
        assertEquals(0, polls)
    }

    @Test fun identityChangeAndDisposalIgnoreOldResponseAndCannotCancelThroughNewApiIdentity() = runTest {
        val late = CompletableDeferred<MemohDeviceLoginResult>()
        var identityCurrent = true
        var refreshes = 0
        var cancels = 0
        val old = poller(
            poll = { _, _ -> withContext(NonCancellable) { late.await() } },
            refresh = { refreshes++ },
            cancel = { _, _ -> cancels++ },
            current = { identityCurrent },
        )
        old.start("old-agent", "Old")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        identityCurrent = false
        old.close()
        val replacement = poller(poll = { _, _ -> MemohDeviceLoginResult("expired") })
        replacement.start("other-bot-agent", "Other")
        late.complete(MemohDeviceLoginResult("authorized"))
        advanceUntilIdle()
        assertNull(old.state.value)
        assertEquals("other-bot-agent", replacement.state.value?.agentId)
        assertEquals(DeviceLoginPhase.Expired, replacement.state.value?.phase)
        assertEquals(0, refreshes)
        assertEquals(0, cancels)
        replacement.dispose()
        assertNull(replacement.state.value)
    }

    @Test fun disposingBotScopeCancelsPollingWithoutSendingCancellationThroughAnotherScope() = runTest {
        var pollCancelled = false
        var cancels = 0
        val helper = poller(
            poll = { _, _ -> try { awaitCancellation() } finally { pollCancelled = true } },
            cancel = { _, _ -> cancels++ },
        )
        helper.start("agent", "Agent")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        helper.dispose()
        runCurrent()
        assertTrue(pollCancelled)
        assertNull(helper.state.value)
        assertEquals(0, cancels)
    }

    @Test fun closingAuthorizedWhileRefreshIsInFlightCannotResurrectOutcome() = runTest {
        val refreshResult = CompletableDeferred<Unit>()
        val helper = poller(
            poll = { _, _ -> MemohDeviceLoginResult("authorized") },
            refresh = { withContext(NonCancellable) { refreshResult.await() } },
        )
        helper.start("agent", "Agent")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(DeviceLoginPhase.Authorized, helper.state.value?.phase)
        helper.close()
        refreshResult.completeExceptionally(IOException("late metadata failure"))
        advanceUntilIdle()
        assertNull(helper.state.value)
    }

    @Test fun startFailureIsRetainedAndRepeatedStartDoesNotDuplicateRequests() = runTest {
        var starts = 0
        val helper = poller(start = { starts++; throw IOException("offline") })
        helper.start("agent", "Agent")
        helper.start("agent", "Agent")
        advanceUntilIdle()
        assertEquals(1, starts)
        assertEquals(DeviceLoginPhase.Failed, helper.state.value?.phase)
        assertTrue(helper.state.value!!.message.contains("offline"))
    }
}
