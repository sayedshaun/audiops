package com.audiops.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiops.app.ui.AudiopsScreen
import com.audiops.app.ui.AudiopsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AudiopsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash screen until settings load, so the first frame uses the chosen theme.
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()

        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: UserSettings()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SystemBarsFor(darkTheme)
            KeepScreenOn(settings.keepScreenOn && state.busy)

            AudiopsTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack,
            ) {
                AudiopsScreen(viewModel)
            }
        }
    }

    /** Status and navigation bar icons follow the in-app theme, not only the system theme. */
    @Composable
    private fun SystemBarsFor(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
