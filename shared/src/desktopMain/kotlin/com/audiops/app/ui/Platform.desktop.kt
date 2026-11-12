package com.audiops.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.DecimalFormat

private val audioExtensions = setOf("wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wma", "aiff")

@Composable
actual fun rememberAudioPicker(onPicked: (id: String, name: String) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Choose a recording", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in audioExtensions }
        isVisible = true
    }
    val file = dialog.file
    if (file != null) onPicked(File(dialog.directory, file).absolutePath, file)
}

@Composable
actual fun rememberFileSaver(mimeType: String, onPicked: (destination: String?) -> Unit): (suggestedName: String) -> Unit = { name ->
    val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply {
        file = name
        isVisible = true
    }
    onPicked(dialog.file?.let { File(dialog.directory, it).absolutePath })
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back gesture; the top bar back button handles navigation.
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // Desktop displays follow the operating system's power settings.
}

@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

actual val supportsDynamicColor: Boolean = false

actual val isDesktop: Boolean = true

@Composable
actual fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return DecimalFormat("#,##0.#").format(value) + " " + units[unit]
}
