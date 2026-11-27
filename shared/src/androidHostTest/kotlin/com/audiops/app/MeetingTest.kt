package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MeetingTest {
    /** Voices as noisy copies of a few random directions, like real embeddings of different people. */
    private val random = Random(7)
    private val dimension = 192
    private val voices = List(3) { FloatArray(dimension) { random.nextFloat() * 2 - 1 } }

    private fun utterance(voice: Int, noise: Float = 0.6f) = FloatArray(dimension) {
        voices[voice][it] +
            (random.nextFloat() * 2 - 1) * noise
    }

    @Test fun liveClusteringSeparatesVoicesAndKeepsShortRepliesWithThePreviousSpeaker() {
        val clusters = SpeakerClusters()
        val order = listOf(0, 0, 1, 0, 2, 1, 2, 0)
        val assigned = order.map { clusters.assign(utterance(it)) }
        assertEquals(listOf(0, 0, 1, 0, 2, 1, 2, 0), assigned)
        assertEquals(3, clusters.count)
        assertEquals(0, clusters.assign(null))
        assertEquals(0, SpeakerClusters().assign(null))
    }

    @Test fun reclusteringFindsTheSpeakersAndNumbersThemInOrderOfAppearance() {
        val order = listOf(2, 2, 0, 1, 0, 2, 1, 1, 0)
        val embeddings: List<FloatArray?> = order.map { utterance(it) }
        val labels = SpeakerClusters.recluster(embeddings)
        assertEquals(listOf(0, 0, 1, 2, 1, 0, 2, 2, 1), labels.toList())
    }

    @Test fun reclusteringFillsUtterancesWithoutAnEmbedding() {
        val embeddings = listOf(null, utterance(0), null, utterance(1), null, utterance(1))
        assertEquals(listOf(0, 0, 0, 1, 1, 1), SpeakerClusters.recluster(embeddings).toList())
        assertEquals(listOf(0, 0), SpeakerClusters.recluster(listOf(null, null)).toList())
        assertEquals(emptyList<Int>(), SpeakerClusters.recluster(emptyList()).toList())
    }

    @Test fun oneVoiceStaysOneSpeaker() {
        val labels = SpeakerClusters.recluster(List(40) { utterance(1, noise = 0.8f) })
        assertTrue(labels.all { it == 0 })
    }

    @Test fun transcriberSkipsSilenceTimesLinesAndCorrectsSpeakersAtTheEnd() {
        // Each "utterance" carries its voice in the first sample, so the fakes can tell them apart.
        val texts = ArrayDeque(listOf("Hello there.", "", "Hi.", "How are you?", "Fine."))
        val transcriber = MeetingTranscriber(
            recognizer = { texts.removeFirst() },
            embedder = { samples -> utterance(samples[0].toInt()) },
        )
        fun audio(voice: Int, seconds: Double) = FloatArray((seconds * MEETING_RATE).toInt()) { voice.toFloat() }

        val first = transcriber.add(0, audio(0, 2.0))
        assertEquals(MeetingLine(0, 2000, 0, "Hello there."), first)
        assertNull(transcriber.add(48_000, audio(1, 1.5)))
        assertEquals(1, transcriber.add(80_000, audio(1, 1.5))?.speaker)
        // Too short to embed: stays with the previous speaker.
        assertEquals(1, transcriber.add(112_000, audio(0, 0.5))?.speaker)
        assertEquals(0, transcriber.add(128_000, audio(0, 3.0))?.speaker)
        assertEquals(2, transcriber.speakers)

        val final = transcriber.finish()
        assertEquals(listOf("Hello there.", "Hi.", "How are you?", "Fine."), final.map { it.text })
        assertEquals(listOf(0, 1, 1, 0), final.map { it.speaker })
        assertEquals(5000L, final[1].startMs)
        assertEquals("Hi.", MeetingTranscriber({ "Hi." }, { null }).partial(FloatArray(10)))
    }

    @Test fun documentJoinsTurnsAndWritesSubtitles() {
        val lines = listOf(
            MeetingLine(0, 2_000, 0, "Hello there."),
            MeetingLine(2_500, 4_000, 0, "Shall we start?"),
            MeetingLine(65_000, 67_000, 1, "Yes."),
            MeetingLine(3_725_000, 3_726_000, 0, "Thanks."),
        )
        val name = { n: Int -> "Speaker $n" }
        assertEquals(
            "Speaker 1 (0:00)\nHello there. Shall we start?\n\nSpeaker 2 (1:05)\nYes.\n\nSpeaker 1 (1:02:05)\nThanks.",
            MeetingDocument.text(lines, name),
        )
        val cues = MeetingDocument.cues(lines, name)
        assertEquals(Cue(65_000, 67_000, "Speaker 2: Yes."), cues[2])
        assertEquals("", MeetingDocument.text(emptyList(), name))
    }
}
