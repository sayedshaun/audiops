package com.audiops.app.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Sliding input window addressed by absolute sample index; old samples can be discarded. */
internal class SampleWindow {
    private var data = FloatArray(1 shl 15)
    private var size = 0
    var start = 0L
        private set
    val end: Long get() = start + size

    fun append(values: FloatArray) {
        if (size + values.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + values.size))
        System.arraycopy(values, 0, data, size, values.size)
        size += values.size
    }

    /** Sample at absolute [index], or 0 outside the stored range. */
    operator fun get(index: Long): Float {
        val i = index - start
        return if (i in 0 until size) data[i.toInt()] else 0f
    }

    fun discardBefore(index: Long) {
        val drop = (index - start).coerceIn(0, size.toLong()).toInt()
        if (drop == 0) return
        System.arraycopy(data, drop, data, 0, size - drop)
        size -= drop
        start += drop
    }
}

/**
 * Tempo change without pitch change (WSOLA: waveform-similarity overlap-add).
 * [speed] > 1 is faster/shorter. Output length is input length / [speed].
 */
class TimeStretcher(private val speed: Float) : AudioProcessor {
    private val frameSize = 1024
    private val synthesisHop = frameSize / 2
    private val tolerance = 256
    private val analysisHop = synthesisHop * speed.toDouble()
    private val window = FloatArray(frameSize) { i -> (0.5 - 0.5 * cos(2 * PI * i / frameSize)).toFloat() }
    private val input = SampleWindow()
    private val overlap = FloatArray(frameSize)
    private var frameIndex = 0L
    private var previous = 0L
    private var totalIn = 0L
    private var totalOut = 0L
    private var flushing = false

    private val passthrough = abs(speed - 1f) < 1e-3f

    override fun process(input: FloatArray): FloatArray {
        if (passthrough) return input
        totalIn += input.size
        this.input.append(input)
        return produce()
    }

    override fun flush(): FloatArray {
        if (passthrough) return FloatArray(0)
        flushing = true
        input.append(FloatArray(frameSize + tolerance * 2 + synthesisHop))
        val tail = produce()
        val expected = (totalIn / speed.toDouble()).roundToLong()
        // Emit what the overlap buffer still holds, then cut to the exact expected length.
        val all = tail + overlap.copyOf(synthesisHop)
        val keep = (expected - (totalOut - tail.size)).coerceIn(0, all.size.toLong()).toInt()
        totalOut = expected
        return all.copyOf(keep)
    }

    private fun produce(): FloatArray {
        val out = SampleSink(4096)
        while (true) {
            val nominal = (frameIndex * analysisHop).roundToLong()
            if (flushing && nominal >= totalIn) break
            val needEnd = maxOf(nominal + tolerance + frameSize, previous + synthesisHop + frameSize)
            if (needEnd > input.end) break
            val position = if (frameIndex == 0L) 0L else bestPosition(nominal)
            for (i in 0 until frameSize) overlap[i] += input[position + i] * window[i]
            for (i in 0 until synthesisHop) out.add(overlap[i])
            System.arraycopy(overlap, synthesisHop, overlap, 0, frameSize - synthesisHop)
            java.util.Arrays.fill(overlap, frameSize - synthesisHop, frameSize, 0f)
            previous = position
            frameIndex++
            val nextNominal = (frameIndex * analysisHop).roundToLong()
            input.discardBefore(minOf(nextNominal - tolerance, previous + synthesisHop) - 1)
        }
        totalOut += out.size
        return out.toArray()
    }

    /**
     * Offset near [nominal] whose frame best continues the previously copied frame
     * (normalized cross-correlation, computed on every 4th sample for speed).
     */
    private fun bestPosition(nominal: Long): Long {
        val natural = previous + synthesisHop
        var best = nominal
        var bestScore = Float.NEGATIVE_INFINITY
        val lowest = maxOf(0L, nominal - tolerance) - nominal
        for (delta in lowest..tolerance.toLong()) {
            val candidate = nominal + delta
            var dot = 0f
            var energy = 1e-9f
            var i = 0
            while (i < frameSize) {
                val c = input[candidate + i]
                dot += c * input[natural + i]
                energy += c * c
                i += 4
            }
            val score = dot / sqrt(energy)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }
}

/**
 * Streaming sample-rate change with cubic Hermite interpolation. [step] is input samples
 * per output sample (> 1 shortens, raising pitch when played at the original rate).
 */
class Resampler(private val step: Double) : AudioProcessor {
    private val input = SampleWindow()
    private var position = 0.0
    private var totalIn = 0L
    private var produced = 0L
    private var flushing = false
    private val passthrough = abs(step - 1.0) < 1e-6

    override fun process(input: FloatArray): FloatArray {
        if (passthrough) return input
        totalIn += input.size
        this.input.append(input)
        return produce()
    }

    override fun flush(): FloatArray {
        if (passthrough) return FloatArray(0)
        flushing = true
        input.append(FloatArray(4))
        val expected = kotlin.math.ceil(totalIn / step).toLong()
        val out = produce()
        val keep = (expected - (produced - out.size)).coerceIn(0, out.size.toLong()).toInt()
        produced = expected
        return out.copyOf(keep)
    }

    private fun produce(): FloatArray {
        val out = SampleSink(4096)
        while (true) {
            val i = floor(position).toLong()
            if (flushing) {
                if (position >= totalIn) break
            } else if (i + 2 >= input.end) {
                break
            }
            val t = (position - i).toFloat()
            val p0 = input[if (i == 0L) 0L else i - 1]
            val p1 = input[i]
            val p2 = input[i + 1]
            val p3 = input[i + 2]
            out.add(hermite(p0, p1, p2, p3, t))
            position += step
        }
        input.discardBefore(floor(position).toLong() - 2)
        produced += out.size
        return out.toArray()
    }

    private fun hermite(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val a = -0.5f * p0 + 1.5f * p1 - 1.5f * p2 + 0.5f * p3
        val b = p0 - 2.5f * p1 + 2f * p2 - 0.5f * p3
        val c = -0.5f * p0 + 0.5f * p2
        return ((a * t + b) * t + c) * t + p1
    }
}

/**
 * Tempo and pitch together: stretch by speed / pitchRatio, then resample by pitchRatio,
 * so pitch moves by [semitones] and duration changes only by [speed].
 */
fun speedPitchProcessor(speed: Float, semitones: Float, rate: Int): AudioProcessor {
    val ratio = Math.pow(2.0, semitones / 12.0)
    val stages = mutableListOf<AudioProcessor>()
    val stretch = (speed / ratio).toFloat()
    if (abs(stretch - 1f) > 1e-3f) stages += TimeStretcher(stretch)
    if (abs(ratio - 1.0) > 1e-6) {
        // Shifting up compresses the spectrum upward: remove what would fold past Nyquist.
        if (ratio > 1.0) stages += FilterChain.antiAlias(rate, (rate / ratio).toInt())
        stages += Resampler(ratio)
    }
    return ProcessorChain(stages)
}
