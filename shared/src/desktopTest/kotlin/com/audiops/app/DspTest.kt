package com.audiops.app

import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.Biquad
import com.audiops.app.dsp.DspPipeline
import com.audiops.app.dsp.FadeProcessor
import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.GainProcessor
import com.audiops.app.dsp.Limiter
import com.audiops.app.dsp.LoudnessMeter
import com.audiops.app.dsp.NoiseProfile
import com.audiops.app.dsp.PcmFile
import com.audiops.app.dsp.Resampler
import com.audiops.app.dsp.SilenceRemover
import com.audiops.app.dsp.StftProcessor
import com.audiops.app.dsp.TimeStretcher
import com.audiops.app.dsp.analyze
import com.audiops.app.dsp.noiseReducer
import com.audiops.app.dsp.speedPitchProcessor
import com.audiops.app.dsp.waveformPeaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val RATE = 44_100

private fun sine(frequency: Double, seconds: Double, amplitude: Float = 0.5f): FloatArray =
    FloatArray((RATE * seconds).toInt()) { i -> amplitude * sin(2 * PI * frequency * i / RATE).toFloat() }

private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
    var s = 0.0
    for (i in from until to) s += x[i].toDouble() * x[i]
    return sqrt(s / (to - from).coerceAtLeast(1)).toFloat()
}

private fun db(ratio: Float) = 20 * log10(ratio)

/** Frequency estimated from rising zero crossings in the middle of the signal. */
private fun frequency(x: FloatArray): Double {
    val from = x.size / 4
    val to = x.size * 3 / 4
    var crossings = 0
    for (i in from + 1 until to) if (x[i - 1] < 0 && x[i] >= 0) crossings++
    return crossings * RATE.toDouble() / (to - from)
}

/** Runs [input] through [processor] in uneven blocks, as the pipeline does, then flushes. */
private fun run(processor: AudioProcessor, input: FloatArray, block: Int = 3001): FloatArray {
    val out = ArrayList<FloatArray>()
    var i = 0
    while (i < input.size) {
        val n = minOf(block, input.size - i)
        out += processor.process(input.copyOfRange(i, i + n))
        i += n
    }
    out += processor.flush()
    return out.fold(FloatArray(0)) { a, b -> a + b }
}

class DspTest {
    @Test fun gainOf6DbDoublesAmplitude() {
        val out = run(GainProcessor(6.0206f), sine(440.0, 0.5, 0.25f))
        assertEquals(0.5f, out.maxOf { abs(it) }, 0.01f)
    }

    @Test fun lowPassKeepsBassAndRemovesTreble() {
        fun through(f: Double) = run(FilterChain(listOf(Biquad.lowPass(RATE, 1000.0), Biquad.lowPass(RATE, 1000.0))), sine(f, 1.0))
        val low = through(200.0)
        val high = through(8000.0)
        assertTrue("200 Hz should pass", rms(low, RATE / 2) > 0.33f)
        assertTrue("8 kHz should be cut by > 30 dB", db(rms(high, RATE / 2) / 0.3536f) < -30f)
    }

    @Test fun fadeShapesBothEndsAndKeepsLength() {
        val input = FloatArray(RATE) { 0.5f }
        val out = run(FadeProcessor(inSamples = 4410, outSamples = 4410), input)
        assertEquals(input.size, out.size)
        assertTrue(abs(out.first()) < 0.01f)
        assertTrue(abs(out.last()) < 0.01f)
        assertEquals(0.5f, out[RATE / 2], 1e-6f)
    }

    @Test fun limiterHoldsTheCeilingAndKeepsLength() {
        val input = sine(220.0, 1.0, 2.0f)
        val out = run(Limiter(-1f, RATE), input)
        assertEquals(input.size, out.size)
        val ceiling = Math.pow(10.0, -1.0 / 20).toFloat()
        assertTrue("peak ${out.maxOf { abs(it) }}", out.maxOf { abs(it) } <= ceiling + 1e-4f)
    }

    @Test fun limiterKeepsShortInputs() {
        val input = floatArrayOf(0.1f, -0.2f, 0.3f)
        assertEquals(3, run(Limiter(-1f, RATE), input).size)
    }

    @Test fun stftWithoutChangesReconstructsTheInput() {
        val input = sine(523.0, 0.7) + sine(97.0, 0.3, 0.2f)
        val out = run(StftProcessor { }, input)
        assertEquals(input.size, out.size)
        var maxError = 0f
        for (i in input.indices) maxError = maxOf(maxError, abs(input[i] - out[i]))
        assertTrue("max error $maxError", maxError < 1e-3f)
    }

