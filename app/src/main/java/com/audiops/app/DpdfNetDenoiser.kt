package com.audiops.app

import android.content.Context
import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.ChunkedEnhancer
import com.audiops.app.dsp.ResampledProcessor
import com.audiops.app.dsp.runInWholeFrames
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserDpdfNetModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import java.io.File

/**
 * AI Noise reduction with DPDFNet (dpdfnet2, 48 kHz high-resolution; Ceva, Apache 2.0), running
 * through sherpa-onnx. The model downloads once and is verified by SHA-256.
 *
 * sherpa-onnx's offline denoiser takes a whole buffer, so long recordings run in 20-second
 * chunks with 4 seconds of history (the recurrent layers restart each call) and 0.2 seconds of
 * lookahead. Strength sets DPDFNet's attenuation limit: Light 9 dB, Medium 18 dB, Strong none.
 */
internal object DpdfNetDenoiser {
    private const val BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speech-enhancement-models"
    private const val MODEL = "dpdfnet2_48khz_hr.onnx"
    private const val DIGEST = "0b399f8a58dc4d70d8cd97541f5c39869406145193b957d00a03b66070944928"

    /** Folder per model digest, so a model update replaces the old file (see [ModelFiles.ensure]). */
    private const val FOLDER = "dpdfnet/0b399f8a58dc"
    private const val MODEL_RATE = 48_000
    private const val BODY = 20 * MODEL_RATE
    private const val WARMUP = 4 * MODEL_RATE
    private const val LOOKAHEAD = MODEL_RATE / 5

    /** DPDFNet works in 10 ms frames and drops a partial last frame. */
    private const val HOP = MODEL_RATE / 100

    fun processor(context: Context, strength: Float, listener: ProcessingListener): AudioProcessor {
        val directory = ModelFiles.ensure(context.noBackupFilesDir, FOLDER, BASE_URL, mapOf(MODEL to DIGEST), listener)
        val denoiser = OfflineSpeechDenoiser(
            config = OfflineSpeechDenoiserConfig(
                model = OfflineSpeechDenoiserModelConfig(
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig(
                        model = File(directory, MODEL).absolutePath,
                        attenuationLimitDb = attenuationLimitDb(strength),
                    ),
                    numThreads = 4,
                ),
            ),
        )
        val chunked = ChunkedEnhancer(BODY, WARMUP, LOOKAHEAD) { samples ->
            runInWholeFrames(samples, HOP) { denoiser.run(it, MODEL_RATE).samples }
        }
        return ReleasingProcessor(ResampledProcessor(MODEL_RATE, chunked)) { denoiser.release() }
    }

    /** 0 disables the limit (all removable noise goes). */
    fun attenuationLimitDb(strength: Float): Float = when {
        strength >= 0.8f -> 0f
        strength >= 0.5f -> 18f
        else -> 9f
    }

    /** Releases the native denoiser once the stream ends. */
    private class ReleasingProcessor(private val inner: AudioProcessor, private val release: () -> Unit) : AudioProcessor {
        override fun process(input: FloatArray): FloatArray = inner.process(input)

        override fun flush(): FloatArray = try {
            inner.flush()
        } finally {
            release()
        }
    }
}
