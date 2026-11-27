package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import java.io.File

/**
 * The user-facing audio pipelines. Both are blocking and must run on a worker thread;
 * both stop promptly when that thread is interrupted (see [throwIfCancelled]).
 */
internal object AudioPipelines {
    private const val ENHANCE_DTYPE = "mobile"

    /** Resemble Enhance (LiteRT) pinned to one commit; every file is verified by SHA-256. */
    private const val ENHANCE_REVISION = "b92eaaee528795690ac1a06ba259baa4cc1134b5"
    private const val ENHANCE_URL = "https://huggingface.co/SayedShaun/resemble-enhance-litert/resolve/$ENHANCE_REVISION/models"
    private val ENHANCE_DIGESTS = linkedMapOf(
        "mel-filters.f32" to "4e98aecc53c8812470979ce83ac77069ca681f9e4c3b0762ad5abfbc908ca791",
        "denoiser-net-int8-dynamic.tflite" to "bb3657bbccb31a70e3f384239ba8deea9466298688b163dc22474bf815daf47a",
        "encoder-int8-dynamic.tflite" to "94c5184a224b9206d3949e454889296d736c6e21e57afded04ea0ec925751700",
        "flow-step-int8-dynamic.tflite" to "f1a6174b38f4cef29851853138f0d9755f0f3a5d9d29d381f9a8c2ff0be52160",
        "decoder-int8-dynamic.tflite" to "33e8b0729cc8b7361bd79bc6083cbf60e042fa02f86f32fc357a8b9b7649bf7a",
        "vocoder-mobile-fp16.tflite" to "92f270e59a9b066d6b23114fbaf42744025eb655371161d222fe1ef4a4abb90d",
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
            val cache = File(context.noBackupFilesDir, "xnnpack-cache")
            // Weight caches are keyed by revision; drop those of older model revisions.
            cache.listFiles()?.filter { !it.name.startsWith("$ENHANCE_REVISION-") }?.forEach { it.delete() }
            ResembleEnhancer(models, ENHANCE_DTYPE, cache).use { model ->
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

    /**
     * Transcribes decoded [audio] with the model the user picked. [language] is a Whisper code and
     * only [TranscriptionModel.WHISPER] uses it; the other models work out the language themselves.
     * [TranscriptionModel.AUTO] exists only for history saved by earlier versions and is treated
     * as Parakeet.
     */
    fun transcribe(context: Context, audio: File, language: String, model: TranscriptionModel, listener: ProcessingListener): Transcript =
        when (model) {
            TranscriptionModel.PARAKEET, TranscriptionModel.AUTO -> ParakeetTranscriber.transcribe(context, audio, listener)
            TranscriptionModel.OMNILINGUAL -> OmnilingualTranscriber.transcribe(context, audio, listener)
            TranscriptionModel.WHISPER -> WhisperTranscriber.transcribe(context, audio, language, listener)
            TranscriptionModel.BANGLA -> BanglaTranscriber.transcribe(context, audio, listener)
        }

    /**
     * Transcribes decoded [audio] utterance by utterance (cut at pauses by Silero VAD) and, with
     * [speakers], tells the speakers apart by their voices. Returns the lines and each speaker's voice.
     */
    fun transcribeUtterances(
        context: Context,
        audio: File,
        language: String,
        model: TranscriptionModel,
        speakers: Boolean,
        listener: ProcessingListener,
    ): Pair<List<MeetingLine>, List<FloatArray?>> {
        val vad = MeetingModels.vad(context, listener)
        try {
            throwIfCancelled()
            val extractor = if (speakers) MeetingModels.speakerExtractor(context, listener) else null
            try {
                throwIfCancelled()
                val recognizer = recognizer(context, model, language, listener)
                try {
                    val transcriber = MeetingTranscriber(
                        recognizer = { samples -> recognizeUtterance(recognizer, samples) },
                        embedder = { samples -> extractor?.let { embedUtterance(it, samples) } },
                    )
                    val lines = SpeechTranscription.lines(audio, VadDetector(vad), transcriber, listener)
                    return lines to transcriber.voices(lines)
                } finally {
                    recognizer.release()
                }
            } finally {
                extractor?.release()
            }
        } finally {
            vad.release()
        }
    }

    /** A recognizer for single utterances with [model] (live meetings). Release it after use. */
    fun recognizer(context: Context, model: TranscriptionModel, language: String, listener: ProcessingListener): OfflineRecognizer =
        when (model) {
            TranscriptionModel.PARAKEET, TranscriptionModel.AUTO -> ParakeetTranscriber.recognizer(context, listener)
            TranscriptionModel.OMNILINGUAL -> OmnilingualTranscriber.recognizer(context, listener)
            TranscriptionModel.WHISPER -> WhisperTranscriber.recognizer(context, language, listener)
            TranscriptionModel.BANGLA -> BanglaTranscriber.recognizer(context, listener)
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
