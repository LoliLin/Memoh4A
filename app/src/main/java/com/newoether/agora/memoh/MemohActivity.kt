package com.newoether.agora.memoh

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.common.rememberAgoraHaptics
import com.newoether.agora.ui.motion.ProvideAgoraMotionPolicy
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ColorSchemePreset
import com.newoether.agora.ui.theme.SchemeStyle
import com.newoether.agora.ui.theme.ThemeMode
import com.newoether.agora.util.appLocaleFor
import com.newoether.agora.util.withAppLocale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Dedicated native entry. Execution and durable history are owned by Memoh. */
class MemohActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking { SettingsManager(newBase).appLanguage.first() }
        java.util.Locale.setDefault(
            appLocaleFor(language) ?: android.content.res.Resources.getSystem().configuration.locales[0],
        )
        super.attachBaseContext(newBase.withAppLocale(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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

            val resolvedThemeMode = runCatching { ThemeMode.valueOf(themeMode) }
                .getOrDefault(ThemeMode.FOLLOW_DEVICE)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (resolvedThemeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.FOLLOW_DEVICE -> systemDark
            }
            SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            AgoraTheme(
                themeMode = resolvedThemeMode,
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
