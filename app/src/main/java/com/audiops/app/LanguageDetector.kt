package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import java.io.File

/**
 * Detects the spoken language from the start of a recording with Whisper Tiny's language
 * identification (the same files as [WhisperTranscriber]). On FLEURS it was right for 24 or
 * 25 of 25 clips in Bangla, Hindi, English, Arabic, French and Spanish, but read most Urdu as
 * Hindi. The detector is released straight after use.
 */
internal object LanguageDetector {
    private const val SECONDS = 30

    /** A Whisper language code such as `en` or `bn`, or empty when detection fails. */
    fun detect(context: Context, audio: File, listener: ProcessingListener): String {
        val directory = WhisperTranscriber.files(context, listener)
        throwIfCancelled()
        val detector = SpokenLanguageIdentification(
            config = SpokenLanguageIdentificationConfig(
                whisper = SpokenLanguageIdentificationWhisperConfig(
                    encoder = File(directory, "encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "decoder.int8.onnx").absolutePath,
                    tailPaddings = 1000,
                ),
                numThreads = 4,
                debug = false,
                provider = "cpu",
            ),
        )
        try {
            val stream = detector.createStream()
            try {
                stream.acceptWaveform(AudioChunks.firstChunk(audio, SECONDS), PIPELINE_RATE)
                return detector.compute(stream).trim()
            } finally {
                stream.release()
            }
        } finally {
            detector.release()
        }
    }
}
