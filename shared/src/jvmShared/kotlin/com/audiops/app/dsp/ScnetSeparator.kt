package com.audiops.app.dsp

import com.audiops.app.throwIfCancelled
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The four sources SCNet separates, in the order of its output. */
enum class Stem { DRUMS, BASS, OTHER, VOCALS }

/**
 * The SCNet network between its STFT and iSTFT. [input] is `[4, BINS, FRAMES]` floats (left real,
 * left imaginary, right real, right imaginary) and the result is `[16, BINS, FRAMES]`: for each
 * [Stem] in order, the same four channels of that source.
 */
fun interface SpectrumNetwork {
    fun run(input: FloatArray): FloatArray
}

/**
 * Music source separation with SCNet (Tong et al., ICASSP 2024; MIT): everything around the
 * network. The mix is standardized, cut into 11-second segments that overlap, each segment goes
 * through an STFT, [network] and an inverse STFT, and the segments are blended with linear
 * fades (Music-Source-Separation-Training's `demix()`). The input is mono 44.1 kHz and is fed
 * to both channels; stems come back mono. Memory stays bounded: finished audio is handed to the
 * sink while later segments are still running.
 *
 * [step] is the distance between segment starts as a fraction of a segment, at most 0.5:
 * 0.25 is the reference (every sample heard four times) and 0.5 halves the work for a small loss.
 */
class ScnetSeparator(private val network: SpectrumNetwork, step: Float = 0.5f) {
    private val hopSamples = (SEGMENT * step.coerceIn(0.05f, 0.5f)).roundToInt()
    private val fft = FloatFFT_1D(FFT.toLong())
    private val fades = FloatArray(FADE) { it / (FADE - 1f) }

    /** Number of network runs [separate] makes for [length] samples. */
    fun segmentCount(length: Int): Int = starts(paddedLength(length)).size

    /**
     * Separates [mix]. [sink] receives consecutive pieces of each stem (in time order, all stems
     * for the same stretch of time together); the pieces of one stem add up to [mix]'s length.
     * [progress] receives (segments done, segments in all).
     */
    fun separate(mix: FloatArray, progress: (done: Int, total: Int) -> Unit = { _, _ -> }, sink: (Stem, FloatArray) -> Unit) {
        require(mix.isNotEmpty()) { "Audio is empty" }
        val n = mix.size
        var sum = 0.0
        for (v in mix) sum += v
        val mean = sum / n
        var squares = 0.0
        for (v in mix) squares += (v - mean) * (v - mean)
        val std = sqrt(squares / n).coerceAtLeast(1e-8)

        val border = border(n)
        val length = n + 2 * border

        // The standardized mix with its reflected borders, read in place so a long song is held in memory once.
        fun at(j: Int): Float = ((mix[reflect(j - border, n)] - mean) / std).toFloat()
        val starts = starts(length)
        val acc = Array(Stem.entries.size) { FloatArray(SEGMENT) }
        val weight = FloatArray(SEGMENT)
        var base = 0 // padded index of acc[*][0] and weight[0]
        val outputStart = border
        val outputEnd = border + n

        starts.forEachIndexed { index, start ->
            throwIfCancelled()
            val last = index == starts.lastIndex
            val first = index == 0
            val stems = separateSegment(segment(length, start, ::at))
            for (s in acc.indices) {
                val target = acc[s]
                val source = stems[s]
                for (j in 0 until SEGMENT) {
                    val w = window(j, first, last)
                    target[j] += source[j] * w
                }
            }
            for (j in 0 until SEGMENT) weight[j] += window(j, first, last)

            // Everything before the next segment's start (or the end) is final.
            val finalEnd = if (last) length else starts[index + 1]
            val from = maxOf(base, outputStart)
            val to = minOf(finalEnd, outputEnd)
            if (to > from) {
                for (s in acc.indices) {
                    val piece =
                        FloatArray(to - from) { k ->
                            (acc[s][from - base + k] / weight[from - base + k].coerceAtLeast(1e-6f) * std).toFloat()
                        }
                    sink(Stem.entries[s], piece)
                }
            }
            if (!last) {
                val shift = finalEnd - base
                for (s in acc.indices) {
                    System.arraycopy(acc[s], shift, acc[s], 0, SEGMENT - shift)
                    java.util.Arrays.fill(acc[s], SEGMENT - shift, SEGMENT, 0f)
                }
                System.arraycopy(weight, shift, weight, 0, SEGMENT - shift)
                java.util.Arrays.fill(weight, SEGMENT - shift, SEGMENT, 0f)
                base = finalEnd
            }
            progress(index + 1, starts.size)
        }
    }

    /** Reflection padding on both sides keeps the edges of long songs out of a fade. */
    private fun border(n: Int): Int = if (n > 2 * SEGMENT - hopSamples) SEGMENT - hopSamples else 0

    private fun paddedLength(n: Int): Int = n + 2 * border(n)

