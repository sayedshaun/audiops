package com.audiops.app.dsp

import com.audiops.app.throwIfCancelled
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** STFT frame size and hop used by the spectral tools (46 ms frames, 75% overlap at 44.1 kHz). */
internal const val STFT_SIZE = 2048
internal const val STFT_HOP = 512
internal const val STFT_BINS = STFT_SIZE / 2 + 1

/** Periodic square-root Hann: analysis × synthesis gives Hann, which sums to 2 at 75% overlap. */
private val sqrtHann = FloatArray(STFT_SIZE) { i -> sqrt(0.5 - 0.5 * cos(2 * PI * i / STFT_SIZE)).toFloat() }

/**
 * Streaming short-time Fourier transform with overlap-add resynthesis. [modify] receives
 * each frame's spectrum in JTransforms' packed real format and edits it in place. Output
 * has the same length as the input; the 1536-sample latency is compensated internally.
 */
class StftProcessor(private val modify: (spectrum: FloatArray) -> Unit) : AudioProcessor {
    private val fft = FloatFFT_1D(STFT_SIZE.toLong())
    private val window = FloatArray(STFT_SIZE)
    private val frame = FloatArray(STFT_SIZE)
    private val accumulator = FloatArray(STFT_SIZE)
    private val carry = FloatArray(STFT_HOP)
    private var carried = 0
    private var totalIn = 0L
    private var totalOut = 0L
    private var toSkip = STFT_SIZE - STFT_HOP

    private fun processHop(hop: FloatArray, out: SampleSink) {
        System.arraycopy(window, STFT_HOP, window, 0, STFT_SIZE - STFT_HOP)
        System.arraycopy(hop, 0, window, STFT_SIZE - STFT_HOP, STFT_HOP)
        for (i in 0 until STFT_SIZE) frame[i] = window[i] * sqrtHann[i]
        fft.realForward(frame)
        modify(frame)
        fft.realInverse(frame, true)
        for (i in 0 until STFT_SIZE) accumulator[i] += frame[i] * sqrtHann[i] * 0.5f
        for (i in 0 until STFT_HOP) {
            if (toSkip > 0) toSkip-- else out.add(accumulator[i])
        }
        System.arraycopy(accumulator, STFT_HOP, accumulator, 0, STFT_SIZE - STFT_HOP)
        java.util.Arrays.fill(accumulator, STFT_SIZE - STFT_HOP, STFT_SIZE, 0f)
    }

    override fun process(input: FloatArray): FloatArray {
        totalIn += input.size
        val out = SampleSink(input.size + STFT_HOP)
        var i = 0
        while (i < input.size) {
            val n = minOf(STFT_HOP - carried, input.size - i)
            System.arraycopy(input, i, carry, carried, n)
            carried += n
            i += n
            if (carried == STFT_HOP) {
                processHop(carry, out)
                carried = 0
            }
        }
        totalOut += out.size
        return out.toArray()
    }

    override fun flush(): FloatArray {
        val out = SampleSink(STFT_SIZE * 2)
        val zeros = FloatArray(STFT_HOP)
        // Complete the partial hop, then push zeros until every input sample has come out.
        if (carried > 0) {
            java.util.Arrays.fill(carry, carried, STFT_HOP, 0f)
            processHop(carry, out)
            carried = 0
        }
        while (totalOut + out.size < totalIn) processHop(zeros, out)
        val keep = (totalIn - totalOut).toInt().coerceIn(0, out.size)
        totalOut += keep
        return out.toArray().copyOf(keep)
    }
}

/** Growable float output buffer for processors. */
internal class SampleSink(capacity: Int) {
    private var data = FloatArray(capacity.coerceAtLeast(16))
    var size = 0
        private set

    fun add(x: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = x
    }

