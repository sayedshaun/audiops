package com.audiops.app.ui

import androidx.compose.runtime.Composable

/** What the UI shows while recording: [recording], how long ([elapsedMs]) and the current loudness ([level], 0 to 1). */
data class RecorderState(val recording: Boolean = false, val elapsedMs: Long = 0, val level: Float = 0f)

/** Records from the microphone into a WAV file that becomes the current recording. */
interface Recorder {
    /** False where the platform cannot record (the Record button is hidden). */
    val available: Boolean

    /** Read from composition; changes while recording. */
    val state: RecorderState

    /** Starts recording (asking for microphone permission first) or, when recording, stops and delivers the file. */
    fun toggle()
}

/**
 * Creates a [Recorder]. [onRecorded] receives (document id, display name) when a recording stops;
 * [onFailed] receives a short reason. Recording stops by itself when the app leaves the foreground.
 */
@Composable
expect fun rememberRecorder(onRecorded: (id: String, name: String) -> Unit, onFailed: (detail: String?) -> Unit): Recorder
