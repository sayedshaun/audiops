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
            if (voice.engine == TtsEngine.PIPER) {
                assertTrue(voice.id, voice.files.keys.any { it.endsWith(".onnx") } && "tokens.txt" in voice.files)
            }
            if (voice.engine == TtsEngine.SUPERTONIC) {
                assertTrue(voice.id, "voice.bin" in voice.files && "unicode_indexer.bin" in voice.files && "tts.json" in voice.files)
                assertTrue(voice.id, supertonicSpeaker(voice) in 0..9)
            }
            if (voice.engine == TtsEngine.POCKET) assertTrue(voice.id, voice.files.keys.single().endsWith(".litertlm"))
            if (voice.engine == TtsEngine.KOKORO) {
                assertTrue(voice.id, voice.language == "en-US" || voice.language == "en-GB")
                assertTrue(
                    voice.id,
                    "voices.bin" in voice.files && "lexicon-us-en.txt" in voice.files && "lexicon-gb-en.txt" in voice.files,
                )
                assertTrue(voice.id, kokoroSpeaker(voice) in 0..53)
            }
        }
    }

    @Test fun catalogueCoversTheAdvertisedLanguages() {
        // 28 earlier languages plus nine only Supertonic covers.
        assertEquals(37, ttsLanguages.size)
        assertEquals("en-US", defaultTtsVoice.language)
        assertEquals(TtsEngine.SUPERTONIC, defaultTtsVoice.engine)
        assertEquals(defaultTtsVoice, ttsVoice(defaultTtsVoice.id))
        // Every language offers Supertonic first, except those it does not cover (Norwegian, Kazakh, Nepali, Persian, English UK, Portuguese Portugal).
        val withoutSupertonic = ttsLanguages.filter { language ->
            ttsVoices.first { it.language == language }.engine != TtsEngine.SUPERTONIC
        }
        assertEquals(setOf("en-GB", "pt-PT", "fa", "kk", "ne", "no"), withoutSupertonic.toSet())
        // The voices of one Supertonic, Kokoro or PocketTTS language share their files.
        val english = ttsVoices.filter { it.language == "en-US" }
        assertEquals(16, english.size)
        assertEquals(3, english.map { it.files }.toSet().size)
        assertEquals(listOf("Emma", "Isabella", "George", "Fable", "Cori"), ttsVoices.filter { it.language == "en-GB" }.map { it.name })
    }

    @Test fun kokoroVoicesMapToItsSpeakerTable() {
        // Positions in the model's speaker_names metadata.
        assertEquals(3, kokoroSpeaker(ttsVoice("kokoro-af_heart")!!))
        assertEquals(26, kokoroSpeaker(ttsVoice("kokoro-bm_george")!!))
    }

    @Test fun supertonicStylesMapToItsSpeakerTable() {
        assertEquals(0, supertonicSpeaker(ttsVoice("supertonic-en-f1")!!))
        assertEquals(5, supertonicSpeaker(ttsVoice("supertonic-ko-m1")!!))
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
