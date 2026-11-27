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
    /**
     * Feeds the VAD in small blocks, as the microphone does in a live meeting, collecting finished
     * utterances after each: given a long block at once, it keeps only part of the speech in it.
     */
    override fun accept(samples: FloatArray): List<Utterance> {
        val found = mutableListOf<Utterance>()
        var from = 0
        while (from < samples.size) {
            val to = minOf(from + BLOCK, samples.size)
            vad.acceptWaveform(samples.copyOfRange(from, to))
            found += drain()
            from = to
        }
        return found
    }

    override fun flush(): List<Utterance> {
        vad.flush()
        return drain()
    }

    private companion object {
        /** 0.1 s, the block size of a live meeting's capture. */
        const val BLOCK = MEETING_RATE / 10
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
