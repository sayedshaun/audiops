package com.audiops.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.audiops.app.ui.AudiopsApp
import com.audiops.app.ui.isDarkTheme

/** Android host for the shared [AudiopsApp]. */
class MainActivity : ComponentActivity() {
    private val viewModel: AudiopsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as AudiopsApplication
                AudiopsViewModel(createSavedStateHandle(), app.engine, SettingsRepository(app.settingsStore))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash screen until settings load, so the first frame uses the chosen theme.
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()

        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: UserSettings()
            SystemBarsFor(settings.isDarkTheme())
            AudiopsApp(viewModel, appVersion = BuildConfig.VERSION_NAME)
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
