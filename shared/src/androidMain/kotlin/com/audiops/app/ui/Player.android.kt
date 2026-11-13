package com.audiops.app.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
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

/** MediaPlayer-backed player. Prepares asynchronously so the UI never blocks. */
private class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val scope = MainScope()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var ticker: Job? = null

    override fun toggle(id: String) {
        val current = player
        if (current != null && _state.value.id == id && prepared) {
            if (current.isPlaying) {
                current.pause()
                _state.update { it.copy(playing = false) }
                ticker?.cancel()
            } else {
                current.start()
                _state.update { it.copy(playing = true) }
                startTicker()
            }
            return
        }
        load(id)
    }

    private fun load(id: String) {
        release()
        _state.value = PlayerState(id = id)
        val mp = MediaPlayer()
        player = mp
        try {
            if (id.startsWith("/")) mp.setDataSource(id) else mp.setDataSource(context, id.toUri())
            mp.setOnPreparedListener {
                prepared = true
                it.start()
                _state.update { s -> s.copy(playing = true, durationMs = it.duration.toLong()) }
                startTicker()
            }
            mp.setOnCompletionListener {
                ticker?.cancel()
                _state.update { s -> s.copy(playing = false, positionMs = 0) }
            }
            mp.setOnErrorListener { _, _, _ ->
                ticker?.cancel()
                _state.update { s -> s.copy(playing = false, failed = true) }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            _state.update { it.copy(failed = true) }
            release()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                if (prepared) _state.update { it.copy(positionMs = mp.currentPosition.toLong()) }
                delay(100)
            }
        }
    }

    override fun seek(fraction: Float) {
        val mp = player ?: return
        if (!prepared) return
        val target = (fraction.coerceIn(0f, 1f) * mp.duration).toInt()
        mp.seekTo(target)
        _state.update { it.copy(positionMs = target.toLong()) }
    }

    override fun stop() {
        release()
        _state.value = PlayerState()
    }

    private fun release() {
        ticker?.cancel()
        prepared = false
        player?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            runCatching { it.release() }
        }
        player = null
    }

    fun dispose() {
        release()
        scope.cancel()
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember { AndroidAudioPlayer(context) }
    DisposableEffect(player) { onDispose { player.dispose() } }
    return player
}
