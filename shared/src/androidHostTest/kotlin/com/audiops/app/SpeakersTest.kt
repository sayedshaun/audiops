package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Sentence breaks, transcription by utterance, speaker files and remembered voices. */
class SpeakersTest {
    @get:Rule val folder = TemporaryFolder()

    private val random = Random(11)
    private val dimension = 192
    private val people = List(3) { FloatArray(dimension) { random.nextFloat() * 2 - 1 } }

    private fun voiceOf(person: Int, noise: Float = 0.5f) = FloatArray(dimension) {
        people[person][it] +
            (random.nextFloat() * 2 - 1) * noise
    }

    private fun line(start: Long, end: Long, text: String, speaker: Int = 0) = MeetingLine(start, end, speaker, text)

    // Sentences

    @Test fun utterancesBecomeSentencesWithTheLanguagesFullStop() {
        val lines = listOf(line(0, 2000, "hello there"), line(2500, 4000, "how are you,"), line(5000, 6000, "fine?"))
        assertEquals(
            listOf("Hello there.", "How are you.", "Fine?"),
            SpeechSentences.punctuate(lines, "en", 12_000).map { it.text },
        )
        assertEquals(
            listOf("আমি ভালো আছি।"),
            SpeechSentences.punctuate(listOf(line(0, 1000, "আমি ভালো আছি")), "bn", 12_000).map { it.text },
        )
        assertEquals("İstanbul.", SpeechSentences.sentence("istanbul", "tr", capitalize = true, close = true))
        assertEquals("ok", SpeechSentences.sentence("ok", "th", capitalize = false, close = true))
    }

    @Test fun anUtteranceCutAtTheLengthLimitRunsOnIntoTheNext() {
        val lines = listOf(line(0, 12_000, "this goes on"), line(12_000, 14_000, "and on"), line(15_000, 16_000, "then stops"))
        assertEquals(
            listOf("This goes on", "and on.", "Then stops."),
            SpeechSentences.punctuate(lines, "en", 12_000).map { it.text },
        )
    }

    @Test fun longPausesStartParagraphsAndChineseHasNoSpaces() {
        val lines = listOf(line(0, 1000, "One."), line(1200, 2000, "Two."), line(5000, 6000, "Three."))
        assertEquals("One. Two.\n\nThree.", SpeechSentences.join(lines, "en"))
        assertEquals("你好。再见。", SpeechSentences.join(listOf(line(0, 500, "你好。"), line(600, 900, "再见。")), "zh"))
    }

    // Transcription by utterance

    /** A detector that cuts the audio into one-second utterances, so the test knows what to expect. */
    private class EverySecond : SpeechDetector {
        var received = 0L
        private var pending = FloatArray(0)
        private var start = 0L

        override fun accept(samples: FloatArray): List<Utterance> {
            received += samples.size
            pending += samples
            val out = mutableListOf<Utterance>()
            while (pending.size >= MEETING_RATE) {
                out += Utterance(start, pending.copyOf(MEETING_RATE))
                start += MEETING_RATE
                pending = pending.copyOfRange(MEETING_RATE, pending.size)
            }
            return out
        }

        override fun flush(): List<Utterance> =
            if (pending.isEmpty()) emptyList() else listOf(Utterance(start, pending)).also { pending = FloatArray(0) }
    }

    private fun pcm(seconds: Double): File {
        val samples = (seconds * PIPELINE_RATE).toInt()
        val buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { buffer.putShort((sin(2 * PI * 220 * it / PIPELINE_RATE) * 8000).toInt().toShort()) }
        return folder.newFile().apply { writeBytes(buffer.array()) }
    }

    @Test fun recordingsAreResampledCutAndTranscribedInOrder() {
        val detector = EverySecond()
        var count = 0
        val transcriber = MeetingTranscriber(recognizer = { "part ${++count}" }, embedder = { null })
        val sections = mutableListOf<Int>()
        val listener = object : ProcessingListener {
            override fun onSection(index: Int, total: Int) {
                sections += index
                assertEquals(3, total)
            }
        }
        val lines = SpeechTranscription.lines(pcm(25.5), detector, transcriber, listener)
        assertEquals(listOf(1, 2, 3), sections)
        // 25.5 s at 16 kHz, give or take the resampler's edge.
        assertTrue(abs(detector.received - 25.5 * MEETING_RATE) < 16)
        assertEquals(26, lines.size)
        assertEquals(MeetingLine(1000, 2000, 0, "part 2"), lines[1])
        val plain = SpeechTranscription.plain(SpeechSentences.punctuate(lines.take(2), "en", 12_000), "en")
        assertEquals("Part 1. Part 2.", plain.text)
        assertEquals(listOf(Cue(0, 1000, "Part 1."), Cue(1000, 2000, "Part 2.")), plain.cues)
    }

