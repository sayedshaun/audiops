package com.audiops.app

import android.content.Context
import java.io.File

/**
 * The user-facing audio pipelines. Both are blocking and must run on a worker thread;
 * both stop promptly when that thread is interrupted (see [throwIfCancelled]).
 */
internal object AudioPipelines {
    private const val ENHANCE_DTYPE = "int8"

    /** Resemble Enhance (LiteRT) pinned to one commit; every file is verified by SHA-256. */
    private const val ENHANCE_REVISION = "d74080faa9ca2e096e095d233d860f6288c718a3"
    private const val ENHANCE_URL = "https://huggingface.co/SayedShaun/resemble-enhance-litert/resolve/$ENHANCE_REVISION/models"
    private val ENHANCE_DIGESTS = linkedMapOf(
        "mel-filters.f32" to "4e98aecc53c8812470979ce83ac77069ca681f9e4c3b0762ad5abfbc908ca791",
        "denoiser-net-int8-dynamic.tflite" to "bb3657bbccb31a70e3f384239ba8deea9466298688b163dc22474bf815daf47a",
        "encoder-int8-dynamic.tflite" to "94c5184a224b9206d3949e454889296d736c6e21e57afded04ea0ec925751700",
        "flow-step-int8-dynamic.tflite" to "f1a6174b38f4cef29851853138f0d9755f0f3a5d9d29d381f9a8c2ff0be52160",
        "decoder-int8-dynamic.tflite" to "33e8b0729cc8b7361bd79bc6083cbf60e042fa02f86f32fc357a8b9b7649bf7a",
        "vocoder-int8-weights.tflite" to "1e697a7d26892992f5f99b05f6271b273ba3f143bba2d2e2bac82d7aefc01dba",
    )
    private const val CROSSFADE_SAMPLES = PIPELINE_RATE

    /**
     * Enhances decoded [audio] (mono 44.1 kHz PCM16) into the WAV file [result]. On failure
     * or cancellation [result] is deleted.
     */
    fun enhance(context: Context, audio: File, result: File, listener: ProcessingListener) {
        try {
            val sections = AudioChunks.enhancementSectionCount(audio)
            val models = ModelFiles.ensure(
                context.noBackupFilesDir,
                "resemble-enhance/$ENHANCE_REVISION",
                ENHANCE_URL,
                ENHANCE_DIGESTS,
                listener,
            )
            ResembleEnhancer(models, ENHANCE_DTYPE).use { model ->
                WavWriter(result).use { writer ->
                    var tail: FloatArray? = null
                    var section = 0
                    AudioChunks.forEachEnhancementChunk(audio) { samples, last ->
                        section++
                        val current = section
                        val enhanced = model.enhance(samples) { done, total ->
                            // One 1..100 scale across all windows and stages.
                            val within = if (total == 0) 1.0 else done.toDouble() / total
                            listener.onSection((1 + (current - 1 + within) * 99 / sections).toInt().coerceIn(1, 100), 100)
                        }
                        val previous = tail
                        val joined = if (previous == null) enhanced else crossfade(previous, enhanced)
                        if (last) {
                            writer.append(joined)
                            tail = null
                        } else {
                            // Hold back the overlap; the next window starts with it and crossfades.
                            val keep = minOf(CROSSFADE_SAMPLES, joined.size)
                            writer.append(joined.copyOfRange(0, joined.size - keep))
                            tail = joined.copyOfRange(joined.size - keep, joined.size)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            result.delete()
            throw e
        }
    }

    /** Transcribes decoded [audio]. [language] is `bn` for Bangla (dedicated model), a Whisper code, or empty for auto. */
    fun transcribe(context: Context, audio: File, language: String, listener: ProcessingListener): String = if (language == "bn") {
        BanglaTranscriber.transcribe(context, audio, listener)
    } else {
        WhisperTranscriber.transcribe(context, audio, language, listener)
    }

    private fun crossfade(previous: FloatArray, enhanced: FloatArray): FloatArray {
        val overlap = minOf(previous.size, enhanced.size)
        return FloatArray(enhanced.size) { index ->
            if (index < overlap) {
                val weight = (index + 1).toFloat() / (overlap + 1)
                previous[index] * (1f - weight) + enhanced[index] * weight
            } else {
                enhanced[index]
            }
        }
    }
}
