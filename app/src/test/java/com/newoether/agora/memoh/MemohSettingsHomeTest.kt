package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.ui.settings.SettingsCategoryHome
import com.newoether.agora.ui.settings.rememberSettingsLazyListState
import com.newoether.agora.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings home must only offer pages this build can back with real data, and opening one must
 * go through Agora's own category list rather than a hand-rolled menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class MemohSettingsHomeTest {
    @get:Rule val compose = createComposeRule()

    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun everyAdvertisedCategoryHasARealDestination() {
        val keys = memohSettingsGroups.flatMap { group -> group.items.map { it.key } }
        assertEquals(keys.size, keys.distinct().size)
        // The shared SettingsCategoryHome can only navigate to keys it is given, so a missing
        // destination would strand the user on a blank page.
        keys.forEach { key -> assertTrue("no destination for $key", isMemohSettingsCategory(key)) }
        assertTrue(MemohSettingsCategory.APPEARANCE in keys)
        assertTrue(MemohSettingsCategory.LANGUAGE in keys)
        assertFalse("Agora-only provider settings must not be offered", MemohSettingsCategory.SERVER == "provider")
    }

    @Test
    fun homeListsMemohPagesAndReportsTheTappedCategory() {
        var opened: String? = null
        compose.setContent {
            AgoraTheme {
                CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy.Default) {
                    MaterialTheme {
                        SettingsCategoryHome(
                            settingsGroups = memohSettingsGroups,
                            listState = rememberSettingsLazyListState(),
                            onBack = {},
                            onCategorySelected = { opened = it },
                        )
                    }
                }
            }
        }

        val server = resources.getString(com.newoether.agora.R.string.memoh_settings_server)
        val account = resources.getString(com.newoether.agora.R.string.memoh_settings_account)
        val appearance = resources.getString(com.newoether.agora.R.string.settings_appearance)
        compose.onNodeWithText(server).assertIsDisplayed()
        compose.onNodeWithText(account).assertIsDisplayed()
        compose.onNodeWithText(appearance).assertIsDisplayed()

        compose.onNodeWithText(server).performClick()
        assertEquals(MemohSettingsCategory.SERVER, opened)
    }

    @Test
    fun groupTitlesUseTheSharedStrings() {
        val titles = memohSettingsGroups.mapNotNull { it.titleRes }
        assertEquals(titles.size, titles.distinct().size)
        assertTrue(titles.isNotEmpty())
    }
}
