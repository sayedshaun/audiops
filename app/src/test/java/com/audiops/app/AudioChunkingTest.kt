package com.audiops.app

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Covers the section arithmetic that the enhancement join and transcription depend on. */
class AudioChunkingTest {
    private lateinit var file: File

    @Before fun createFile() {
        file = File.createTempFile("chunking-test", ".pcm")
    }

    @After fun removeFile() {
        file.delete()
    }

    /** Writes [seconds] of silent 44.1 kHz mono PCM16. */
    private fun pcm(seconds: Int) {
        file.outputStream().use { it.write(ByteArray(44_100 * seconds * 2)) }
    }

    @Test fun enhancementWindowsOverlapByOneSecond() {
        pcm(22)
        val sizes = mutableListOf<Pair<Int, Boolean>>()
        AudioDecoder.forEachEnhancementChunk(file) { samples, last -> sizes.add(samples.size to last) }
        assertEquals(listOf(20 * 44_100 to false, 3 * 44_100 to true), sizes)
    }

    @Test fun enhancementSectionCountMatchesWindowsYielded() {
        for (seconds in listOf(1, 20, 21, 22, 39, 40, 41, 60, 95)) {
            pcm(seconds)
            var yielded = 0
            AudioDecoder.forEachEnhancementChunk(file) { _, _ -> yielded++ }
            assertEquals("for ${seconds}s", yielded, AudioDecoder.enhancementSectionCount(file))
        }
    }

    @Test fun enhancementStopsExactlyOnAWindowBoundary() {
        // A window that ends exactly at the end of the audio must not start an extra one.
        pcm(20)
        var windows = 0
        AudioDecoder.forEachEnhancementChunk(file) { _, _ -> windows++ }
        assertEquals(1, windows)
    }

    @Test fun transcriptionSectionsAreFixedLength() {
        pcm(22)
        val sizes = mutableListOf<Int>()
        AudioDecoder.forEachChunk(file, 20) { sizes.add(it.size) }
        assertEquals(listOf(20 * 44_100, 2 * 44_100), sizes)
        assertEquals(2, AudioDecoder.sectionCount(file, 20))
        assertEquals(1, AudioDecoder.sectionCount(file, 30))
    }

    @Test fun cancellationStopsChunkedWork() {
        pcm(60)
        Thread.currentThread().interrupt()
        try {
            assertThrows(CancellationException::class.java) {
                AudioDecoder.forEachChunk(file, 20) { }
            }
        } finally {
            Thread.interrupted()
        }
    }
}
