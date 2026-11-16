package com.audiops.app.dsp

import com.audiops.app.PIPELINE_RATE
import com.audiops.app.ProcessingListener
import com.audiops.app.throwIfCancelled

/**
 * An AI noise reducer supplied by the platform (DPDFNet on Android). When the engine has one,
 * Noise reduction uses it instead of spectral subtraction.
 */
fun interface AiDenoiser {
    /** A processor at [PIPELINE_RATE] for Noise reduction [strength] (0..1). May download its model first. */
    fun processor(strength: Float, listener: ProcessingListener): AudioProcessor
}

/**
 * Runs a whole-buffer enhancer on a long stream with bounded memory. Each call gets [warmup]
 * samples of history (so a recurrent model settles), [body] samples that are kept, and
 * [lookahead] samples of future context; only the body of the result is output. The first
 * chunk has no history, and the stream end is processed with what remains.
 *
 * [enhance] must return as many samples as it is given.
 */
class ChunkedEnhancer(
    private val body: Int,
    private val warmup: Int,
    private val lookahead: Int,
    private val enhance: (FloatArray) -> FloatArray,
) : AudioProcessor {
    private var buffer = FloatArray(warmup + body + lookahead)
    private var size = 0 // samples in buffer
    private var history = 0 // leading samples of buffer that are history, not yet-to-output body

    init {
        require(body > 0 && warmup >= 0 && lookahead >= 0)
    }

    override fun process(input: FloatArray): FloatArray {
        append(input)
        val out = SampleSink(input.size + body)
        while (size - history >= body + lookahead) {
            runChunk(body, out)
        }
        return out.toArray()
    }

    override fun flush(): FloatArray {
        val out = SampleSink(size)
        while (size > history) {
            runChunk(minOf(body, size - history), out)
        }
        return out.toArray()
    }

    /** Enhances history + [count] body samples + available lookahead, outputs the body, keeps new history. */
    private fun runChunk(count: Int, out: SampleSink) {
        throwIfCancelled()
        val end = minOf(size, history + count + lookahead)
        val result = enhance(buffer.copyOf(end))
        check(result.size == end) { "enhancer returned ${result.size} samples for $end" }
        for (i in history until history + count) out.add(result[i])
        // The next chunk's history is the last [warmup] samples before its body.
        val consumed = history + count
        val keepFrom = maxOf(0, consumed - warmup)
        buffer.copyInto(buffer, 0, keepFrom, size)
        size -= keepFrom
        history = consumed - keepFrom
    }

    private fun append(input: FloatArray) {
        if (size + input.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + input.size))
        input.copyInto(buffer, size)
        size += input.size
    }
}

/**
 * Wraps a processor that works at [modelRate]: resamples the pipeline audio to that rate,
 * processes it, and resamples back (with an anti-alias filter when going down).
 */
class ResampledProcessor(private val modelRate: Int, private val inner: AudioProcessor) : AudioProcessor {
    private val up = Resampler(PIPELINE_RATE.toDouble() / modelRate)
    private val down = ProcessorChain(
        listOfNotNull(
            if (modelRate > PIPELINE_RATE) FilterChain.antiAlias(modelRate, PIPELINE_RATE) else null,
            Resampler(modelRate.toDouble() / PIPELINE_RATE),
        ),
    )

    override fun process(input: FloatArray): FloatArray = down.process(inner.process(up.process(input)))

    override fun flush(): FloatArray {
        val tail = inner.process(up.flush())
        val rest = inner.flush()
        return down.process(if (rest.isEmpty()) tail else tail + rest) + down.flush()
    }
}
