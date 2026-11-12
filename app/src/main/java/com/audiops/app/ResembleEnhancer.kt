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
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

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

/** Experimental offline, fixed-shape implementation. Use on a worker thread. */
class ResembleEnhancer(modelDirectory: File, dtype: String = "fp16") : Closeable {
    /** Downloads a public Hub repository on first use. Call on a worker thread. */
    constructor(context: Context, repoId: String, dtype: String = "fp16") :
        this(context, repoId, dtype, ProcessingListener.None)

    internal constructor(context: Context, repoId: String, dtype: String, listener: ProcessingListener) :
        this(
            HubModels.prepare(context.applicationContext.noBackupFilesDir, repoId, dtype) { index, total ->
                listener.onModelDownload(index, total)
            },
            dtype,
        )

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
            throwIfCancelled()
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
