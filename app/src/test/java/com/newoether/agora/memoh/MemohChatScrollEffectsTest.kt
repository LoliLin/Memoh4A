package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import com.newoether.agora.ui.chat.AbsoluteBottomSentinelKey
import com.newoether.agora.ui.chat.ChatScrollCoordinator
import com.newoether.agora.ui.chat.buildMessageListTurns
import com.newoether.agora.ui.chat.rememberChatScrollCoordinator
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class MemohChatScrollEffectsTest {
    @get:Rule val compose = createComposeRule()

    private class Fixture(searching: Boolean = false, loaded: Boolean = true) {
        val messages = mutableStateOf((0 until 12).flatMap { index ->
            listOf(
                ChatMessage(id = "user-$index", text = "Question $index", participant = Participant.USER),
                ChatMessage(id = "model-$index", text = "Answer $index", participant = Participant.MODEL),
            )
        })
        val searching = mutableStateOf(searching)
        val pending = mutableStateOf<String?>(null)
        val loadedSession = mutableStateOf(if (loaded) "session" else null)
        lateinit var coordinator: ChatScrollCoordinator
    }

    private fun render(fixture: Fixture) {
        compose.setContent {
            MaterialTheme {
                val coordinator = rememberChatScrollCoordinator("session", 0)
                fixture.coordinator = coordinator
                val density = LocalDensity.current
                coordinator.BindLayoutObservation("session", fixture.loadedSession.value, 0, density)
                MemohChatScrollEffects(
                    coordinator = coordinator,
                    sessionId = "session",
                    loadedHistorySessionId = fixture.loadedSession.value,
                    messages = fixture.messages,
                    pendingUserText = fixture.pending.value,
                    running = false,
                    searchActive = fixture.searching.value,
                    stickToBottom = true,
                    density = density,
                    bottomBarHeight = 0.dp,
                    motion = AgoraMotionPolicy(reduceMotion = true),
                )
                LazyColumn(
                    state = coordinator.listState,
                    modifier = Modifier.width(300.dp).height(300.dp)
                        .testTag("history")
                        .onSizeChanged { coordinator.recordViewportHeight(it.height) },
                ) {
                    items(buildMessageListTurns(fixture.messages.value), key = { it.messages.first().id }) { turn ->
                        Box(Modifier.height(180.dp)) { Text(turn.messages.first().text) }
                    }
                    item(key = AbsoluteBottomSentinelKey) { Spacer(Modifier.height(1.dp)) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun awaitBottom(fixture: Fixture) {
        compose.waitForIdle()
        try {
            compose.waitUntil(5_000) {
                val state = fixture.coordinator.listState
                state.layoutInfo.visibleItemsInfo.any { it.key == AbsoluteBottomSentinelKey } &&
                    !state.canScrollForward
            }
        } catch (timeout: Throwable) {
            val coordinator = fixture.coordinator
            val state = coordinator.listState
            throw AssertionError(
                "Bottom not reached: index=${state.firstVisibleItemIndex}, " +
                    "items=${state.layoutInfo.totalItemsCount}, forward=${state.canScrollForward}, " +
                    "viewport=${coordinator.viewportHeightPx}, phase=${coordinator.absoluteBottomScrollPhase}, " +
                    "visible=${state.layoutInfo.visibleItemsInfo.map { it.key }}",
                timeout,
            )
        }
        // Let the shared opening actor collect its three stable layout samples.
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
    }

    @Test
    fun openingWaitsForItsHistoryThenUsesTheGroupedListBottom() {
        val fixture = Fixture(loaded = false)
        render(fixture)
        assertEquals(0, fixture.coordinator.listState.firstVisibleItemIndex)
        compose.runOnIdle { fixture.loadedSession.value = "session" }
        awaitBottom(fixture)
        assertEquals(13, fixture.coordinator.listState.layoutInfo.totalItemsCount)
        assertTrue(fixture.coordinator.listState.firstVisibleItemIndex > 0)
    }

    @Test
    fun anIncomingTurnDoesNotPullAReaderOutOfHistory() {
        val fixture = Fixture()
        render(fixture)
        awaitBottom(fixture)
        compose.onNodeWithTag("history").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            !fixture.coordinator.listState.isScrollInProgress &&
                !fixture.coordinator.isWithinAbsoluteBottomAttachThreshold
        }
        val before = fixture.coordinator.listState.firstVisibleItemIndex
        compose.runOnIdle {
            fixture.messages.value += ChatMessage("new-user", text = "New question", participant = Participant.USER)
        }
        compose.waitForIdle()
        assertEquals(before, fixture.coordinator.listState.firstVisibleItemIndex)
        assertFalse(fixture.coordinator.listState.layoutInfo.visibleItemsInfo.any { it.key == AbsoluteBottomSentinelKey })
    }

    @Test
    fun activeSearchSuppressesOpeningAndPendingSendScrolls() {
        val fixture = Fixture(searching = true)
        render(fixture)
        compose.runOnIdle {
            fixture.pending.value = "Pending message"
            fixture.messages.value += ChatMessage(
                MEMOH_PENDING_MESSAGE_ID, text = "Pending message", participant = Participant.USER,
            )
        }
        compose.waitForIdle()
        assertEquals(0, fixture.coordinator.listState.firstVisibleItemIndex)
        assertTrue(fixture.coordinator.listState.canScrollForward)
    }

    @Test
    fun anAttachedReaderFollowsTheCommittedIncomingTurn() {
        val fixture = Fixture()
        render(fixture)
        awaitBottom(fixture)
        compose.waitUntil(5_000) { fixture.coordinator.isWithinAbsoluteBottomAttachThreshold }
        compose.runOnIdle {
            fixture.messages.value += ChatMessage("new-user", text = "New question", participant = Participant.USER)
        }
        awaitBottom(fixture)
        assertEquals(14, fixture.coordinator.listState.layoutInfo.totalItemsCount)
    }

    @Test
    fun sendingFromHistoryReachesTheCommittedPendingTurn() {
        val fixture = Fixture()
        render(fixture)
        awaitBottom(fixture)
        compose.onNodeWithTag("history").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            !fixture.coordinator.listState.isScrollInProgress &&
                !fixture.coordinator.isWithinAbsoluteBottomAttachThreshold
        }
        compose.runOnIdle {
            fixture.pending.value = "My next message"
            fixture.messages.value += ChatMessage(
                MEMOH_PENDING_MESSAGE_ID, text = "My next message", participant = Participant.USER,
            )
        }
        awaitBottom(fixture)
        assertEquals(14, fixture.coordinator.listState.layoutInfo.totalItemsCount)
    }
}
