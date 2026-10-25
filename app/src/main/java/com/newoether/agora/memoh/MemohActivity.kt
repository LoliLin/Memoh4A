package com.newoether.agora.memoh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.common.rememberAgoraHaptics
import com.newoether.agora.ui.motion.ProvideAgoraMotionPolicy
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ColorSchemePreset
import com.newoether.agora.ui.theme.SchemeStyle
import com.newoether.agora.ui.theme.ThemeMode

/** Dedicated native entry. Execution and durable history are owned by Memoh. */
class MemohActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: MemohViewModel = viewModel()
            val settings = model.settings
            val themeMode by settings.themeMode.collectAsState()
            val colorScheme by settings.colorScheme.collectAsState()
            val schemeStyle by settings.schemeStyle.collectAsState()
            val dynamicColor by settings.dynamicColor.collectAsState()
            val amoledEnabled by settings.amoledEnabled.collectAsState()
            val fontPreference by settings.fontPreference.collectAsState()
            val customFontPath by settings.customFontPath.collectAsState()
            val reduceMotion by settings.reduceMotion.collectAsState()
            val hapticsEnabled by settings.hapticsEnabled.collectAsState()

            AgoraTheme(
                themeMode = runCatching { ThemeMode.valueOf(themeMode) }
                    .getOrDefault(ThemeMode.FOLLOW_DEVICE),
                colorSchemePreset = runCatching { ColorSchemePreset.valueOf(colorScheme) }
                    .getOrDefault(ColorSchemePreset.FOREST),
                schemeStyle = runCatching { SchemeStyle.valueOf(schemeStyle) }
                    .getOrDefault(SchemeStyle.TONAL_SPOT),
                dynamicColor = dynamicColor,
                amoledEnabled = amoledEnabled,
                fontPreference = fontPreference,
                customFontPath = customFontPath,
            ) {
                ProvideAgoraMotionPolicy(appReduceMotion = reduceMotion) {
                    CompositionLocalProvider(
                        LocalAgoraHaptics provides rememberAgoraHaptics(hapticsEnabled),
                    ) {
                        // A locale change only takes effect after the activity is recreated, which is
                        // what Agora's language page asks the user to confirm. Do it as soon as the
                        // saved preference changes rather than leaving a stale UI behind.
                        val activity = this@MemohActivity
                        LaunchedEffect(Unit) {
                            snapshotFlow { model.languageRestartPending }
                                .collect { pending ->
                                    if (pending) {
                                        model.consumeAppLanguageRestart()
                                        activity.recreate()
                                    }
                                }
                        }
                        MemohScreen(model)
                    }
                }
            }
        }
    }
}
