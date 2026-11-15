package com.audiops.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object NoRecorder : Recorder {
    override val available: Boolean = false
    override val state: RecorderState = RecorderState()

    override fun toggle() = Unit
}

@Composable
actual fun rememberRecorder(onRecorded: (id: String, name: String) -> Unit, onFailed: (detail: String?) -> Unit): Recorder =
    remember { NoRecorder }
