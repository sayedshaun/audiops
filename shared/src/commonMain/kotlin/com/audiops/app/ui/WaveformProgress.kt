package com.audiops.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val BARS = 56
private const val GLOW_BARS = 3f

/**
 * Job progress drawn as a waveform: bars take the shape of [peaks] (the recording being worked on,
 * or a stable made-up shape from [seed] when there is none), those already processed are lit and the
 * few ahead of the front pulse. With no [progress] a soft wave sweeps across the bars instead. The
 * status text beside it carries the percentage, so the drawing is decorative for screen readers.
 */
@Composable
fun WaveformProgress(progress: Float?, peaks: FloatArray?, seed: String, modifier: Modifier = Modifier) {
    val lit = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val heights = remember(peaks, seed) { barHeights(peaks, seed) }
    val shown by animateFloatAsState(progress ?: 0f, ProgressIndicatorDefaults.ProgressAnimationSpec, label = "waveform progress")
    val pulse by rememberInfiniteTransition(label = "waveform pulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)), label = "phase")

    Canvas(modifier.fillMaxWidth().height(56.dp).clearAndSetSemantics { }) {
        val slot = size.width / BARS
        val width = slot * 0.6f
        for (i in 0 until BARS) {
            val center = (i + 0.5f) / BARS
            val color: Color
            val scale: Float
            if (progress != null) {
                val ahead = center - shown
                if (ahead <= 0f) {
                    color = lit
                    scale = 1f
                } else {
                    // The bars just ahead of the front glow and breathe.
                    val glow = (1f - ahead * BARS / GLOW_BARS).coerceIn(0f, 1f)
                    val breath = 0.5f + 0.5f * sin(pulse * 2 * PI.toFloat())
                    color = lerp(muted, lit, glow * (0.4f + 0.6f * breath))
                    scale = 1f + 0.12f * glow * breath
                }
            } else {
                val wave = 0.5f + 0.5f * sin((pulse - center * 1.5f) * 2 * PI.toFloat())
                color = lerp(muted, lit, wave * 0.9f)
                scale = 0.85f + 0.25f * wave
            }
            val half = size.height * heights[i] * scale / 2f
            val x = slot * (i + 0.5f)
            drawLine(
                color,
                Offset(x, size.height / 2f - half),
                Offset(x, size.height / 2f + half),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** [BARS] heights in 0.2..1: the loudest of each stretch of [peaks], or a stable speech-like shape from [seed]. */
private fun barHeights(peaks: FloatArray?, seed: String): FloatArray {
    if (peaks != null && peaks.isNotEmpty()) {
        val loudest = peaks.maxOrNull()?.coerceAtLeast(1e-4f) ?: 1f
        return FloatArray(BARS) { i ->
            val from = i * peaks.size / BARS
            val to = maxOf(from + 1, (i + 1) * peaks.size / BARS).coerceAtMost(peaks.size)
            var peak = 0f
            for (j in from until to) peak = maxOf(peak, abs(peaks[j]))
            0.2f + 0.8f * kotlin.math.sqrt(peak / loudest)
        }
    }
    var state = seed.fold(17) { h, c -> h * 31 + c.code }
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 8) and 0xffff) / 65535f
    }
    val a = next() * 6f
    val b = next() * 6f
    return FloatArray(BARS) { i ->
        val envelope = abs(sin(i * 0.23f + a) * sin(i * 0.071f + b))
        0.2f + 0.8f * (0.55f * envelope + 0.45f * next()).coerceIn(0f, 1f)
    }
}
