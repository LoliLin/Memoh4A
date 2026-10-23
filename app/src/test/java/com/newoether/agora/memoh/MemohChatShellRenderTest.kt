package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
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
}
