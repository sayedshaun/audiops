package com.audiops.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The WAV reader behind audio export: header parsing, byte output and rate conversion. */
class PcmSourceTest {
    private lateinit var file: File

    @Before fun create() {
        file = File.createTempFile("pcm-source", ".wav")
    }

    @After fun remove() {
        file.delete()
    }

    private fun sine(rate: Int, seconds: Double, hz: Double = 440.0) {
        WavWriter(file, rate).use { it.append(FloatArray((rate * seconds).toInt()) { i -> 0.5f * sin(2 * PI * hz * i / rate).toFloat() }) }
    }

    private fun readAll(source: PcmSource): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(1000)
        while (true) {
            buffer.clear()
            val n = source.read(buffer)
            if (n < 0) break
            out.write(buffer.array(), 0, n)
        }
        return out.toByteArray()
    }

    @Test fun readsTheRateAndEverySample() {
        sine(24_000, 1.0)
        PcmSource(file).use { source ->
            assertEquals(24_000, source.sourceRate)
            assertEquals(24_000, source.rate)
            assertEquals(24_000L, source.sourceSamples)
            assertEquals(48_000, readAll(source).size)
        }
    }

    @Test fun bytesAreLittleEndianPcm16() {
        sine(16_000, 0.1)
        val bytes = PcmSource(file).use(::readAll)
        val first = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals(0, first.get(0).toInt())
        assertTrue(abs(first.get(9).toInt()) > 1000)
    }

    @Test fun upsamplingKeepsTheDuration() {
        sine(22_050, 2.0)
        PcmSource(file, targetRate = 48_000).use { source ->
            assertEquals(48_000, source.rate)
            val samples = readAll(source).size / 2
            assertTrue("got $samples", abs(samples - 96_000) < 200)
        }
    }

    @Test fun veryLargeUpsamplingIsNotTruncated() {
        sine(8_000, 1.5)
        PcmSource(file, targetRate = 48_000).use { source ->
            val samples = readAll(source).size / 2
            assertTrue("got $samples", abs(samples - 72_000) < 300)
        }
    }

    @Test fun downsamplingKeepsTheDuration() {
        sine(44_100, 1.0)
        PcmSource(file, targetRate = 16_000).use { source ->
            val samples = readAll(source).size / 2
            assertTrue("got $samples", abs(samples - 16_000) < 100)
        }
    }

    @Test fun rejectsFilesThatAreNotMonoPcm16() {
        file.writeBytes("not a wav file".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { PcmSource(file).close() }
    }
}
