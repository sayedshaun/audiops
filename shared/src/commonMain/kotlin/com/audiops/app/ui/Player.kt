package com.audiops.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/** Playback state of the app-wide player. Positions are in milliseconds. */
data class PlayerState(
    val id: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failed: Boolean = false,
)

/** Plays one recording or result at a time. [id] is a document id or a file path. */
interface AudioPlayer {
    val state: StateFlow<PlayerState>

    /** Plays [id], or pauses/resumes it if it is already loaded. */
    fun toggle(id: String)

    /** Seeks the loaded item to [fraction] of its length. */
    fun seek(fraction: Float)

    fun stop()
}

/** Creates the platform player; released when it leaves the composition. */
@Composable
expect fun rememberAudioPlayer(): AudioPlayer

val LocalAudioPlayer = staticCompositionLocalOf<AudioPlayer> { error("No AudioPlayer provided") }
