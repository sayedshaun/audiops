package com.audiops.app

import android.content.Context
import java.io.File

/**
 * The user-facing audio pipelines. Both are blocking and must run on a worker thread;
 * both stop promptly when that thread is interrupted (see [throwIfCancelled]).
 */
internal object AudioPipelines {
    private const val ENHANCE_REPO = "SayedShaun/resemble-enhance-litert"
    private const val ENHANCE_DTYPE = "int8"
    private const val CROSSFADE_SAMPLES = PIPELINE_RATE

    /**
     * Enhances decoded [audio] (mono 44.1 kHz PCM16) into the WAV file [result]. On failure
     * or cancellation [result] is deleted.
     */
    fun enhance(context: Context, audio: File, result: File, listener: ProcessingListener) {
        try {
            val sections = AudioChunks.enhancementSectionCount(audio)
            ResembleEnhancer(context, ENHANCE_REPO, ENHANCE_DTYPE, listener).use { model ->
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
