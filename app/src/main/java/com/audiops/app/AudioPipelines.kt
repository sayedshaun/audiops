package com.audiops.app

import android.content.Context
import android.net.Uri
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
     * Enhances [input] and returns a WAV file in the cache directory. The caller owns the
     * returned file and must delete it once it is no longer needed.
     */
    fun enhance(context: Context, input: Uri, listener: ProcessingListener): File {
        val audio = AudioDecoder.decode(context, input)
        try {
            val result = File.createTempFile("enhanced-", ".wav", context.cacheDir)
            try {
                val sections = AudioDecoder.enhancementSectionCount(audio)
                ResembleEnhancer(context, ENHANCE_REPO, ENHANCE_DTYPE, listener).use { model ->
                    WavWriter(result).use { writer ->
                        var tail: FloatArray? = null
                        var section = 0
                        AudioDecoder.forEachEnhancementChunk(audio) { samples, last ->
                            section++
                            listener.onSection(section, sections)
                            val enhanced = model.enhance(samples)
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
                return result
            } catch (e: Throwable) {
                result.delete()
                throw e
            }
        } finally {
            audio.delete()
        }
    }

    /** Transcribes [input]. [language] is `bn` for Bangla (dedicated model) or a Whisper code, or empty for auto. */
    fun transcribe(context: Context, input: Uri, language: String, listener: ProcessingListener): String {
        val audio = AudioDecoder.decode(context, input)
        try {
            return if (language == "bn") {
                BanglaTranscriber.transcribe(context, audio, listener)
            } else {
                WhisperTranscriber.transcribe(context, audio, language, listener)
            }
        } finally {
            audio.delete()
        }
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
