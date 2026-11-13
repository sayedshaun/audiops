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
internal class LiteModel(
    file: File,
    private val inputNames: List<String>,
    private val outputName: String,
    private val inputSizes: IntArray,
    private val outputSize: Int,
) : Closeable {
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

/**
 * Experimental offline, fixed-shape implementation. Use on a worker thread.
 *
 * Runs **staged**: each model is loaded, applied to every 1-second chunk of the input, and
 * closed before the next one loads. Loading all five at once needed ~2.2 GB on a phone
 * (weight-only INT8 layers are dequantized to float32 at load) and got the app killed by
 * Android's low-memory killer; staged, peak memory is about one model.
 */
class ResembleEnhancer(private val modelDirectory: File, private val dtype: String = "fp16") : Closeable {
    private class ModelSpec(val name: String, val inputs: List<String>, val output: String, val inputSizes: IntArray, val outputSize: Int)

    private class Chunk(val start: Int, val count: Int, val peak: Float, val seed: Long) {
        val silent: Boolean get() = peak <= 1e-7f
        var originalMel: FloatArray? = null
        var condition: FloatArray? = null
        var state: FloatArray? = null
        var decoded: FloatArray? = null
        var random: Random? = null
    }

    companion object {
        private const val FRAMES = 105
        private const val LATENT = 64 * FRAMES
        private const val MEL = 128 * FRAMES
        private const val PREDICTION = 3 * 841 * FRAMES
        private const val VOCODER_INPUT = 160 * FRAMES
        private const val VOCODER_NOISE = 128 * (FRAMES + 10)

        private val DENOISER = ModelSpec("denoiser-net", listOf("features"), "prediction", intArrayOf(PREDICTION), PREDICTION)
        private val ENCODER = ModelSpec("encoder", listOf("mel"), "latent", intArrayOf(MEL), LATENT)
        private val FLOW = ModelSpec("flow-step", listOf("state", "condition", "time"), "velocity", intArrayOf(LATENT, MEL, 1), LATENT)
        private val DECODER = ModelSpec("decoder", listOf("input"), "output", intArrayOf(LATENT), VOCODER_INPUT)
        private val VOCODER = ModelSpec(
            "vocoder",
            listOf("conditioning", "noise"),
            "waveform",
            intArrayOf(VOCODER_INPUT, VOCODER_NOISE),
            AudioFeatures.SAMPLES,
        )

        // Relative cost of each stage per chunk, for smooth progress (flow runs the model 32 times).
        private const val COST_DENOISER = 1
        private const val COST_ENCODER = 1
        private const val COST_FLOW = 6
        private const val COST_DECODER = 1
        private const val COST_VOCODER = 2
        private const val COST_TOTAL = COST_DENOISER + COST_ENCODER + COST_FLOW + COST_DECODER + COST_VOCODER
    }

    private val features: AudioFeatures

    init {
        require(dtype in setOf("fp32", "fp16", "int8")) { "dtype must be 'fp32', 'fp16', or 'int8'" }
        features = AudioFeatures(File(modelDirectory, "mel-filters.f32").readBytes())
        // Fail early, before any audio work, if a model file is missing.
        listOf(DENOISER, ENCODER, FLOW, DECODER, VOCODER).forEach { spec ->
            val file = File(modelDirectory, HubModels.fileName(spec.name, dtype))
            require(file.isFile && file.length() > 0) { "Missing model: ${file.name}" }
        }
    }

    private inline fun <T> withModel(spec: ModelSpec, block: (LiteModel) -> T): T =
        LiteModel(File(modelDirectory, HubModels.fileName(spec.name, dtype)), spec.inputs, spec.output, spec.inputSizes, spec.outputSize)
            .use(block)

    /**
     * Processes arbitrary length 44.1 kHz mono PCM with 50% overlap. [progress] receives
     * (done, total) in cost units across all stages.
     */
    fun enhance(samples: FloatArray, progress: (Int, Int) -> Unit = { _, _ -> }): FloatArray {
        require(samples.isNotEmpty()) { "Audio is empty" }
        require(samples.all { it.isFinite() }) { "Audio contains non-finite samples" }
        val size = AudioFeatures.SAMPLES
        val hop = size / 2
        val chunks = List((samples.size + hop - 1) / hop) { index ->
            val start = index * hop
            val count = minOf(size, samples.size - start)
            var peak = 1e-7f
            for (i in start until start + count) peak = maxOf(peak, abs(samples[i]))
            Chunk(start, count, peak, 1234L + index)
        }
        val active = chunks.filter { !it.silent }
        val total = active.size * COST_TOTAL
        var done = 0
        fun step(cost: Int) {
            done += cost
            progress(done, total)
        }
        fun chunkSamples(c: Chunk): FloatArray = FloatArray(size).also { samples.copyInto(it, 0, c.start, c.start + c.count) }
        fun normalize(x: FloatArray): FloatArray {
            var peak = 0f
            for (v in x) peak = maxOf(peak, abs(v))
            return FloatArray(x.size) { x[it] / (peak + 1e-7f) }
        }

        // Enhancer normalizes first; the denoiser normalizes once more.
        withModel(DENOISER) { denoiser ->
            for (c in active) {
                throwIfCancelled()
                val x = normalize(chunkSamples(c))
                val originalMel = features.mel(x)
                val spectral = features.denoiserFeatures(normalize(x))
                val cleanMel = features.mel(features.denoiseWaveform(spectral, denoiser.run(spectral)))
                c.originalMel = originalMel
                c.condition = FloatArray(MEL) { (originalMel[it] + cleanMel[it]) * 0.5f }
                step(COST_DENOISER)
            }
        }
        withModel(ENCODER) { encoder ->
            for (c in active) {
                throwIfCancelled()
                val prior = encoder.run(c.originalMel!!)
                val random = Random(c.seed)
                c.state = FloatArray(LATENT) { i -> 3f * prior[i] + 0.5f * random.nextGaussian().toFloat() }
                c.random = random
                c.originalMel = null
                step(COST_ENCODER)
            }
        }
        withModel(FLOW) { flow ->
            for (c in active) {
                val condition = c.condition!!
                c.state = MidpointSolver.integrate(c.state!!) { value, time -> flow.run(value, condition, floatArrayOf(time)) }
                c.condition = null
                step(COST_FLOW)
            }
        }
        withModel(DECODER) { decoder ->
            for (c in active) {
                throwIfCancelled()
                val state = c.state!!
                c.decoded = decoder.run(FloatArray(LATENT) { state[it] / 6f })
                c.state = null
                step(COST_DECODER)
            }
        }
        val output = DoubleArray(samples.size)
        val weights = DoubleArray(samples.size)
        val window = FloatArray(size) { i -> maxOf(1e-3f, (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat()) }
        withModel(VOCODER) { vocoder ->
            for (c in active) {
                throwIfCancelled()
                val random = c.random!!
                val noise = FloatArray(VOCODER_NOISE) { random.nextGaussian().toFloat() }
                val enhanced = vocoder.run(c.decoded!!, noise)
                for (i in 0 until c.count) output[c.start + i] += enhanced[i] * c.peak * window[i]
                c.decoded = null
                step(COST_VOCODER)
            }
        }
        // Silent chunks contribute zeros but still count toward the overlap weights.
        for (c in chunks) for (i in 0 until c.count) weights[c.start + i] += window[i].toDouble()
        return FloatArray(samples.size) { (output[it] / weights[it]).toFloat() }
    }

    /** Models are opened per stage and closed immediately, so nothing is held between calls. */
    override fun close() = Unit
}
