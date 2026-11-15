package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * NVIDIA Parakeet TDT 0.6B v3 (INT8), running locally through sherpa-onnx. English and 24 other
 * European languages, with punctuation and capital letters. In our benchmark (FLEURS, 25 clips
 * per language) it made 2.5 to 4 times fewer errors than Omnilingual on English, French, Spanish
 * and German, and ran about 1.7 times faster. CC BY 4.0 (NVIDIA).
 */
internal object ParakeetTranscriber {
    private const val REVISION = "ed5d745623472f97cfc2e6c2916ee248798fc5a8"
    private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/$REVISION"
    private const val SECTION_SECONDS = 20
    private val digests = mapOf(
        "encoder.int8.onnx" to "1afbe69c1ffdabee94755f42e4daf1d93549131a3da11356bda84fcef0f2c479",
        "decoder.int8.onnx" to "0222a86147299d4dee90edf95b6d13827edb42ad8b7c9cf0b2582f030937c0b5",
        "joiner.int8.onnx" to "cbd02f3696e9944970111fb439d9c3fd365a80374565f13f2b9c7007d253fc98",
        "tokens.txt" to "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
    )

    /** Whisper language codes of the languages Parakeet v3 was trained on. */
    val languages = setOf(
        "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hr", "hu", "it",
        "lt", "lv", "mt", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "uk",
    )

    fun transcribe(context: Context, audio: File, listener: ProcessingListener): String {
        val directory = ModelFiles.ensure(context.noBackupFilesDir, "parakeet-tdt-v3/$REVISION", BASE_URL, digests, listener)
        fun path(name: String) = File(directory, name).absolutePath
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = path("encoder.int8.onnx"),
                        decoder = path("decoder.int8.onnx"),
                        joiner = path("joiner.int8.onnx"),
                    ),
                    tokens = path("tokens.txt"),
                    modelType = "nemo_transducer",
                    numThreads = 4,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )
        try {
            val sections = AudioChunks.sectionCount(audio, SECTION_SECONDS)
            val parts = mutableListOf<String>()
            var index = 0
            AudioChunks.forEachChunk(audio, SECTION_SECONDS) { samples ->
                throwIfCancelled()
                listener.onSection(++index, sections)
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, PIPELINE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
                } finally {
                    stream.release()
                }
            }
            return parts.joinToString(" ")
        } finally {
            recognizer.release()
        }
    }
}
