/* Resemble Enhance signal processing (MIT, Resemble AI): pure JVM, no inference runtime. */
package com.audiops.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import org.jtransforms.fft.FloatFFT_1D

/** Fixed-shape DSP matching the experimental one-second exports. */
class AudioFeatures(filterBytes: ByteArray) {
    companion object {
        const val RATE = 44100
        const val SAMPLES = 44100
        const val FRAMES = 105
        const val BINS = 841
        const val MEL_BINS = 128
        private const val HOP = 420
        private const val DENOISE_FFT = 1680
        private const val MEL_FFT = 2048
    }

    private val melWeights: FloatArray
    private val melFft = FloatFFT_1D(MEL_FFT.toLong())
    private val denoiseFft = FloatFFT_1D(DENOISE_FFT.toLong())
    private val denoiseWindow = FloatArray(DENOISE_FFT) { hann(it, DENOISE_FFT) }
    private val melWindow = FloatArray(MEL_FFT) { hann(it, MEL_FFT) }

    init {
        require(filterBytes.size == 1025 * MEL_BINS * 4) { "Invalid mel filter bank" }
        val reader = ByteBuffer.wrap(filterBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        melWeights = FloatArray(reader.remaining()).also(reader::get)
    }

    private fun hann(index: Int, size: Int): Float = (0.5 - 0.5 * cos(2.0 * PI * index / size)).toFloat()

    fun mel(audio: FloatArray): FloatArray {
        require(audio.size == SAMPLES)
        val emphasis = FloatArray(audio.size) { i -> audio[i] - 0.97f * if (i == 0) 0f else audio[i - 1] }
        val output = FloatArray(MEL_BINS * FRAMES)
        val fftData = FloatArray(2 * MEL_FFT)
        val magnitudes = FloatArray(1025)
        for (frame in 0 until FRAMES) {
            java.util.Arrays.fill(fftData, 0f)
            val offset = frame * HOP - MEL_FFT / 2
            for (i in 0 until MEL_FFT) {
                val at = offset + i
                if (at in emphasis.indices) fftData[i] = emphasis[at] * melWindow[i]
            }
            melFft.realForwardFull(fftData)
            for (k in magnitudes.indices) magnitudes[k] = hypot(fftData[2 * k], fftData[2 * k + 1])
            for (m in 0 until MEL_BINS) {
                var sum = 0f
                for (k in magnitudes.indices) sum += magnitudes[k] * melWeights[k * MEL_BINS + m]
                val db = 20f * log10(max(sum, 1e-4f))
                output[m * FRAMES + frame] = ((db + 80f) / 95f - 0.3232421875f) / 0.1934284120798111f
            }
        }
        return output
    }

    /** Returns NCHW magnitudes, cosines, sines in the denoiser's layout. */
    fun denoiserFeatures(audio: FloatArray): FloatArray {
        require(audio.size == SAMPLES)
        val out = FloatArray(3 * BINS * FRAMES)
        val plane = BINS * FRAMES
        val data = FloatArray(DENOISE_FFT * 2)
        for (frame in 0 until FRAMES) {
            java.util.Arrays.fill(data, 0f)
            val offset = frame * HOP - DENOISE_FFT / 2
            for (i in 0 until DENOISE_FFT) {
                val pos = offset + i
                val reflected = when {
                    pos < 0 -> -pos
                    pos >= audio.size -> 2 * audio.size - pos - 2
                    else -> pos
                }
                data[i] = audio[reflected] * denoiseWindow[i]
            }
            denoiseFft.realForwardFull(data)
            for (bin in 0 until BINS) {
                val re = data[2 * bin]
                val im = data[2 * bin + 1]
                val angle = atan2(im, re)
                val idx = bin * FRAMES + frame
                out[idx] = hypot(re, im)
                out[plane + idx] = cos(angle)
                out[2 * plane + idx] = sin(angle)
            }
        }
        return out
    }

    /** Apply upstream magnitude/phase masks and invert its centered STFT. */
    fun denoiseWaveform(features: FloatArray, prediction: FloatArray): FloatArray {
        val plane = BINS * FRAMES
        require(features.size == 3 * plane && prediction.size == 3 * plane)
        val real = FloatArray(plane)
        val imag = FloatArray(plane)
        for (i in 0 until plane) {
            val mask = 1.0 / (1.0 + kotlin.math.exp(-prediction[i].toDouble()))
            val r = kotlin.math.tanh(prediction[plane + i])
            val im = kotlin.math.tanh(prediction[2 * plane + i])
            val mag = kotlin.math.sqrt(r * r + im * im + 1e-7f)
            val residualCos = r / mag
            val residualSin = im / mag
            val sepMag = max(0f, features[i] * mask.toFloat())
            val c = features[plane + i]
            val s = features[2 * plane + i]
            real[i] = sepMag * (c * residualCos - s * residualSin)
            imag[i] = sepMag * (s * residualCos + c * residualSin)
        }
        val accum = FloatArray(SAMPLES + DENOISE_FFT)
        val weights = FloatArray(accum.size)
        val data = FloatArray(2 * DENOISE_FFT)
        // Upstream repeats the last spectrum frame before inverse STFT.
        for (frame in 0..FRAMES) {
            val sourceFrame = minOf(frame, FRAMES - 1)
            for (bin in 0 until BINS) {
                val idx = bin * FRAMES + sourceFrame
                data[2 * bin] = real[idx]
                data[2 * bin + 1] = imag[idx]
            }
            for (bin in 1 until BINS - 1) {
                data[2 * (DENOISE_FFT - bin)] = data[2 * bin]
                data[2 * (DENOISE_FFT - bin) + 1] = -data[2 * bin + 1]
            }
            denoiseFft.complexInverse(data, true)
            val offset = frame * HOP
            for (i in 0 until DENOISE_FFT) {
                val pos = offset + i
                if (pos < accum.size) {
                    accum[pos] += data[2 * i] * denoiseWindow[i]
                    weights[pos] += denoiseWindow[i] * denoiseWindow[i]
                }
            }
        }
        return FloatArray(SAMPLES) { i ->
            val pos = i + DENOISE_FFT / 2
            if (weights[pos] > 1e-11f) accum[pos] / weights[pos] else 0f
        }
    }
}

/** Upstream's exponential time mapping with 16 midpoint steps / 32 evaluations. */
object MidpointSolver {
    private val times = doubleArrayOf(0.0, 0.15483690, 0.28779418, 0.40196359, 0.5,
        0.58418312, 0.65647053, 0.71854319, 0.77184451, 0.81761395, 0.85691582,
        0.89066404, 0.91964338, 0.94452772, 0.96589571, 0.98424425, 1.0)

    fun integrate(initial: FloatArray, velocity: (FloatArray, Float) -> FloatArray): FloatArray {
        var state = initial.copyOf()
        for (i in 0 until times.size - 1) {
            throwIfCancelled()
            val t = times[i]
            val dt = (times[i + 1] - t).toFloat()
            val k1 = velocity(state, t.toFloat())
            require(k1.size == state.size)
            val midpoint = FloatArray(state.size) { j -> state[j] + 0.5f * dt * k1[j] }
            val k2 = velocity(midpoint, (t + dt / 2.0).toFloat())
            require(k2.size == state.size)
            state = FloatArray(state.size) { j -> state[j] + dt * k2[j] }
        }
        return state
    }
}

