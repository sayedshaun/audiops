package com.audiops.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriterTest {
    private lateinit var file: File

    @Before fun createFile() {
        file = File.createTempFile("wav-test", ".wav")
    }

    @After fun removeFile() {
        file.delete()
    }

    @Test fun writesValidMono44kPcm16Header() {
        WavWriter(file).use { it.append(floatArrayOf(0f, 0.5f, -0.5f)) }

        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes.array(), 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 3 * 2, bytes.getInt(4)) // RIFF size: everything after the first 8 bytes
        assertEquals("WAVE", String(bytes.array(), 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes.array(), 12, 4, Charsets.US_ASCII))
        assertEquals(1, bytes.getShort(20).toInt()) // PCM
        assertEquals(1, bytes.getShort(22).toInt()) // mono
        assertEquals(44_100, bytes.getInt(24))
        assertEquals(88_200, bytes.getInt(28))
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals("data", String(bytes.array(), 36, 4, Charsets.US_ASCII))
        assertEquals(6, bytes.getInt(40))
    }

    @Test fun clampsSamplesAndAppendsAcrossCalls() {
        WavWriter(file).use { writer ->
            writer.append(floatArrayOf(2f, -2f))
            writer.append(floatArrayOf(1f))
        }

        val data = ByteBuffer.wrap(file.readBytes(), 44, 6).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32767, data.short.toInt())
        assertEquals(-32767, data.short.toInt())
        assertEquals(32767, data.short.toInt())
    }

    @Test fun rejectsNonFiniteSamples() {
        var rejected = false
        try {
            WavWriter(file).use { it.append(floatArrayOf(Float.NaN)) }
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("NaN samples must not be written", rejected)
    }
}
