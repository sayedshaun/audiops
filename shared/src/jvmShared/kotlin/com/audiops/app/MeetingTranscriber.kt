package com.audiops.app

/** Speech to text for one utterance (16 kHz mono samples). */
fun interface SegmentRecognizer {
    fun recognize(samples: FloatArray): String
}

/** A voice embedding for one utterance, or null when it is too short to tell the voice. */
fun interface SpeakerEmbedder {
    fun embed(samples: FloatArray): FloatArray?
}

/**
 * Turns the utterances a voice activity detector cuts from a meeting into transcript lines with
 * speakers. Not thread-safe: one worker thread calls [add], [partial] and [finish] in turn.
 */
class MeetingTranscriber(
    private val recognizer: SegmentRecognizer,
    private val embedder: SpeakerEmbedder,
    private val sampleRate: Int = MEETING_RATE,
) {
    private val clusters = SpeakerClusters()
    private val lines = mutableListOf<MeetingLine>()
    private val embeddings = mutableListOf<FloatArray?>()

    /** Final lines so far, oldest first. */
    val transcript: List<MeetingLine> get() = lines.toList()

    /** Speakers heard so far. */
    val speakers: Int get() = lines.map { it.speaker }.distinct().size

    /**
     * Transcribes the utterance that started at [startSample] and returns its line, or null when it
     * held no words (a cough, a door).
     */
    fun add(startSample: Long, samples: FloatArray): MeetingLine? {
        val text = recognizer.recognize(samples).trim()
        if (text.isEmpty()) return null
        val embedding = if (samples.size >= sampleRate * MIN_EMBED_SECONDS) embedder.embed(samples) else null
        val line = MeetingLine(
            startMs = startSample * 1000 / sampleRate,
            endMs = (startSample + samples.size) * 1000 / sampleRate,
            speaker = clusters.assign(embedding),
            text = text,
        )
        lines += line
        embeddings += embedding
        return line
    }

    /** What has been said so far in an utterance that is still going on. */
    fun partial(samples: FloatArray): String = recognizer.recognize(samples).trim()

    /** The final transcript, with speakers worked out again from every utterance at once. */
    fun finish(): List<MeetingLine> {
        if (lines.isEmpty() || embeddings.size > SpeakerClusters.MAX_RECLUSTER) return renumbered(lines)
        val speakers = SpeakerClusters.recluster(embeddings)
        return lines.mapIndexed { index, line -> line.copy(speaker = speakers[index]) }
    }

    private fun renumbered(lines: List<MeetingLine>): List<MeetingLine> {
        val numbers = SpeakerClusters.renumber(lines.map { it.speaker }.toIntArray())
        return lines.mapIndexed { index, line -> line.copy(speaker = numbers[index]) }
    }

    companion object {
        /** Utterances shorter than this give unreliable voice embeddings. */
        const val MIN_EMBED_SECONDS = 1
    }
}

/** Live meetings are captured and processed at the speech models' rate. */
const val MEETING_RATE = 16_000
