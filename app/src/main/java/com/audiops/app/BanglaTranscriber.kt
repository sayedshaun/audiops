package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * Bangla Zipformer INT8, by SayedShaun (CC BY-SA 4.0). See licenses/ and the model card
 * for attribution.
 */
internal object BanglaTranscriber {
    private const val REVISION = "5442ca3d447572aaa5ef6913684d8f24d6d916cc"
    private const val BASE_URL = "https://huggingface.co/SayedShaun/bangla-zipformer-onnx/resolve/$REVISION"
    private const val SECTION_SECONDS = 20
    private val digests = mapOf(
        "int8/encoder.onnx" to "43d5bdfc8e956c8137b8652fca347a4deb8ae6742cc0a2840db47c02a5c4cd4a",
        "int8/decoder.onnx" to "a183a765346a050c8577dc947cb96e69beecb8a83a73ebef48ad0cdd387e18df",
        "int8/joiner.onnx" to "bd9d5b3a02b0550fc09e55f222dce6ac72d38396b5c5087c462d67d0cb840fb4",
        "tokens.txt" to "8edb5b3ef2587207c86cc3966f3f729ec3845250d3b040e5ba916ce935fcae2b",
    )

    fun transcribe(context: Context, audio: File, listener: ProcessingListener): String {
        val directory = ModelFiles.ensure(context, "bangla-zipformer/$REVISION", BASE_URL, digests, listener)
        fun path(name: String) = File(directory, name).absolutePath
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = path("int8/encoder.onnx"),
                        decoder = path("int8/decoder.onnx"),
                        joiner = path("int8/joiner.onnx"),
                    ),
                    tokens = path("tokens.txt"),
                    numThreads = 4,
                    provider = "cpu",
                ),
                decodingMethod = "modified_beam_search",
                maxActivePaths = 4,
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
