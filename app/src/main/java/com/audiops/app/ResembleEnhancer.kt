/* Experimental CPU LiteRT inference; see README.md for usage and limitations.
 * This file uses the Resemble Enhance model licensed under MIT.
 */
package com.audiops.app

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import org.jtransforms.fft.FloatFFT_1D

/** Download public Hub models once, using an app-private persistent cache. */
internal object HubModels {
    private val lock = Any()
    private val components = listOf("denoiser-net", "encoder", "flow-step", "decoder", "vocoder")

    fun fileName(name: String, dtype: String): String = when (dtype) {
        "fp32" -> "$name-float32.tflite"
        "fp16" -> "$name-fp16weights.tflite"
        "int8" -> if (name == "vocoder") "$name-int8-weights.tflite" else "$name-int8-dynamic.tflite"
        else -> throw IllegalArgumentException("dtype must be 'fp32', 'fp16', or 'int8'")
    }

    fun prepare(cacheRoot: File, repoId: String, dtype: String): File = synchronized(lock) {
        require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$").matches(repoId)) {
            "repoId must be a public Hugging Face repository in the form owner/model"
        }
        val files = components.map { fileName(it, dtype) } + "mel-filters.f32"
        val hash = MessageDigest.getInstance("SHA-256").digest(repoId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = File(cacheRoot, "resemble-enhance/$hash")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create model cache: $directory" }
        for (name in files) {
            val destination = File(directory, name)
            if (destination.isFile && destination.length() > 0L) continue
            download("https://huggingface.co/$repoId/resolve/main/models/$name", destination)
        }
        directory
    }

    internal fun download(url: String, destination: File) {
        val temp = File.createTempFile("download-", ".part", destination.parentFile)
        var connection: HttpURLConnection? = null
        try {
            var current = URL(url)
            for (redirects in 0..10) {
                require(current.protocol == "https") { "Model downloads require HTTPS" }
                val active = current.openConnection() as HttpURLConnection
                connection = active
                active.instanceFollowRedirects = false
                active.connectTimeout = 30000
                active.readTimeout = 120000
                val status = active.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    check(redirects < 10) { "Too many model download redirects" }
                    val location = active.getHeaderField("Location") ?: error("Missing redirect location")
                    current = URL(current, location)
                    active.disconnect()
                    connection = null
                    continue
                }
                check(status in 200..299) { "Model download failed with HTTP $status: ${destination.name}" }
                val expected = active.contentLengthLong
                val copied = active.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(copied > 0L && (expected < 0L || copied == expected)) {
                    "Incomplete model download: ${destination.name}"
                }
                check(temp.renameTo(destination)) { "Cannot save model: ${destination.name}" }
                return
            }
            error("Too many model download redirects")
        } finally {
            connection?.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}

/** A single-threaded CPU model. Calls must not overlap. */
internal class LiteModel(file: File, private val inputNames: List<String>, private val outputName: String,
                         private val inputSizes: IntArray, private val outputSize: Int) : Closeable {
    private val model: CompiledModel
    private val inputs: List<TensorBuffer>
    private val outputs: List<TensorBuffer>

    init {
        require(file.isFile && file.canRead()) { "Missing model: ${file.absolutePath}" }
        val options = CompiledModel.Options(Accelerator.CPU)
        options.cpuOptions = CompiledModel.CpuOptions(numThreads = 4)
        model = CompiledModel.create(file.absolutePath, options)
        val createdInputs = mutableListOf<TensorBuffer>()
        val createdOutputs = mutableListOf<TensorBuffer>()
        try {
            require(inputNames.size == inputSizes.size)
            inputNames.forEach { createdInputs.add(model.createInputBuffer(it, "serving_default")) }
            createdOutputs.add(model.createOutputBuffer(outputName, "serving_default"))
            require(createdInputs.size == inputSizes.size && createdOutputs.size == 1) {
                "Unexpected tensor count for ${file.name}"
            }
            inputs = createdInputs.toList()
            outputs = createdOutputs.toList()
        } catch (e: Throwable) {
            createdInputs.forEach { it.close() }
            createdOutputs.forEach { it.close() }
            model.close()
            throw e
        }
    }

    fun run(vararg values: FloatArray): FloatArray {
        require(values.size == inputs.size)
        values.forEachIndexed { index, value ->
            require(value.size == inputSizes[index]) { "Input $index has ${value.size} values; expected ${inputSizes[index]}" }
            inputs[index].writeFloat(value)
        }
        model.run(inputNames.zip(inputs).toMap(), mapOf(outputName to outputs[0]), "serving_default")
        val output = outputs[0].readFloat()
        require(output.size == outputSize) { "Output has ${output.size} values; expected $outputSize" }
        check(output.all { it.isFinite() }) { "Model returned non-finite values" }
        return output
    }

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
    }
}

/** Fixed-shape DSP matching the experimental one-second exports. */
internal class AudioFeatures(filterBytes: ByteArray) {
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
internal object MidpointSolver {
    private val times = doubleArrayOf(0.0, 0.15483690, 0.28779418, 0.40196359, 0.5,
        0.58418312, 0.65647053, 0.71854319, 0.77184451, 0.81761395, 0.85691582,
        0.89066404, 0.91964338, 0.94452772, 0.96589571, 0.98424425, 1.0)

