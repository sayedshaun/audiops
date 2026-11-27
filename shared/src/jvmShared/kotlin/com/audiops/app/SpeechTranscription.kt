package com.audiops.app

import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import java.io.File

/** One stretch of speech: [samples] (16 kHz) starting at sample [startSample] of the recording. */
class Utterance(val startSample: Long, val samples: FloatArray)

/** Finds utterances in 16 kHz audio fed to it in order (a voice activity detector). */
interface SpeechDetector {
    /** Adds audio; returns the utterances that have ended. */
    fun accept(samples: FloatArray): List<Utterance>

    /** Ends the audio; returns what is left. */
    fun flush(): List<Utterance>
}

/**
 * Transcribes a recording utterance by utterance, the way a live meeting does: a voice activity
 * detector cuts it at pauses ([UtterancePadding] widens each cut a little), and [MeetingTranscriber]
 * transcribes each utterance and, when it has an embedder, tells the speakers apart. Cutting at pauses means no word is split between two
 * sections, and every utterance can become a sentence ([SpeechSentences]).
 */
object SpeechTranscription {
    private const val SECTION_SECONDS = 10

    /**
     * Runs over decoded [audio] (44.1 kHz mono PCM16) and returns the lines, with speakers worked out
     * from every utterance at once. Reports progress per section and stops when cancelled.
     */
    fun lines(audio: File, detector: SpeechDetector, transcriber: MeetingTranscriber, listener: ProcessingListener): List<MeetingLine> {
        val sections = AudioChunks.sectionCount(audio, SECTION_SECONDS)
        val down = ProcessorChain(
            listOf(FilterChain.antiAlias(PIPELINE_RATE, MEETING_RATE), Resampler(PIPELINE_RATE.toDouble() / MEETING_RATE)),
        )
        val padding = UtterancePadding()
        fun feed(samples: FloatArray) = padding.add(samples, detector.accept(samples)).forEach { add(transcriber, it) }
        var index = 0
        AudioChunks.forEachChunk(audio, SECTION_SECONDS) { chunk ->
            listener.onSection(++index, sections)
            feed(down.process(chunk))
        }
        feed(down.flush())
        padding.flush(detector.flush()).forEach { add(transcriber, it) }
        return transcriber.finish()
    }

    private fun add(transcriber: MeetingTranscriber, utterance: Utterance) {
        throwIfCancelled()
        transcriber.add(utterance.startSample, utterance.samples)
    }

    /** Plain text and subtitles, one subtitle per utterance (split when long), without speakers. */
    fun plain(lines: List<MeetingLine>, language: String): Transcript = Transcript(
        SpeechSentences.join(lines, language),
        Cues.sequential(lines.flatMap { Cues.forSection(it.startMs, it.endMs - it.startMs, it.text, emptyList(), emptyList()) }),
    )
}
