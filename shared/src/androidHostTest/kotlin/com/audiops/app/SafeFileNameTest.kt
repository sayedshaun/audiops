package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFileNameTest {
    @Test fun ordinaryNamesAreKept() {
        assertEquals("voice-note_01.ogg", safeFileName("voice-note_01.ogg"))
    }

    @Test fun unsafeCharactersAndPathPartsAreReplaced() {
        val name = safeFileName("../../My recording (final)?.m4a")
        assertFalse(name, name.contains('/') || name.contains(' ') || name.startsWith("."))
        assertTrue(name, name.endsWith(".m4a"))
    }

    @Test fun nonLatinNamesStillGiveAUsableName() {
        assertEquals("audio", safeFileName("রেকর্ডিং"))
        assertEquals("audio.wav", safeFileName("রেকর্ডিং.wav"))
    }

    @Test fun longNamesAreShortenedAndKeepTheExtension() {
        val name = safeFileName("a".repeat(300) + ".mp3")
        assertTrue(name.length <= 70)
        assertTrue(name.endsWith(".mp3"))
    }

    @Test fun emptyNamesGetADefault() {
        assertEquals("audio", safeFileName(""))
    }
}