    @Test fun denoiserLowersNoiseAndKeepsSpeechBand() {
        val random = Random(7)
        val tone = sine(440.0, 4.0, 0.3f)
        // Noise everywhere, the tone only after the first second (noise-only lead-in).
        val input = FloatArray(tone.size) { i -> (random.nextFloat() * 2 - 1) * 0.05f + if (i >= RATE) tone[i] else 0f }
        val profile = NoiseProfile.estimate { consume ->
            var i = 0
            while (i < input.size) {
                val n = minOf(4096, input.size - i)
                consume(input.copyOfRange(i, i + n))
                i += n
            }
        }
        val out = run(noiseReducer(profile, 0.7f), input)
        assertEquals(input.size, out.size)
        val noiseBefore = rms(input, RATE / 10, RATE * 9 / 10)
        val noiseAfter = rms(out, RATE / 10, RATE * 9 / 10)
        assertTrue("noise reduced by ${db(noiseAfter / noiseBefore)} dB", db(noiseAfter / noiseBefore) < -10f)
        val toneAfter = rms(out, RATE * 2, RATE * 3)
        assertTrue("tone kept: ${db(toneAfter / 0.2121f)} dB", abs(db(toneAfter / 0.2121f)) < 3f)
    }

    @Test fun timeStretchChangesDurationButNotPitch() {
        val input = sine(440.0, 2.0)
        for (speed in listOf(2f, 0.5f, 1.25f)) {
            val out = run(TimeStretcher(speed), input)
            val expected = input.size / speed
            assertEquals("length at $speed", expected, out.size.toFloat(), expected * 0.01f)
            assertEquals("pitch at $speed", 440.0, frequency(out), 440.0 * 0.03)
        }
    }

    @Test fun pitchShiftByAnOctaveDoublesFrequencyAndKeepsDuration() {
        val input = sine(440.0, 2.0)
        val out = run(speedPitchProcessor(speed = 1f, semitones = 12f, rate = RATE), input)
        assertEquals(input.size.toFloat(), out.size.toFloat(), input.size * 0.01f)
        assertEquals(880.0, frequency(out), 880 * 0.03)
    }

    @Test fun resamplerProducesTheTargetLength() {
        val out = run(Resampler(RATE / 16_000.0), sine(300.0, 1.0))
        assertEquals(16_000f, out.size.toFloat(), 2f)
        assertEquals(300.0 * RATE / 16_000, frequency(out), 300.0 * RATE / 16_000 * 0.03)
    }

    @Test fun silenceRemoverShortensOnlyLongPauses() {
        val tone = sine(440.0, 1.0)
        val longPause = tone + FloatArray(RATE * 2) + tone
        val out = run(SilenceRemover(-40f, minSilenceMs = 500, paddingMs = 100, rate = RATE), longPause)
        assertEquals(2.2f * RATE, out.size.toFloat(), RATE * 0.02f)

        val shortPause = tone + FloatArray(RATE * 3 / 10) + tone
        val kept = run(SilenceRemover(-40f, minSilenceMs = 500, paddingMs = 100, rate = RATE), shortPause)
        assertEquals(shortPause.size, kept.size)
    }

    @Test fun loudnessMatchesTheBs1770Reference() {
        // A full-scale 997 Hz sine measures -3.01 LUFS in mono; -20 dBFS gives -23.01.
        val meter = LoudnessMeter(RATE).apply { add(sine(997.0, 5.0, 1f)) }
        assertEquals(-3.01f, meter.integrated()!!, 0.3f)
        val quiet = LoudnessMeter(RATE).apply { add(sine(997.0, 5.0, 0.1f)) }
        assertEquals(-23.01f, quiet.integrated()!!, 0.3f)
    }

    @Test fun pipelineTrimsNormalizesAndResamples() {
        val dir = File.createTempFile("dsp", "").apply {
            delete()
            mkdirs()
        }
        try {
            val pcm = File(dir, "in.pcm")
            PcmFile.Writer(pcm).use { it.write(sine(440.0, 3.0, 0.2f)) }
            assertEquals(3000L, PcmFile.durationMs(pcm))
            assertEquals(100, waveformPeaks(pcm, 100).size)

            val trimmed = File(dir, "trim.wav")
            DspPipeline.run(
                pcm,
                listOf(AudioOperation.Trim(500, 1500), AudioOperation.NormalizePeak(-1f)),
                trimmed,
                dir,
                ProcessingListener.None,
            )
            val samples = readWav(trimmed)
            assertEquals(RATE.toFloat(), samples.size.toFloat(), 2f)
            assertEquals(-1f, db(samples.maxOf { abs(it) }), 0.1f)

            val resampled = File(dir, "16k.wav")
            DspPipeline.run(pcm, listOf(AudioOperation.Resample(16_000)), resampled, dir, ProcessingListener.None)
            val header = ByteBuffer.wrap(resampled.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(16_000, header.getInt(24))
            assertEquals(48_000f, ((resampled.length() - 44) / 2).toFloat(), 4f)

            val report = analyze(pcm, ProcessingListener.None)
            assertEquals(3000L, report.durationMs)
            assertEquals(db(0.2f), report.peakDb, 0.1f)
            assertTrue(dir.listFiles()!!.none { it.name.startsWith("stage-") })
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun readWav(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray((bytes.size - 44) / 2) { buffer.short / 32768f }
    }
}
