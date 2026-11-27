package com.audiops.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What a transcript with speakers is made from, kept next to it (`<name>.speakers.json`) so the
 * speakers can be named afterwards: the [lines], each speaker's [voices] (a unit-length voice
 * embedding, or null when they spoke too briefly) and the [names] given so far (null = "Speaker N").
 */
@Serializable
data class SpeakerTranscript(val lines: List<MeetingLine>, val voices: List<List<Float>?>, val names: List<String?>) {
    val speakers: Int get() = voices.size

    /** The name shown for [speaker] (0-based); [defaultName] names unnamed speakers (1-based). */
    fun nameOf(speaker: Int, defaultName: (Int) -> String): String = names.getOrNull(speaker) ?: defaultName(speaker + 1)

    fun transcript(defaultName: (Int) -> String): Transcript {
        val name: (Int) -> String = { number -> nameOf(number - 1, defaultName) }
        return Transcript(MeetingDocument.text(lines, name), MeetingDocument.cues(lines, name))
    }

    fun info(): List<SpeakerInfo> = List(speakers) { speaker ->
        SpeakerInfo(
            name = names.getOrNull(speaker),
            sample = lines.firstOrNull { it.speaker == speaker }?.text.orEmpty().take(SAMPLE_LENGTH),
            hasVoice = voices[speaker] != null,
        )
    }

    fun write(file: File) = file.writeText(json.encodeToString(serializer(), this), Charsets.UTF_8)

    companion object {
        private const val SAMPLE_LENGTH = 120
        private val json = Json { ignoreUnknownKeys = true }

        /** Speakers file of the transcript at [transcriptPath]. */
        fun fileFor(transcriptPath: String): File = File(transcriptPath.removeSuffix(".txt") + ".speakers.json")

        fun read(file: File): SpeakerTranscript = json.decodeFromString(serializer(), file.readText(Charsets.UTF_8))

        /** Built from finished [lines] and their speakers' [voices], named from [book] where a voice is known. */
        fun of(lines: List<MeetingLine>, voices: List<FloatArray?>, book: VoiceBook?): SpeakerTranscript {
            val count = maxOf(voices.size, (lines.maxOfOrNull { it.speaker } ?: -1) + 1)
            val padded = List(count) { voices.getOrNull(it) }
            val names = book?.match(padded) ?: List(count) { null }
            return SpeakerTranscript(lines, padded.map { it?.toList() }, names)
        }
    }
}
