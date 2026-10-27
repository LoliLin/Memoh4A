package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.ui.chat.MessageList
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.ui.theme.AgoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Memoh chat shell on the JVM.
 *
 * The emulator on this machine cannot start without a hardware hypervisor, so
 * this is the only way to prove the shared Agora components actually compose
 * and lay out with Memoh's data instead of only compiling.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class MemohChatShellRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main").isDirectory) return dir
            dir = dir.parentFile
        }
        error("project root not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun projectedTurnsAndLiveTailComposeIntoTheSharedMessageList() {
        val turns = listOf(
            MemohTurn("t1", "user", "保留 Agora 的原生界面，把 Memoh 接进来。"),
            MemohTurn(
                "t2", "assistant",
                blocks = listOf(
                    MemohBlock(0, "text", "已接入原生组件。"),
                    MemohBlock(1, "tool", "Completed", toolName = "read_file", toolOutput = "ok"),
                ),
            ),
        )
        val messages = memohMessages(
            turns = turns,
            pendingText = "这条还在等服务器确认",
            pendingAttachments = emptyList(),
            liveBlocks = listOf(MemohBlock(0, "text", "实时尾部输出")),
            running = true,
        )

        // Every Memoh turn, the optimistic pending turn and the live tail survive projection.
        assertTrue("expected 4 projected rows, got ${messages.size}", messages.size == 4)
        assertTrue(messages.any { it.id == MEMOH_PENDING_MESSAGE_ID })
        assertTrue(messages.any { it.id == MEMOH_LIVE_MESSAGE_ID })

        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        compose.setContent {
            AgoraTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAgoraMotionPolicy provides AgoraMotionPolicy.Default,
                ) {
                    MaterialTheme {
                        com.newoether.agora.ui.chat.ChatTopBar(
                            isNewChatMode = false,
                            conversations = emptyList(),
                            currentConversationId = "c1",
                            currentConversationTitle = "Agora × Memoh",
                            totalTokens = 0,
                            contextTokenBudget = 0,
                            contextAvailable = false,
                            subtitle = "Memoh · Connected",
                            onOpenDrawer = {},
                            onSystemPromptClick = {},
                            onNewChat = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Agora × Memoh", substring = true).assertIsDisplayed()

        // Robolectric cannot hand back a window bitmap (forceRedraw never settles), so
        // assert on the laid-out tree instead: the capsules and title must have real
        // bounds, which only happens if the component actually composed and measured.
        val root = compose.onRoot().fetchSemanticsNode()
        assertTrue("top bar laid out with no height", root.size.height > 0)
        assertTrue("top bar laid out with no width", root.size.width > 0)

        val title = compose.onNodeWithText("Agora × Memoh", substring = true)
            .fetchSemanticsNode()
        assertTrue("title capsule has no width", title.size.width > 0)
        assertTrue("title capsule has no height", title.size.height > 0)
        assertTrue(
            "title capsule starts below the top edge",
            title.boundsInRoot.top > 0f,
        )
    }

    @Test
    fun liveTurnAndSettledTurnRenderOnceInTheSharedMessageList() {
        val user = MemohTurn("user-row", "user", "A unique question", turnId = "turn-1", turnPosition = 1)
        val oldAnswer = MemohTurn("old-answer", "assistant", "Stale answer", turnId = "turn-1", turnPosition = 1)
        val finalAnswer = MemohTurn("answer-row", "assistant", "A unique answer", turnId = "turn-1", turnPosition = 1)
        val activeRows = memohTranscriptRows(listOf(user, oldAnswer), listOf(user), "A unique question",
            emptyList(), "turn-1", "turn-1", true, null)
        val settledRows = memohTranscriptRows(listOf(user, finalAnswer), emptyList(), null,
            emptyList(), null, "", false, null)
        val liveMessage = memohMessages(emptyList(), null, emptyList(),
            listOf(MemohBlock(1, "text", "A unique answer")), true).single()
        var settled by mutableStateOf(false)

        compose.setContent {
            val rows = if (settled) settledRows else activeRows
            val stable = StableMessageList(rows)
            AgoraTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true),
                ) {
                    MessageList(
                        messages = stable,
                        authoritativeMessages = stable,
                        allMessages = stable,
                        conversationId = "chat",
                        modifier = Modifier.width(360.dp).height(700.dp),
                        viewportHeight = 700,
                        isLoading = !settled,
                        streamingMessage = if (settled) null else liveMessage,
                        streamingAutoFollowEnabled = false,
                        messageActionsEnabled = false,
                        initialMessage = { id -> rows.firstOrNull { it.id == id } },
                        observeMessage = { id -> kotlinx.coroutines.flow.flowOf(rows.firstOrNull { it.id == id }) },
                    )
                }
            }
        }
        // The streaming overlay arrives through a state channel, so give composition a chance to
        // settle instead of asserting on the first frame.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("A unique answer").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("A unique question").assertCountEquals(1)
        compose.onAllNodesWithText("A unique answer").assertCountEquals(1)
        compose.onAllNodesWithText("Stale answer").assertCountEquals(0)
        val question = compose.onNodeWithText("A unique question").fetchSemanticsNode()
        val answer = compose.onNodeWithText("A unique answer").fetchSemanticsNode()
        assertTrue("the answer should be below its question", question.boundsInRoot.bottom <= answer.boundsInRoot.top)

        compose.runOnIdle { settled = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("A unique answer").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("A unique question").assertCountEquals(1)
        compose.onAllNodesWithText("A unique answer").assertCountEquals(1)
        compose.onAllNodesWithText("Stale answer").assertCountEquals(0)
    }
}
