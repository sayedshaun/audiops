package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.security.MessageDigest

/** Whisper Tiny INT8, running locally through sherpa-onnx. */
internal object WhisperTranscriber {
    private const val REVISION = "1f6243ec6dffc4de2db84fe1446576d1ff52c252"
    private val hashes = mapOf(
        "encoder.int8.onnx" to "d71c2315088f23b10b9eba8eaa3e7b542475aad969be93d9e248759ed2677605",
        "decoder.int8.onnx" to "4f35a102081f83cb7b5d2c3f499fff8a04965b06ccf798c34cba6fb9a122701a",
        "tokens.txt" to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
    )

    fun transcribe(context: Context, audio: File, language: String): String {
        val directory = File(context.noBackupFilesDir, "whisper-tiny/$REVISION")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create model storage." }
        for ((name, hash) in hashes) {
            val file = File(directory, name)
            file.parentFile?.mkdirs()
            if (!file.isFile || sha256(file) != hash) {
                file.delete()
                HubModels.download("https://huggingface.co/ippocode/indic-asr-onnx/resolve/$REVISION/models/whisper-tiny/$name", file)
                if (sha256(file) != hash) {
                    file.delete()
                    error("Model file verification failed: $name")
                }
            }
        }
        fun path(name: String) = File(directory, name).absolutePath
        val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = path("encoder.int8.onnx"), decoder = path("decoder.int8.onnx"),
                    language = language, task = "transcribe", tailPaddings = 1000
                ), tokens = path("tokens.txt"), numThreads = 4, provider = "cpu"
            ), decodingMethod = "greedy_search"
        ))
        try {
            val parts = mutableListOf<String>()
            AudioDecoder.forEachChunk(audio, 30) { samples ->
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, 44_100)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
                } finally { stream.release() }
            }
            return parts.joinToString(" ")
        } finally { recognizer.release() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