    @Test fun eachSpeakersVoiceIsTheMeanOfTheirUtterances() {
        val order = listOf(0, 1, 0, 1, 0)
        var index = 0
        val transcriber = MeetingTranscriber(recognizer = { "words" }, embedder = { voiceOf(order[index++]) })
        order.forEachIndexed { i, _ -> transcriber.add(i * 32_000L, FloatArray(32_000)) }
        val final = transcriber.finish()
        val voices = transcriber.voices(final)
        assertEquals(2, voices.size)
        val book = VoiceBook(folder.newFile("v.json").apply { delete() })
        book.remember("Ana", people[0])
        book.remember("Ben", people[1])
        assertEquals(listOf("Ana", "Ben"), book.match(voices))
    }

    // Remembered voices

    @Test fun voiceBookMatchesEachNameOnceAndOnlyWhenClose() {
        val file = File(folder.root, "voices.json")
        val book = VoiceBook(file)
        assertEquals(listOf(null, null), book.match(listOf(people[0], null)))
        book.remember("Ana", voiceOf(0, 0.2f))
        book.remember(" Ana ", voiceOf(0, 0.2f)) // refines the same voice
        book.remember("Ben", voiceOf(1, 0.2f))
        assertEquals(listOf("Ana", "Ben"), VoiceBook(file).names())
        // Two speakers close to Ana: only the closer one gets the name; a stranger gets none.
        assertEquals(listOf("Ana", null, null, "Ben"), book.match(listOf(people[0], voiceOf(0, 0.9f), people[2], people[1])))
        book.forget("Ana")
        assertEquals(listOf("Ben"), book.names())
        book.forget("Ben")
        assertFalse(file.exists())
    }

    // Speakers files and naming

    private class TestEngine(root: File) : JvmAudioEngine(File(root, "work"), File(root, "outputs"), File(root, "models")) {
        override fun decode(source: String): File = error("unused")
        override val supportsEnhance = false
        override val supportsTranscribe = true
        override val supportsSpeak = false
        override fun enhance(source: String, listener: ProcessingListener): String = error("unused")
        override fun speak(text: String, voiceId: String, speed: Float, listener: ProcessingListener): String = error("unused")
        override fun transcribe(
            source: String,
            language: String,
            model: TranscriptionModel,
            speakers: Boolean,
            speakerName: (Int) -> String,
            listener: ProcessingListener,
        ): String = error("unused")

        override fun copyFile(path: String, destination: String) = error("unused")

        override fun writeText(text: String, destination: String) = error("unused")

        fun store(speakers: SpeakerTranscript, speakerName: (Int) -> String) = storeSpeakerTranscript(speakers, speakerName)
    }

    @Test fun namingSpeakersRewritesTheTranscriptAndRemembersTheirVoices() {
        val engine = TestEngine(folder.root)
        val name: (Int) -> String = { "Speaker $it" }
        val lines = listOf(line(0, 2000, "Hello.", 0), line(2000, 4000, "Hi.", 1), line(4000, 5000, "Bye.", 0))
        val path = engine.store(SpeakerTranscript.of(lines, listOf(people[0], null), null), name)
        assertEquals("Speaker 1 (0:00)\nHello.\n\nSpeaker 2 (0:02)\nHi.\n\nSpeaker 1 (0:04)\nBye.", engine.readText(path))
        assertEquals(SpeakerTranscript.fileFor(path).absolutePath, engine.speakersFile(path))
        assertEquals(listOf(SpeakerInfo(null, "Hello.", true), SpeakerInfo(null, "Hi.", false)), engine.speakers(path))

        engine.nameSpeakers(path, listOf("Ana", " "), remember = true, speakerName = name)
        assertEquals("Ana (0:00)\nHello.\n\nSpeaker 2 (0:02)\nHi.\n\nAna (0:04)\nBye.", engine.readText(path))
        assertTrue(engine.readText(engine.subtitlesFor(path)!!).contains("Ana: Bye."))
        assertEquals("Ana", engine.speakers(path).first().name)
        assertEquals(listOf("Ana"), engine.rememberedVoices())

        // A new transcript with Ana's voice is named by itself.
        val next = engine.store(
            SpeakerTranscript.of(lines, listOf(voiceOf(0, 0.3f), people[2]), VoiceBook(File(folder.root, "voices.json"))),
            name,
        )
        assertTrue(engine.readText(next).startsWith("Ana (0:00)"))

        engine.forgetVoice("Ana")
        assertEquals(emptyList<String>(), engine.rememberedVoices())
        engine.deleteFile(path)
        assertNull(engine.speakersFile(path))
        assertNull(engine.subtitlesFor(path))
    }

    @Test fun speakersFilesSurviveARoundTrip() {
        val file = folder.newFile()
        val speakers = SpeakerTranscript(listOf(line(0, 1000, "Hi.", 0)), listOf(listOf(0.6f, 0.8f)), listOf("Ana"))
        speakers.write(file)
        assertEquals(speakers, SpeakerTranscript.read(file))
    }
}
