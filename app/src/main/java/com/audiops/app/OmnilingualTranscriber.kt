package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/**
 * Omnilingual ASR 300M (CTC, INT8), running locally through sherpa-onnx. One model for every
 * language except Bangla, which uses [BanglaTranscriber]. The model detects the language
 * itself, so there is no language setting. Apache License 2.0 (Meta Platforms, Inc.).
 */
internal object OmnilingualTranscriber {
    private const val REVISION = "6abf1ece20cd2308bdb7d13cd78ec1c44fa4c094"
    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/resolve/$REVISION"
    private const val SECTION_SECONDS = 20
    private val digests = mapOf(
        "model.int8.onnx" to "e7c4e54ee4c4c47829cc6667d5d00ed8ea7bef1dcfeef0fce766f77752a2726c",
        "tokens.txt" to "a7a044c52cb29cbe8b0dc1953e92cefd4ca16b0ed968177b6beab21f9a7d0b31",
    )

    fun transcribe(context: Context, audio: File, listener: ProcessingListener): String {
        val directory = ModelFiles.ensure(context.noBackupFilesDir, "omnilingual-asr/$REVISION", BASE_URL, digests, listener)
        fun path(name: String) = File(directory, name).absolutePath
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = path("model.int8.onnx")),
                    tokens = path("tokens.txt"),
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
