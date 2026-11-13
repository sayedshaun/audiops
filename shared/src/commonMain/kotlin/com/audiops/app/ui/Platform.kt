package com.audiops.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Platform UI services. Android uses the system document picker, predictive back and
 * wallpaper colours; desktop uses native file dialogs and the brand palette.
 */

/** Returns a function that opens a picker for audio; [onPicked] receives (document id, display name). */
@Composable
expect fun rememberAudioPicker(onPicked: (id: String, name: String) -> Unit): () -> Unit

/** Returns a function that asks where to save a file named like its argument; [onPicked] gets the destination or null. */
@Composable
expect fun rememberFileSaver(mimeType: String, onPicked: (destination: String?) -> Unit): (suggestedName: String) -> Unit

/** System back (predictive back on Android). No-op where the platform has no back gesture. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Keeps the display on while [enabled]. No-op on desktop. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Wallpaper-based colour scheme where supported (Android 12+), otherwise null. */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** Whether [platformDynamicColorScheme] can return a scheme on this device. */
expect val supportsDynamicColor: Boolean

/** True on desktop (Linux, Windows); used to hide phone-only settings and wording. */
expect val isDesktop: Boolean

/** Localized short file size, such as "104 MB". */
@Composable
expect fun formatFileSize(bytes: Long): String
