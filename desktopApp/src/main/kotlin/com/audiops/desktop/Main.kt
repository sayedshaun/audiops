package com.audiops.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.SavedStateHandle
import com.audiops.app.AudiopsViewModel
import com.audiops.app.HistoryRepository
import com.audiops.app.SettingsRepository
import com.audiops.app.createSettingsStore
import com.audiops.app.ui.AudiopsApp
import java.io.File

private const val APP_VERSION = "0.1.0"

/** Per-user data directory: %APPDATA%\Audiops on Windows, $XDG_DATA_HOME/audiops on Linux. */
private fun dataDirectory(): File {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    val base = when {
        windows -> System.getenv("APPDATA")?.let(::File)
        else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    } ?: File(System.getProperty("user.home"))
    return File(base, if (windows) "Audiops" else "audiops").apply { mkdirs() }
}

fun main() {
    val data = dataDirectory()
    // One DataStore, engine and ViewModel per process. The ViewModel is created directly
    // (desktop has no configuration changes), so no ViewModelStoreOwner is needed.
    val settingsStore = createSettingsStore(File(data, "settings.preferences_pb").absolutePath)
    val engine = DesktopAudioEngine(data)
    val viewModel = AudiopsViewModel(SavedStateHandle(), engine, SettingsRepository(settingsStore), HistoryRepository(settingsStore))

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Audiops",
            state = rememberWindowState(width = 1100.dp, height = 800.dp),
        ) {
            AudiopsApp(viewModel, appVersion = APP_VERSION)
        }
    }
}
