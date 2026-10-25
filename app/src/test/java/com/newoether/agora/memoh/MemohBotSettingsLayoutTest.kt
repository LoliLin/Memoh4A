package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.R
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.ui.settings.LocalSettingsPaneBackButtonVisible
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w411dp-h891dp-xhdpi")
class MemohBotSettingsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun botSectionCanMeasureAndScrollLazyContentAndAlwaysOffersBack() {
        var wentBack = false
        compose.setContent {
            CompositionLocalProvider(
                LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true),
                LocalSettingsPaneBackButtonVisible provides false,
            ) {
                MaterialTheme {
                    MemohBotSectionFrame("Container", { wentBack = true }) {
                        LazyColumn(Modifier.testTag("section-list")) {
                            items(120) { Text("Row $it") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Row 0").assertIsDisplayed()
        compose.onNodeWithTag("section-list").performScrollToNode(hasText("Row 119"))
        compose.onNodeWithText("Row 119").assertIsDisplayed()
        val back = ApplicationProvider.getApplicationContext<Application>().getString(R.string.back)
        compose.onNodeWithContentDescription(back).performClick()
        assertTrue(wentBack)
    }
}