    private fun starts(length: Int): List<Int> {
        val list = ArrayList<Int>()
        var s = 0
        while (true) {
            list += s
            if (s + SEGMENT >= length) break
            s += hopSamples
        }
        return list
    }

    /** Linear fades over the first and last tenth of a segment; none at the very start or end of the audio. */
    private fun window(j: Int, first: Boolean, last: Boolean): Float = when {
        j < FADE && !first -> fades[j]
        j >= SEGMENT - FADE && !last -> fades[SEGMENT - 1 - j]
        else -> 1f
    }

    /** One segment from [start] of a signal of [length] samples read through [at]; a short last segment is reflected out when more than half is audio, else zero-padded. */
    private fun segment(length: Int, start: Int, at: (Int) -> Float): FloatArray {
        val available = length - start
        if (available >= SEGMENT) return FloatArray(SEGMENT) { at(start + it) }
        val out = FloatArray(SEGMENT)
        for (j in 0 until available) out[j] = at(start + j)
        if (available > SEGMENT / 2) {
            for (j in available until SEGMENT) out[j] = at(start + reflect(j, available))
        }
        return out
    }

    /** STFT, network, inverse STFT of one segment: four mono stems of [SEGMENT] samples. */
    private fun separateSegment(segment: FloatArray): Array<FloatArray> {
        val output = network.run(spectrum(segment))
        require(output.size == STEMS * 4 * BINS * FRAMES) { "Unexpected network output size ${output.size}" }
        return Array(STEMS) { s -> inverse(output, s) }
    }

    /** `[4, BINS, FRAMES]` input: the segment's STFT, the same for both channels. */
    private fun spectrum(segment: FloatArray): FloatArray {
        val input = FloatArray(4 * BINS * FRAMES)
        val padded = FloatArray(SEGMENT + FFT) { j -> segment[reflect(j - FFT / 2, SEGMENT)] }
        val frame = FloatArray(FFT)
        val scale = 1f / sqrt(FFT.toFloat())
        for (f in 0 until FRAMES_USED) {
            System.arraycopy(padded, f * HOP, frame, 0, FFT)
            fft.realForward(frame)
            for (b in 0 until BINS) {
                val re: Float
                val im: Float
                when (b) {
                    0 -> {
                        re = frame[0]
                        im = 0f
                    }

                    BINS - 1 -> {
                        re = frame[1]
                        im = 0f
                    }

                    else -> {
                        re = frame[2 * b]
                        im = frame[2 * b + 1]
                    }
                }
                val at = b * FRAMES + f
                input[at] = re * scale
                input[BINS * FRAMES + at] = im * scale
                input[2 * BINS * FRAMES + at] = re * scale
                input[3 * BINS * FRAMES + at] = im * scale
            }
        }
        return input
    }

    /** Inverse STFT of stem [s] (left and right averaged), trimmed to [SEGMENT] samples. */
    private fun inverse(output: FloatArray, s: Int): FloatArray {
        val length = FFT + HOP * (FRAMES_USED - 1)
        val y = FloatArray(length)
        val frame = FloatArray(FFT)
        val plane = BINS * FRAMES
        val base = 4 * s * plane
        val scale = sqrt(FFT.toFloat()) * 0.5f // undo the forward scaling; average of the two channels
        for (f in 0 until FRAMES_USED) {
            for (b in 0 until BINS) {
                val at = b * FRAMES + f
                val re = (output[base + at] + output[base + 2 * plane + at]) * scale
                val im = (output[base + plane + at] + output[base + 3 * plane + at]) * scale
                when (b) {
                    0 -> frame[0] = re

                    BINS - 1 -> frame[1] = re

                    else -> {
                        frame[2 * b] = re
                        frame[2 * b + 1] = im
                    }
                }
            }
            fft.realInverse(frame, true)
            val offset = f * HOP
            for (i in 0 until FFT) y[offset + i] += frame[i]
        }
        // Rectangular frames overlap four times in the middle and fewer at the ends: divide by the overlap count.
        return FloatArray(SEGMENT) { j ->
            val g = j + FFT / 2
            val firstFrame = if (g < FFT) 0 else (g - FFT) / HOP + 1
            val lastFrame = minOf(g / HOP, FRAMES_USED - 1)
            y[g] / (lastFrame - firstFrame + 1).coerceAtLeast(1)
        }
    }

    /** Mirror index into `[0, length)` without repeating the edge sample (one bounce is enough here). */
    private fun reflect(i: Int, length: Int): Int = when {
        length == 1 -> 0
        i < 0 -> -i
        i >= length -> 2 * (length - 1) - i
        else -> i
    }.coerceIn(0, length - 1)

    companion object {
        const val SAMPLE_RATE = 44_100
        const val SEGMENT = 485_100
        const val FFT = 4096
        const val HOP = 1024
        const val BINS = FFT / 2 + 1
        const val FRAMES = 476
        const val STEMS = 4

        /** STFT frames of one segment before the network's padding to [FRAMES]. */
        private const val FRAMES_USED = 1 + SEGMENT / HOP
        private const val FADE = SEGMENT / 10
    }
}
