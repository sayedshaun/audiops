package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs

/** Scores below are what the Zipformer tagger gave for real test clips (see SoundTags). */
class SoundTagsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cleanSpeechSuggestsTranscription() {
        val profile = SoundTags.profile(mapOf("Speech" to 0.96f, "Inside, small room" to 0.03f, "Narration, monologue" to 0.01f))
        assertEquals(SoundProfile(SoundKind.SPEECH, listOf("Speech")), profile)
        assertEquals(Tool.TRANSCRIBE, profile.kind.suggestedTool)
    }

    @Test fun speechWithNoiseOrHumSuggestsEnhancement() {
        val white = mapOf("Speech" to 0.89f, "White noise" to 0.14f, "Pink noise" to 0.08f, "Static" to 0.01f)
        assertEquals(SoundKind.NOISY_SPEECH, SoundTags.profile(white).kind)
        val hum = mapOf("Speech" to 0.68f, "Sine wave" to 0.52f, "Music" to 0.07f)
        assertEquals(SoundProfile(SoundKind.NOISY_SPEECH, listOf("Speech", "Sine wave")), SoundTags.profile(hum))
        assertEquals(Tool.ENHANCE, SoundKind.NOISY_SPEECH.suggestedTool)
    }

    @Test fun songsAndMusicSuggestSeparationButSpeechOverQuietMusicIsSpeech() {
        val choir = mapOf("Music" to 0.80f, "A capella" to 0.77f, "Singing" to 0.47f, "Vocal music" to 0.47f, "Choir" to 0.46f)
        assertEquals(SoundKind.MUSIC, SoundTags.profile(choir).kind)
        assertEquals(listOf("Music", "A capella", "Singing", "Vocal music"), SoundTags.profile(choir).tags)
        assertEquals(SoundKind.SPEECH, SoundTags.profile(mapOf("Speech" to 0.9f, "Music" to 0.45f)).kind)
        assertEquals(SoundKind.MUSIC, SoundTags.profile(mapOf("Speech" to 0.5f, "Music" to 0.8f)).kind)
        assertEquals(Tool.SEPARATE, SoundKind.MUSIC.suggestedTool)
    }

    @Test fun otherSoundsAreNamedWithoutASuggestion() {
        val cat = mapOf("Cat" to 0.77f, "Meow" to 0.68f, "Animal" to 0.61f, "Music" to 0.21f, "Speech" to 0.06f)
        assertEquals(SoundProfile(SoundKind.OTHER, listOf("Cat", "Meow", "Animal", "Music")), SoundTags.profile(cat))
        assertEquals(null, SoundKind.OTHER.suggestedTool)
    }

    @Test fun windowsAreAveraged() {
        val average = SoundTags.average(listOf(mapOf("Speech" to 1f, "Music" to 0.5f), mapOf("Speech" to 0.5f)))
        assertEquals(mapOf("Speech" to 0.75f, "Music" to 0.25f), average)
        assertEquals(emptyMap<String, Float>(), SoundTags.average(emptyList()))
    }

    private fun silence(seconds: Int): File = folder.newFile().apply { writeBytes(ByteArray(seconds * PIPELINE_RATE * 2)) }

    @Test fun longRecordingsAreHeardAtTheStartMiddleAndEnd() {
        fun sizes(seconds: Int) = SoundTags.windows(silence(seconds)).map { it.size }
        val window = SoundTags.WINDOW_SECONDS * MEETING_RATE
        assertEquals(1, sizes(4).size)
        assertEquals(true, abs(sizes(4).single() - 4 * MEETING_RATE) <= 2)
        assertEquals(2, sizes(25).size)
        val long = sizes(120)
        assertEquals(3, long.size)
        long.forEach { assertEquals(true, abs(it - window) <= 2) }
        assertEquals(emptyList<Int>(), SoundTags.windows(folder.newFile()).map { it.size })
    }
}