    fun addAll(values: FloatArray, from: Int = 0, to: Int = values.size) {
        val n = to - from
        if (n <= 0) return
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        System.arraycopy(values, from, data, size, n)
        size += n
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

/** Magnitude of bin [k] in a JTransforms packed spectrum of [STFT_SIZE] points. */
internal fun packedMagnitude(spectrum: FloatArray, k: Int): Float = when (k) {
    0 -> kotlin.math.abs(spectrum[0])
    STFT_SIZE / 2 -> kotlin.math.abs(spectrum[1])
    else -> kotlin.math.hypot(spectrum[2 * k], spectrum[2 * k + 1])
}

/** Multiplies bin [k] of a packed spectrum by [gain]. */
internal fun scaleBin(spectrum: FloatArray, k: Int, gain: Float) {
    when (k) {
        0 -> spectrum[0] *= gain

        STFT_SIZE / 2 -> spectrum[1] *= gain

        else -> {
            spectrum[2 * k] *= gain
            spectrum[2 * k + 1] *= gain
        }
    }
}

/**
 * Per-bin noise magnitude, learned from the quietest frames of a recording (in the spirit
 * of Audacity's noise reduction, but estimated automatically).
 */
class NoiseProfile(val magnitude: FloatArray) {
    companion object {
        /**
         * Two passes over [blocks] (called twice; each call must replay the same audio):
         * first to find the quietest 15% of frames by energy, then to average their spectra.
         */
        fun estimate(blocks: ((FloatArray) -> Unit) -> Unit): NoiseProfile {
            val energies = ArrayList<Float>()
            framesOf(blocks) { frame -> energies.add(frame.fold(0f) { a, x -> a + x * x }) }
            if (energies.isEmpty()) return NoiseProfile(FloatArray(STFT_BINS))
            val sorted = energies.sorted()
            val threshold = sorted[(sorted.size * 0.15).toInt().coerceIn(0, sorted.lastIndex)]

            val fft = FloatFFT_1D(STFT_SIZE.toLong())
            val sum = DoubleArray(STFT_BINS)
            val sumSq = DoubleArray(STFT_BINS)
            var count = 0
            var index = 0
            val work = FloatArray(STFT_SIZE)
            framesOf(blocks) { frame ->
                if (energies[index++] <= threshold) {
                    for (i in 0 until STFT_SIZE) work[i] = frame[i] * sqrtHann[i]
                    fft.realForward(work)
                    for (k in 0 until STFT_BINS) {
                        val m = packedMagnitude(work, k).toDouble()
                        sum[k] += m
                        sumSq[k] += m * m
                    }
                    count++
                }
            }
            if (count == 0) return NoiseProfile(FloatArray(STFT_BINS))
            // Mean plus one standard deviation: covers most of the noise's fluctuation.
            return NoiseProfile(
                FloatArray(STFT_BINS) { k ->
                    val mean = sum[k] / count
                    val variance = max(0.0, sumSq[k] / count - mean * mean)
                    (mean + sqrt(variance)).toFloat()
                },
            )
        }

        /** Calls [onFrame] for each frame of the stream (hop [STFT_HOP]); one window shift per hop. */
        private fun framesOf(blocks: ((FloatArray) -> Unit) -> Unit, onFrame: (FloatArray) -> Unit) {
            val window = FloatArray(STFT_SIZE)
            val hop = FloatArray(STFT_HOP)
            var filled = 0
            var carried = 0
            blocks { block ->
                throwIfCancelled()
                for (x in block) {
                    if (filled < STFT_SIZE) {
                        window[filled++] = x
                        if (filled == STFT_SIZE) onFrame(window)
                    } else {
                        hop[carried++] = x
                        if (carried == STFT_HOP) {
                            System.arraycopy(window, STFT_HOP, window, 0, STFT_SIZE - STFT_HOP)
                            System.arraycopy(hop, 0, window, STFT_SIZE - STFT_HOP, STFT_HOP)
                            carried = 0
                            onFrame(window)
                        }
                    }
                }
            }
            if (filled in 1 until STFT_SIZE) onFrame(window)
        }
    }
}

/**
 * Spectral-subtraction noise reduction with over-subtraction, a spectral floor, and
 * time/frequency smoothing of the gain mask to avoid "musical noise".
 * [strength] 0..1 maps to 6–30 dB of maximum reduction.
 */
fun noiseReducer(profile: NoiseProfile, strength: Float): AudioProcessor {
    val s = strength.coerceIn(0f, 1f)
    val overSubtraction = 1f + 3f * s
    val floor = 10f.pow(-(6f + 24f * s) / 20f)
    val noisePower = FloatArray(STFT_BINS) { profile.magnitude[it] * profile.magnitude[it] }
    val previous = FloatArray(STFT_BINS) { 1f }
    val gains = FloatArray(STFT_BINS)
    return StftProcessor { spectrum ->
        for (k in 0 until STFT_BINS) {
            val m = packedMagnitude(spectrum, k)
            val power = m * m
            val g = if (power <= 1e-12f) floor else sqrt(max(0f, 1f - overSubtraction * noisePower[k] / power))
            gains[k] = max(g, floor)
        }
        for (k in 0 until STFT_BINS) {
            // Frequency smoothing (3 taps), then temporal smoothing: fast attack, slower release.
            val left = gains[maxOf(0, k - 1)]
            val right = gains[minOf(STFT_BINS - 1, k + 1)]
            val smoothed = (left + 2 * gains[k] + right) / 4f
            val g = if (smoothed > previous[k]) 0.3f * previous[k] + 0.7f * smoothed else 0.7f * previous[k] + 0.3f * smoothed
            previous[k] = g
            scaleBin(spectrum, k, g)
        }
    }
}
