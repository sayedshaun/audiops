package com.audiops.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// Platform UI services: the system document picker, predictive back and wallpaper colours.

/** Returns a function that opens a picker for audio; [onPicked] receives (document id, display name). */
@Composable
expect fun rememberAudioPicker(onPicked: (id: String, name: String) -> Unit): () -> Unit

/** Returns a function that asks where to save a file named like its argument; [onPicked] gets the destination or null. */
@Composable
expect fun rememberFileSaver(mimeType: String, onPicked: (destination: String?) -> Unit): (suggestedName: String) -> Unit

/** System back (predictive back on Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Keeps the display on while [enabled]. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Wallpaper-based colour scheme where supported (Android 12+), otherwise null. */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** Whether [platformDynamicColorScheme] can return a scheme on this device. */
expect val supportsDynamicColor: Boolean

/** Localized short file size, such as "104 MB". */
@Composable
expect fun formatFileSize(bytes: Long): String

/** Returns a function that asks for microphone access when needed, then calls [onResult] with whether it was granted. */
@Composable
expect fun rememberMicrophonePermission(onResult: (granted: Boolean) -> Unit): () -> Unit

/** Whether the app may show notifications ([enabled]), and [open], which opens the system page to change that. */
class NotificationAccess(val enabled: Boolean, val open: () -> Unit)

/** The current [NotificationAccess], checked again whenever the screen resumes (the user may come back from the system page). */
@Composable
expect fun rememberNotificationAccess(): NotificationAccess
