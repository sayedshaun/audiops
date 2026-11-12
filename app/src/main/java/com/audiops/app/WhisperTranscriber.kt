package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/** Whisper Tiny INT8, running locally through sherpa-onnx. */
internal object WhisperTranscriber {
    private const val REVISION = "1f6243ec6dffc4de2db84fe1446576d1ff52c252"
    private const val BASE_URL = "https://huggingface.co/ippocode/indic-asr-onnx/resolve/$REVISION/models/whisper-tiny"
    private const val SECTION_SECONDS = 30
    private val digests = mapOf(
        "encoder.int8.onnx" to "d71c2315088f23b10b9eba8eaa3e7b542475aad969be93d9e248759ed2677605",
        "decoder.int8.onnx" to "4f35a102081f83cb7b5d2c3f499fff8a04965b06ccf798c34cba6fb9a122701a",
        "tokens.txt" to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
    )

    /** [language] is a Whisper language code, or empty for automatic detection. */
    fun transcribe(context: Context, audio: File, language: String, listener: ProcessingListener): String {
        val directory = ModelFiles.ensure(context, "whisper-tiny/$REVISION", BASE_URL, digests, listener)
        fun path(name: String) = File(directory, name).absolutePath
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = path("encoder.int8.onnx"),
                        decoder = path("decoder.int8.onnx"),
                        language = language,
                        task = "transcribe",
                        tailPaddings = 1000,
                    ),
                    tokens = path("tokens.txt"),
                    numThreads = 4,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )
        try {
            val sections = AudioDecoder.sectionCount(audio, SECTION_SECONDS)
            val parts = mutableListOf<String>()
            var index = 0
            AudioDecoder.forEachChunk(audio, SECTION_SECONDS) { samples ->
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
