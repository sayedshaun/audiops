package com.audiops.app.dsp

import com.audiops.app.EqPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A streaming block processor. [process] may return more or fewer samples than it was
 * given (filters with latency, time-stretching, silence removal); [flush] returns what is
 * still buffered at the end of the stream. Instances are single-use and not thread-safe.
 */
interface AudioProcessor {
    fun process(input: FloatArray): FloatArray
    fun flush(): FloatArray = FloatArray(0)
}

/** Second-order IIR filter (RBJ Audio EQ Cookbook), transposed direct form II, in double precision. */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun lowPass(rate: Int, frequency: Double, q: Double = 0.7071): Biquad {
            val w = 2 * PI * frequency / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return normalized((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun highPass(rate: Int, frequency: Double, q: Double = 0.7071): Biquad {
            val w = 2 * PI * frequency / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return normalized((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun peaking(rate: Int, frequency: Double, q: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * frequency / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return normalized(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowShelf(rate: Int, frequency: Double, gainDb: Double, q: Double = 0.7071): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * frequency / rate
            val c = cos(w)
            val beta = 2 * sqrt(a) * sin(w) / (2 * q)
            return normalized(
                a * ((a + 1) - (a - 1) * c + beta),
                2 * a * ((a - 1) - (a + 1) * c),
                a * ((a + 1) - (a - 1) * c - beta),
                (a + 1) + (a - 1) * c + beta,
                -2 * ((a - 1) + (a + 1) * c),
                (a + 1) + (a - 1) * c - beta,
            )
        }

        fun highShelf(rate: Int, frequency: Double, gainDb: Double, q: Double = 0.7071): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * frequency / rate
            val c = cos(w)
            val beta = 2 * sqrt(a) * sin(w) / (2 * q)
            return normalized(
                a * ((a + 1) + (a - 1) * c + beta),
                -2 * a * ((a - 1) + (a + 1) * c),
                a * ((a + 1) + (a - 1) * c - beta),
                (a + 1) - (a - 1) * c + beta,
                2 * ((a - 1) - (a + 1) * c),
                (a + 1) - (a - 1) * c - beta,
            )
        }
    }
}

/** Runs samples through a cascade of biquads, with an optional output gain. */
class FilterChain(private val filters: List<Biquad>, private val outputGain: Double = 1.0) : AudioProcessor {
    override fun process(input: FloatArray): FloatArray = FloatArray(input.size) { i ->
        var x = input[i].toDouble()
        for (f in filters) x = f.process(x)
        (x * outputGain).toFloat()
    }

    companion object {
        /** Filter chains for the equalizer presets. Gains are modest so presets rarely clip. */
        fun equalizer(preset: EqPreset, rate: Int): FilterChain = when (preset) {
            EqPreset.VOICE_CLARITY -> FilterChain(
                listOf(
                    Biquad.highPass(rate, 80.0),
                    Biquad.peaking(rate, 250.0, 1.0, -3.0), // reduce mud
                    Biquad.peaking(rate, 3000.0, 1.0, 4.0), // presence
                    Biquad.highShelf(rate, 8000.0, 2.0), // air
                ),
                outputGain = 0.85,
            )

            EqPreset.BASS_BOOST -> FilterChain(listOf(Biquad.lowShelf(rate, 120.0, 6.0)), outputGain = 0.6)

            EqPreset.TREBLE_BOOST -> FilterChain(listOf(Biquad.highShelf(rate, 4000.0, 6.0)), outputGain = 0.6)

            EqPreset.WARM -> FilterChain(
                listOf(Biquad.lowShelf(rate, 200.0, 3.0), Biquad.highShelf(rate, 6000.0, -3.0)),
                outputGain = 0.8,
            )

            EqPreset.REMOVE_RUMBLE -> FilterChain(listOf(Biquad.highPass(rate, 90.0), Biquad.highPass(rate, 90.0)))

            EqPreset.TELEPHONE -> FilterChain(
                listOf(
                    Biquad.highPass(rate, 300.0),
                    Biquad.highPass(rate, 300.0),
                    Biquad.lowPass(rate, 3400.0),
                    Biquad.lowPass(rate, 3400.0),
                ),
            )
        }

        /** Low-pass before downsampling to [targetRate], to avoid aliasing. */
        fun antiAlias(rate: Int, targetRate: Int): FilterChain {
            val cutoff = 0.45 * targetRate
            return FilterChain(List(4) { Biquad.lowPass(rate, cutoff, 0.7071) })
        }
    }
}

/** Constant gain in dB. */
class GainProcessor(db: Float) : AudioProcessor {
    private val factor = 10.0.pow(db / 20.0).toFloat()
    override fun process(input: FloatArray): FloatArray = FloatArray(input.size) { input[it] * factor }
}

/**
 * Fade in over the first [inSamples] and out over the last [outSamples]. The fade-out
 * needs no total length: it holds back the last [outSamples] and shapes them on [flush].
 */
class FadeProcessor(private val inSamples: Int, private val outSamples: Int) : AudioProcessor {
    private var position = 0L
    private var held = FloatArray(0)

    override fun process(input: FloatArray): FloatArray {
        val faded = FloatArray(input.size) { i ->
            val p = position + i
            if (p < inSamples) input[i] * curve((p + 1).toFloat() / inSamples) else input[i]
        }
        position += input.size
        if (outSamples <= 0) return faded
        val combined = held + faded
        if (combined.size <= outSamples) {
            held = combined
            return FloatArray(0)
        }
        val release = combined.size - outSamples
        held = combined.copyOfRange(release, combined.size)
        return combined.copyOfRange(0, release)
    }

    override fun flush(): FloatArray {
        val n = held.size
        return FloatArray(n) { i -> held[i] * curve((n - i).toFloat() / outSamples.coerceAtLeast(1)) }
    }

    /** Smoothstep: gentle start and end, no audible corner. */
    private fun curve(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }
}

/**
 * Look-ahead peak limiter. Delays the signal by [lookAheadSamples] so gain can drop before
 * a peak arrives; releases smoothly. Output never exceeds [ceiling].
 */
class Limiter(ceilingDb: Float, rate: Int, lookAheadMs: Int = 5, releaseMs: Int = 80) : AudioProcessor {
    private val ceiling = 10.0.pow(ceilingDb / 20.0).toFloat()
    private val lookAhead = (rate * lookAheadMs / 1000).coerceAtLeast(1)
    private val release = kotlin.math.exp(-1.0 / (rate * releaseMs / 1000.0)).toFloat()
    private val delay = FloatArray(lookAhead)
    private var writeIndex = 0
    private var filled = 0
    private var gain = 1f

    // Sliding maximum of |x| over the look-ahead window plus the sample being output
    // (lookAhead + 1 samples), kept as a monotonic deque in a ring buffer.
    private val dequeIdx = LongArray(lookAhead + 2)
    private val dequeVal = FloatArray(lookAhead + 2)
    private var head = 0
    private var tail = 0
    private var count = 0L

    private fun pushPeak(value: Float) {
        while (tail != head && dequeVal[(tail - 1 + dequeIdx.size) % dequeIdx.size] <= value) {
            tail = (tail - 1 + dequeIdx.size) % dequeIdx.size
        }
        dequeIdx[tail] = count
        dequeVal[tail] = value
        tail = (tail + 1) % dequeIdx.size
        while (dequeIdx[head] < count - lookAhead) head = (head + 1) % dequeIdx.size
        count++
    }

    private fun step(x: Float, out: FloatArray, at: Int): Boolean {
        pushPeak(kotlin.math.abs(x))
        val peak = dequeVal[head]
        val target = if (peak > ceiling) ceiling / peak else 1f
        gain = if (target < gain) target else target + (gain - target) * release
        val delayed = delay[writeIndex]
        delay[writeIndex] = x
        writeIndex = (writeIndex + 1) % lookAhead
        if (filled < lookAhead) {
            filled++
            return false
        }
        out[at] = (delayed * gain).coerceIn(-ceiling, ceiling)
        return true
    }

    override fun process(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        var n = 0
        for (x in input) if (step(x, out, n)) n++
        return if (n == out.size) out else out.copyOf(n)
    }

    override fun flush(): FloatArray {
        // Exactly `filled` real samples are still delayed; lookAhead zeros push them all out.
        val out = FloatArray(filled)
        var n = 0
        repeat(lookAhead) { if (step(0f, out, n)) n++ }
        return out.copyOf(n)
    }
}

/** Applies processors in order, feeding each one's output to the next. */
class ProcessorChain(private val stages: List<AudioProcessor>) : AudioProcessor {
    override fun process(input: FloatArray): FloatArray = stages.fold(input) { block, stage -> stage.process(block) }

    override fun flush(): FloatArray {
        var carried = FloatArray(0)
        for (stage in stages) {
            val processed = if (carried.isEmpty()) FloatArray(0) else stage.process(carried)
            carried = processed + stage.flush()
        }
        return carried
    }
}
