package com.audiops.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiops.app.AudiopsViewModel
import com.audiops.app.ThemeMode
import com.audiops.app.UserSettings

/**
 * The whole app, shared by Android and desktop: applies the user's theme settings and
 * shows the navigation root. Platforms only host it (Activity or Window).
 */
@Composable
fun AudiopsApp(viewModel: AudiopsViewModel, appVersion: String) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: UserSettings()
    val state by viewModel.state.collectAsStateWithLifecycle()

    KeepScreenOn(settings.keepScreenOn && state.activeJobs.isNotEmpty())

    AudiopsTheme(
        darkTheme = settings.isDarkTheme(),
        dynamicColor = settings.dynamicColor,
        pureBlack = settings.pureBlack,
    ) {
        // One player for the whole app: starting another item stops the previous one.
        CompositionLocalProvider(LocalAudioPlayer provides rememberAudioPlayer()) {
            AudiopsScreen(viewModel, appVersion)
        }
    }
}

/** Resolves the theme setting against the system theme. */
@Composable
fun UserSettings.isDarkTheme(): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
