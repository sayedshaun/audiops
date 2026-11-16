package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The voice catalogue and the text splitting used by text to speech. */
class TtsTest {
    @Test fun voiceIdsAreUniqueAndEveryFileIsPinned() {
        assertEquals(ttsVoices.size, ttsVoices.map { it.id }.toSet().size)
        for (voice in ttsVoices) {
            assertTrue(voice.id, voice.revision.matches(Regex("[0-9a-f]{40}")))
            assertTrue(voice.id, voice.files.isNotEmpty() && voice.files.values.all { it.matches(Regex("[0-9a-f]{64}")) })
            assertTrue(voice.id, voice.downloadMb > 0)
            if (voice.engine ==
                TtsEngine.PIPER
            ) {
                assertTrue(voice.id, voice.files.keys.any { it.endsWith(".onnx") } && "tokens.txt" in voice.files)
            }
            if (voice.engine == TtsEngine.POCKET) assertTrue(voice.id, voice.files.keys.single().endsWith(".litertlm"))
        }
    }

    @Test fun catalogueCoversTheAdvertisedLanguages() {
        assertEquals(28, ttsLanguages.size)
        assertEquals("en-US", defaultTtsVoice.language)
        assertEquals(defaultTtsVoice, ttsVoice(defaultTtsVoice.id))
        // Voices of one PocketTTS language share one file.
        val english = ttsVoices.filter { it.language == "en-US" }
        assertEquals(8, english.size)
        assertEquals(1, english.map { it.files }.toSet().size)
    }

    @Test fun textIsSplitAtSentencesWithinTheLimit() {
        val text = "First sentence. Second one!\nThird line? " + "word ".repeat(200)
        val pieces = TextChunks.split(text, maxChars = 60)
        assertTrue(pieces.all { it.length <= 60 && it.isNotBlank() })
        assertEquals("First sentence. Second one! Third line?", pieces.first())
        assertEquals(text.split(Regex("\\s+")).filter { it.isNotEmpty() }, pieces.flatMap { it.split(' ') })
    }

    @Test fun textWithoutSpacesIsStillSplit() {
        val pieces = TextChunks.split("あ".repeat(250), maxChars = 100)
        assertEquals(listOf(100, 100, 50), pieces.map { it.length })
    }

    @Test fun blankTextGivesNoPieces() {
        assertTrue(TextChunks.split("  \n\n ").isEmpty())
    }
}
