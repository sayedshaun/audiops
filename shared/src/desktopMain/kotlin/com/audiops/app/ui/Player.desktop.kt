package com.audiops.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

/** Java Sound player for WAV/AIFF files. Opening happens off the UI thread. */
private class DesktopAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val scope = MainScope()
    private var clip: Clip? = null
    private var ticker: Job? = null
    private var loader: Job? = null

    override fun toggle(id: String) {
        val current = clip
        if (current != null && _state.value.id == id) {
            if (current.isRunning) {
                current.stop()
                _state.update { it.copy(playing = false) }
            } else {
                if (current.framePosition >= current.frameLength) current.framePosition = 0
                current.start()
                _state.update { it.copy(playing = true) }
                startTicker()
            }
            return
        }
        release()
        _state.value = PlayerState(id = id)
        loader = scope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(File(id))) }
                }.getOrNull()
            }
            if (opened == null) {
                _state.update { it.copy(failed = true) }
                return@launch
            }
            clip = opened
            opened.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP && opened.framePosition >= opened.frameLength) {
                    _state.update { it.copy(playing = false, positionMs = 0) }
                }
            }
            opened.start()
            _state.update { it.copy(playing = true, durationMs = opened.microsecondLength / 1000) }
            startTicker()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = clip ?: break
                _state.update { it.copy(positionMs = c.microsecondPosition / 1000, playing = c.isRunning) }
                if (!c.isRunning) break
                delay(100)
            }
        }
    }

    override fun seek(fraction: Float) {
        val c = clip ?: return
        c.framePosition = (fraction.coerceIn(0f, 1f) * c.frameLength).toInt()
        _state.update { it.copy(positionMs = c.microsecondPosition / 1000) }
    }

    override fun stop() {
        release()
        _state.value = PlayerState()
    }

    private fun release() {
        loader?.cancel()
        ticker?.cancel()
        clip?.let {
            runCatching {
                it.stop()
                it.close()
            }
        }
        clip = null
    }

    fun dispose() {
        release()
        scope.cancel()
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(player) { onDispose { player.dispose() } }
    return player
}
