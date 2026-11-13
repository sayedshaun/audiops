package com.audiops.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_pause
import com.audiops.shared.resources.action_play
import com.audiops.shared.resources.player_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Bars of a recording's peak envelope. Bars inside [selection] (fractions of the length)
 * use [activeColor]; the rest are dimmed. [playhead] draws the playback position.
 */
@Composable
fun WaveformView(
    peaks: FloatArray,
    modifier: Modifier = Modifier,
    selection: ClosedFloatingPointRange<Float> = 0f..1f,
    playhead: Float? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val playheadColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.clearAndSetSemantics { }) {
        if (peaks.isEmpty()) return@Canvas
        val loudest = peaks.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
        val step = size.width / peaks.size
        val barWidth = (step * 0.6f).coerceAtLeast(1.5f)
        val minHeight = 2.dp.toPx()
        peaks.forEachIndexed { i, peak ->
            val position = (i + 0.5f) / peaks.size
            val height = (peak / loudest * size.height).coerceAtLeast(minHeight)
            drawRoundRect(
                color = if (position in selection) activeColor else inactiveColor,
                topLeft = Offset(i * step + (step - barWidth) / 2, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
        if (playhead != null && playhead > 0f) {
            val x = playhead.coerceIn(0f, 1f) * size.width
            drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Play/pause, a seek bar and the time, for one item of the app-wide player. */
@Composable
fun PlayerBar(id: String, modifier: Modifier = Modifier) {
    val player = LocalAudioPlayer.current
    val state by player.state.collectAsState()
    val active = state.id == id
    val playing = active && state.playing
    val fraction = if (active && state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(onClick = { player.toggle(id) }, modifier = Modifier.size(48.dp)) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playing) Res.string.action_pause else Res.string.action_play),
            )
        }
        Slider(
            value = fraction,
            onValueChange = { if (active) player.seek(it) },
            enabled = active && state.durationMs > 0,
            // Visible on tonal cards too: the default inactive track matches container colours.
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            modifier = Modifier.weight(1f).height(40.dp),
        )
        Text(
            when {
                active && state.failed -> stringResource(Res.string.player_unavailable)
                active -> formatDuration(state.positionMs)
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(if (active && state.failed) 96.dp else 52.dp),
        )
    }
}
