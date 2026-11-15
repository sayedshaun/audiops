package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Subtitle cue building from recognizer output, and SRT / WebVTT writing. */
class SubtitlesTest {
    @Test fun srtNumbersCuesAndUsesCommaMilliseconds() {
        val srt = Subtitles.srt(listOf(Cue(0, 1_500, "Hello."), Cue(3_725_042, 3_726_000, "Second\nline.")))
        assertEquals("1\n00:00:00,000 --> 00:00:01,500\nHello.\n\n2\n01:02:05,042 --> 01:02:06,000\nSecond\nline.\n\n", srt)
    }

    @Test fun vttHasAHeaderAndDotMilliseconds() {
        val vtt = Subtitles.toVtt(Subtitles.srt(listOf(Cue(1_000, 2_250, "Hi, there."))))
        assertTrue(vtt, vtt.startsWith("WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.250\nHi, there."))
    }

    @Test fun tokenTimesPlaceEachSentence() {
        val tokens = listOf("▁Hello", "▁world", ".", "▁Bye", ".")
        val times = listOf(0.5f, 1.0f, 1.4f, 3.0f, 3.4f)
        val cues = Cues.forSection(offsetMs = 20_000, durationMs = 20_000, text = "Hello world. Bye.", tokens = tokens, timestamps = times)
        assertEquals(listOf("Hello world.", "Bye."), cues.map { it.text })
        assertEquals(20_500, cues[0].startMs)
        assertEquals(23_000, cues[0].endMs)
        assertEquals(23_000, cues[1].startMs)
        assertTrue(cues.all { it.endMs > it.startMs })
    }

    @Test fun withoutTokenTimesCuesAreSpreadOverTheSection() {
        val cues = Cues.forSection(0, 10_000, "One two. Three four.", emptyList(), emptyList())
        assertEquals(2, cues.size)
        assertEquals(0, cues[0].startMs)
        assertTrue(cues[0].endMs <= cues[1].startMs)
        assertTrue(cues[1].endMs <= 10_000)
    }

    @Test fun cuesStayInOrderAndInsideTheSection() {
        val text = "word ".repeat(60).trim()
        val cues = Cues.forSection(5_000, 8_000, text, emptyList(), emptyList())
        assertTrue(cues.size > 1)
        cues.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
        assertTrue(cues.all { it.startMs >= 5_000 && it.text.length <= 85 })
    }

    @Test fun longCuesWrapToTwoLines() {
        val cues = Cues.forSection(0, 6_000, "This sentence is long enough that it needs two lines on screen.", emptyList(), emptyList())
        assertEquals(1, cues.size)
        assertEquals(2, cues[0].text.lines().size)
    }

    @Test fun blankTextGivesNoCues() {
        assertTrue(Cues.forSection(0, 1_000, "   ", emptyList(), emptyList()).isEmpty())
    }
}