    fun integrate(initial: FloatArray, velocity: (FloatArray, Float) -> FloatArray): FloatArray {
        var state = initial.copyOf()
        for (i in 0 until times.size - 1) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Enhancement cancelled")
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

/** Experimental offline, fixed-shape implementation. Use on a worker thread. */
class ResembleEnhancer(modelDirectory: File, dtype: String = "fp16") : Closeable {
    /** Downloads a public Hub repository on first use. Call on a worker thread. */
    constructor(context: Context, repoId: String, dtype: String = "fp16") :
        this(HubModels.prepare(context.applicationContext.noBackupFilesDir, repoId, dtype), dtype)

    companion object {
        private const val FRAMES = 105
        private const val LATENT = 64 * FRAMES
        private const val MEL = 128 * FRAMES
        private const val PREDICTION = 3 * 841 * FRAMES
        private const val VOCODER_INPUT = 160 * FRAMES
        private const val VOCODER_NOISE = 128 * (FRAMES + 10)
    }

    private val features = AudioFeatures(File(modelDirectory, "mel-filters.f32").readBytes())
    private val allModels = mutableListOf<LiteModel>()
    private val denoiser: LiteModel
    private val encoder: LiteModel
    private val flow: LiteModel
    private val decoder: LiteModel
    private val vocoder: LiteModel

    init {
        require(dtype in setOf("fp32", "fp16", "int8")) { "dtype must be 'fp32', 'fp16', or 'int8'" }
        fun open(name: String, names: List<String>, outputName: String, inputs: IntArray, output: Int): LiteModel =
            LiteModel(File(modelDirectory, HubModels.fileName(name, dtype)), names, outputName, inputs, output).also { allModels.add(it) }
        try {
            denoiser = open("denoiser-net", listOf("features"), "prediction", intArrayOf(PREDICTION), PREDICTION)
            encoder = open("encoder", listOf("mel"), "latent", intArrayOf(MEL), LATENT)
            flow = open("flow-step", listOf("state", "condition", "time"), "velocity", intArrayOf(LATENT, MEL, 1), LATENT)
            decoder = open("decoder", listOf("input"), "output", intArrayOf(LATENT), VOCODER_INPUT)
            vocoder = open("vocoder", listOf("conditioning", "noise"), "waveform", intArrayOf(VOCODER_INPUT, VOCODER_NOISE), AudioFeatures.SAMPLES)
        } catch (e: Throwable) {
            allModels.asReversed().forEach { it.close() }
            throw e
        }
    }

    /** Processes arbitrary length 44.1 kHz mono PCM with 50% overlap. */
    fun enhance(samples: FloatArray, progress: (Int, Int) -> Unit = { _, _ -> }): FloatArray {
        require(samples.isNotEmpty()) { "Audio is empty" }
        require(samples.all { it.isFinite() }) { "Audio contains non-finite samples" }
        val size = AudioFeatures.SAMPLES
        val hop = size / 2
        val chunks = (samples.size + hop - 1) / hop
        val output = DoubleArray(samples.size)
        val weights = DoubleArray(samples.size)
        val window = FloatArray(size) { i -> maxOf(1e-3f, (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat()) }
        for (chunkIndex in 0 until chunks) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Enhancement cancelled")
            val start = chunkIndex * hop
            val count = minOf(size, samples.size - start)
            val chunk = FloatArray(size)
            samples.copyInto(chunk, 0, start, start + count)
            var peak = 1e-7f
            for (i in 0 until count) peak = maxOf(peak, abs(chunk[i]))
            val enhanced = if (peak <= 1e-7f) FloatArray(size) else enhanceChunk(chunk, 1234L + chunkIndex)
            for (i in 0 until count) {
                val w = window[i].toDouble()
                output[start + i] += enhanced[i] * peak * w
                weights[start + i] += w
            }
            progress(chunkIndex + 1, chunks)
        }
        return FloatArray(samples.size) { (output[it] / weights[it]).toFloat() }
    }

    private fun enhanceChunk(samples: FloatArray, seed: Long): FloatArray {
        // Enhancer normalizes first; the denoiser normalizes once more.
        fun normalize(x: FloatArray): FloatArray {
            var peak = 0f
            for (v in x) peak = maxOf(peak, abs(v))
            return FloatArray(x.size) { x[it] / (peak + 1e-7f) }
        }
        val x = normalize(samples)
        val originalMel = features.mel(x)
        val spectral = features.denoiserFeatures(normalize(x))
        val denoised = features.denoiseWaveform(spectral, denoiser.run(spectral))
        val cleanMel = features.mel(denoised)
        val condition = FloatArray(MEL) { (originalMel[it] + cleanMel[it]) * 0.5f }
        val prior = encoder.run(originalMel)
        val random = Random(seed)
        val initial = FloatArray(LATENT) { i -> 3f * prior[i] + 0.5f * random.nextGaussian().toFloat() }
        val state = MidpointSolver.integrate(initial) { value, time -> flow.run(value, condition, floatArrayOf(time)) }
        val decoded = decoder.run(FloatArray(LATENT) { state[it] / 6f })
        val noise = FloatArray(VOCODER_NOISE) { random.nextGaussian().toFloat() }
        return vocoder.run(decoded, noise)
    }

    override fun close() = allModels.asReversed().forEach { it.close() }
}
