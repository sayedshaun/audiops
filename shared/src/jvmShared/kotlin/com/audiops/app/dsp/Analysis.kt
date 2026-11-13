package com.audiops.app.dsp

import com.audiops.app.AudioReport
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shortens pauses: any run of 10 ms frames quieter than [thresholdDb] that lasts at least
 * [minSilenceMs] is cut down to [paddingMs] on each side. Shorter pauses are untouched.
 */
class SilenceRemover(thresholdDb: Float, minSilenceMs: Long, paddingMs: Long, rate: Int) : AudioProcessor {
    private val frameSize = rate / 100
    private val threshold = 10.0.pow(thresholdDb / 20.0).toFloat()
    private val minFrames = (minSilenceMs / 10).toInt().coerceAtLeast(2)
    private val padFrames = (paddingMs / 10).toInt().coerceIn(0, minFrames / 2)
    private val frame = FloatArray(frameSize)
    private var filled = 0
    private val pending = ArrayList<FloatArray>() // silent frames while the run is still short
    private val tail = ArrayDeque<FloatArray>() // last frames of a long run (trailing padding)
    private var longRun = false

    override fun process(input: FloatArray): FloatArray {
        val out = SampleSink(input.size)
        for (x in input) {
            frame[filled++] = x
            if (filled == frameSize) {
                handle(frame.copyOf(), out)
                filled = 0
            }
        }
        return out.toArray()
    }

    override fun flush(): FloatArray {
        val out = SampleSink(frameSize * (minFrames + 2))
        if (filled > 0) handle(frame.copyOf(filled), out)
        filled = 0
        if (longRun) tail.forEach { out.addAll(it) } else pending.forEach { out.addAll(it) }
        pending.clear()
        tail.clear()
        return out.toArray()
    }

    private fun handle(f: FloatArray, out: SampleSink) {
        var sum = 0f
        for (x in f) sum += x * x
        val silent = sqrt(sum / f.size) < threshold
        if (silent) {
            if (!longRun) {
                pending.add(f)
                if (pending.size >= minFrames) {
                    for (i in 0 until padFrames) out.addAll(pending[i])
                    for (i in pending.size - padFrames until pending.size) tail.addLast(pending[i])
                    pending.clear()
                    longRun = true
                }
            } else {
                tail.addLast(f)
                while (tail.size > padFrames) tail.removeFirst()
            }
        } else {
            if (longRun) {
                tail.forEach { out.addAll(it) }
                tail.clear()
                longRun = false
            } else {
                pending.forEach { out.addAll(it) }
                pending.clear()
            }
            out.addAll(f)
        }
    }
}

/**
 * Integrated loudness per ITU-R BS.1770-4 for a mono signal: K-weighting, 400 ms blocks
 * with 75% overlap, an absolute gate at -70 LUFS and a relative gate 10 LU below.
 */
class LoudnessMeter(rate: Int) {
    // K-weighting: high-shelf (+4 dB above ~1.7 kHz) then a ~38 Hz high-pass.
    private val shelf = Biquad.highShelf(rate, 1681.974450955533, 3.999843853973347, 0.7071752369554196)
    private val highPass = Biquad.highPass(rate, 38.13547087602444, 0.5003270373238773)
    private val segmentLength = rate / 10 // 100 ms
    private var segmentSum = 0.0
    private var segmentCount = 0
    private val segments = ArrayList<Double>()

    fun add(block: FloatArray) {
        for (x in block) {
            val y = highPass.process(shelf.process(x.toDouble()))
            segmentSum += y * y
            if (++segmentCount == segmentLength) {
                segments.add(segmentSum / segmentLength)
                segmentSum = 0.0
                segmentCount = 0
            }
        }
    }

    /** Integrated loudness in LUFS, or null if fewer than 400 ms passed the gates. */
    fun integrated(): Float? {
        if (segments.size < 4) return null
        val blocks = DoubleArray(segments.size - 3) { i -> (segments[i] + segments[i + 1] + segments[i + 2] + segments[i + 3]) / 4 }
        fun lufs(meanSquare: Double) = -0.691 + 10 * log10(meanSquare)
        val absolute = blocks.filter { it > 0 && lufs(it) > -70.0 }
        if (absolute.isEmpty()) return null
        val relativeGate = lufs(absolute.average()) - 10.0
        val gated = absolute.filter { lufs(it) > relativeGate }
        if (gated.isEmpty()) return null
        return lufs(gated.average()).toFloat()
    }
}

/** Streaming measurement of an audio stream for the Analyze tool. */
class AudioAnalyzer(private val rate: Int) {
    private val loudness = LoudnessMeter(rate)
    private var count = 0L
    private var peak = 0f
    private var sumSquares = 0.0
    private var sum = 0.0
    private var clipped = 0L
    private var frames = 0L
    private var silentFrames = 0L
    private var frameSum = 0f
    private var frameFill = 0
    private val frameSize = rate / 100
    private val silence = 10.0.pow(-50.0 / 20.0).toFloat()

    fun add(block: FloatArray) {
        loudness.add(block)
        for (x in block) {
            val a = abs(x)
            if (a > peak) peak = a
            if (a >= 0.999f) clipped++
            sumSquares += x.toDouble() * x
            sum += x
            frameSum += x * x
            if (++frameFill == frameSize) {
                frames++
                if (sqrt(frameSum / frameSize) < silence) silentFrames++
                frameSum = 0f
                frameFill = 0
            }
        }
        count += block.size
    }

    /** Linear peak so far (used by peak normalization). */
    val peakLevel: Float get() = peak

    fun integratedLoudness(): Float? = loudness.integrated()

    fun report(): AudioReport {
        fun db(v: Double) = if (v <= 1e-10) -120f else (20 * log10(v)).toFloat()
        return AudioReport(
            durationMs = count * 1000 / rate,
            sampleRate = rate,
            peakDb = db(peak.toDouble()),
            rmsDb = db(if (count == 0L) 0.0 else sqrt(sumSquares / count)),
            loudnessLufs = loudness.integrated(),
            clippedSamples = clipped,
            dcOffset = if (count == 0L) 0f else (sum / count).toFloat(),
            silenceRatio = if (frames == 0L) 0f else silentFrames.toFloat() / frames,
        )
    }
}

/** Gain in dB that brings [measured] to [target], limited to ±[maxDb]. */
internal fun gainToward(measured: Float, target: Float, maxDb: Float = 30f): Float =
    (target - measured).coerceIn(-maxDb, maxDb).let { if (it.isNaN()) 0f else it }

/** dBFS of a linear level. */
internal fun toDb(level: Float): Float = if (level <= 1e-6f) -120f else 20f * log10(max(level, 1e-6f))
