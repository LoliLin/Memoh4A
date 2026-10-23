package com.newoether.agora.memoh

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.work.Configuration
import androidx.work.WorkManager
import com.newoether.agora.data.local.ChatDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemohStartupTest {
    // Use the real manifest and launcher instead of substituting a bare test Application.
    @get:Rule val compose = createAndroidComposeRule<MemohActivity>()

    @Test
    fun `cold start renders login without opening the local Agora runtime`() {
        val app = compose.activity.application
        assertEquals(MemohApplication::class.java, app.javaClass)
        // Removing the auto-initializer must not break shared code that asks for WorkManager.
        assertNotNull(WorkManager.getInstance(app))
        assertNotNull((app as Configuration.Provider).workManagerConfiguration)
        compose.onNodeWithText("API address").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsDisplayed()
        assertFalse(compose.activity.getDatabasePath(ChatDatabase.DB_NAME).exists())
    }

    @Test
    fun `recreating the launcher keeps the remote login usable`() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("API address").assertIsDisplayed()
        // The server probe may have resolved email-code sign-in by now, so accept whichever
        // form that result selected rather than pinning one deployment's method.
        val readyToSignIn = compose.onAllNodesWithText("Connect").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Send code").fetchSemanticsNodes().isNotEmpty()
        assertTrue("Sign-in form must still offer an action", readyToSignIn)
        assertFalse(compose.activity.getDatabasePath(ChatDatabase.DB_NAME).exists())
    }
}
