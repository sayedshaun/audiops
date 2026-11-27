package com.audiops.app

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.Vad

/** Transcribes one utterance (16 kHz). */
internal fun recognizeUtterance(recognizer: OfflineRecognizer, samples: FloatArray): String {
    val stream = recognizer.createStream()
    try {
        stream.acceptWaveform(samples, MEETING_RATE)
        recognizer.decode(stream)
        return recognizer.getResult(stream).text
    } finally {
        stream.release()
    }
}

/** The voice embedding of one utterance (16 kHz), or null when it is too short. */
internal fun embedUtterance(extractor: SpeakerEmbeddingExtractor, samples: FloatArray): FloatArray? {
    val stream = extractor.createStream()
    try {
        stream.acceptWaveform(samples, MEETING_RATE)
        stream.inputFinished()
        return if (extractor.isReady(stream)) extractor.compute(stream) else null
    } finally {
        stream.release()
    }
}

/** Silero VAD as a [SpeechDetector]. */
internal class VadDetector(private val vad: Vad) : SpeechDetector {
    override fun accept(samples: FloatArray): List<Utterance> {
        if (samples.isNotEmpty()) vad.acceptWaveform(samples)
        return drain()
    }

    override fun flush(): List<Utterance> {
        vad.flush()
        return drain()
    }

    private fun drain(): List<Utterance> {
        val found = mutableListOf<Utterance>()
        while (!vad.empty()) {
            val segment = vad.front()
            found += Utterance(segment.start.toLong(), segment.samples)
            vad.pop()
        }
        return found
    }
}
