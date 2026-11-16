package com.audiops.app

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import java.io.File

/**
 * Runs [recognizer] over [audio] in sections of [sectionSeconds] (constant memory), reporting
 * progress and honouring Cancel between sections. Returns the text and, from each section's
 * token times, the subtitle cues. Every sherpa-onnx transcriber uses this loop.
 */
internal fun transcribeSections(audio: File, recognizer: OfflineRecognizer, sectionSeconds: Int, listener: ProcessingListener): Transcript {
    val sections = AudioChunks.sectionCount(audio, sectionSeconds)
    val texts = mutableListOf<String>()
    val cues = mutableListOf<Cue>()
    var index = 0
    AudioChunks.forEachChunk(audio, sectionSeconds) { samples ->
        throwIfCancelled()
        listener.onSection(++index, sections)
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, PIPELINE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val text = result.text.trim()
            if (text.isNotEmpty()) {
                texts += text
                cues += Cues.forSection(
                    offsetMs = (index - 1) * sectionSeconds * 1000L,
                    durationMs = samples.size * 1000L / PIPELINE_RATE,
                    text = text,
                    tokens = result.tokens.toList(),
                    timestamps = result.timestamps.toList(),
                )
            }
        } finally {
            stream.release()
        }
    }
    return Transcript(texts.joinToString(" "), cues)
}
