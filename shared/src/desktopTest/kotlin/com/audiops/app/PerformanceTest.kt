package com.audiops.app

import com.audiops.app.EqPreset
import com.audiops.app.dsp.AudioAnalyzer
import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.Limiter
import com.audiops.app.dsp.NoiseProfile
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import com.audiops.app.dsp.SilenceRemover
import com.audiops.app.dsp.noiseReducer
import com.audiops.app.dsp.speedPitchProcessor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Throughput guard: every DSP operation must run far faster than real time, so long
 * recordings never feel stuck. Prints the realtime factor of each operation.
 */
class PerformanceTest {
    private val rate = 44_100
    private val seconds = 60
    private val audio: FloatArray = Random(3).let { random ->
        FloatArray(rate * seconds) { i ->
            val t = i.toDouble() / rate
            val voice = if ((t.toInt() % 6) < 4) 0.3f * sin(2 * PI * (180 + 40 * sin(2 * PI * 0.7 * t)) * t).toFloat() else 0f
            voice + (random.nextFloat() * 2 - 1) * 0.03f
        }
    }

    private fun blocks(consume: (FloatArray) -> Unit) {
        var i = 0
        while (i < audio.size) {
            val n = minOf(16_384, audio.size - i)
            consume(audio.copyOfRange(i, i + n))
            i += n
        }
    }

    /** Seconds of audio processed per second of compute. */
    private fun realtimeFactor(name: String, make: () -> AudioProcessor): Double {
        val start = System.nanoTime()
        val processor = make()
        blocks { processor.process(it) }
        processor.flush()
        val elapsed = (System.nanoTime() - start) / 1e9
        val factor = seconds / elapsed
        println("perf %-16s %6.2f s for %d s of audio  (%.0fx real time)".format(name, elapsed, seconds, factor))
        return factor
    }

    @Test fun everyOperationRunsMuchFasterThanRealTime() {
        // Warm up the JIT so the first measurement is not penalized.
        realtimeFactor("warm-up") { FilterChain.equalizer(EqPreset.VOICE_CLARITY, rate) }

        val factors = mapOf(
            "equalizer" to
                realtimeFactor("equalizer") {
                    ProcessorChain(listOf(FilterChain.equalizer(EqPreset.VOICE_CLARITY, rate), Limiter(-0.5f, rate)))
                },
            "resample 48k" to
                realtimeFactor("resample 48k") {
                    ProcessorChain(listOf(FilterChain.antiAlias(rate, 44_100), Resampler(48_000.0 / 44_100)))
                },
            "resample 16k" to
                realtimeFactor("resample 16k") { ProcessorChain(listOf(FilterChain.antiAlias(rate, 16_000), Resampler(rate / 16_000.0))) },
            "speed 1.5x" to realtimeFactor("speed 1.5x") { speedPitchProcessor(1.5f, 0f, rate) },
            "pitch +5 st" to realtimeFactor("pitch +5 st") { speedPitchProcessor(1f, 5f, rate) },
            "silence" to realtimeFactor("silence") { SilenceRemover(-40f, 800, 120, rate) },
            "denoise" to realtimeFactor("denoise") {
                noiseReducer(NoiseProfile.estimate { consume -> blocks(consume) }, 0.6f)
            },
        )
        val start = System.nanoTime()
        AudioAnalyzer(rate).apply { blocks { add(it) } }.report()
        println("perf %-16s %6.2f s".format("analyze", (System.nanoTime() - start) / 1e9))

        // A loose bound that still catches accidental O(n²) work; desktop runs reach 50-500x.
        factors.forEach { (name, factor) -> assertTrue("$name only ${factor}x real time", factor > 5.0) }
    }
}
