package com.audiops.desktop

import com.audiops.app.AudioOperation
import com.audiops.app.ProcessingListener
import com.audiops.app.UnsupportedOnPlatformException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class DesktopAudioEngineTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = File.createTempFile("audiops-desktop", "").apply {
            delete()
            mkdirs()
        }
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    /** Writes a stereo 16-bit WAV of a 440 Hz tone at [amplitude]. */
    private fun stereoWav(rate: Int, seconds: Double, amplitude: Double): File {
        val frames = (rate * seconds).toInt()
        val buffer = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val v = (amplitude * sin(2 * PI * 440 * i / rate) * 32767).toInt().toShort()
            buffer.putShort(v).putShort(v)
        }
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        val file = File(dir, "tone-$rate.wav")
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(buffer.array()), format, frames.toLong()), AudioFileFormat.Type.WAVE, file)
        return file
    }

    @Test fun decodesStereo48kAndRunsTools() {
        val engine = DesktopAudioEngine(File(dir, "data"))
        val source = stereoWav(48_000, 2.0, 0.5).absolutePath

        val waveform = engine.waveform(source, 100)
        assertEquals(2000f, waveform.durationMs.toFloat(), 2f)
        assertEquals(100, waveform.peaks.size)

        val output = File(engine.process(source, listOf(AudioOperation.Gain(-6.0206f)), ProcessingListener.None))
        assertTrue(output.exists())
        val header = ByteBuffer.wrap(output.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44_100, header.getInt(24))
        assertEquals(1, header.getShort(22).toInt())

        val report = engine.analyze(source, ProcessingListener.None)
        assertEquals(20 * log10(0.5f), report.peakDb, 0.3f)
        assertTrue("stored output counted", engine.outputStorageBytes() >= output.length())
    }

    @Test fun unsupportedFormatsGiveAFriendlyError() {
        val engine = DesktopAudioEngine(File(dir, "data"))
        val fake = File(dir, "song.mp3").apply { writeText("not audio") }
        val error = assertThrows(UnsupportedOnPlatformException::class.java) { engine.waveform(fake.absolutePath, 10) }
        assertTrue(error.message!!.contains("WAV"))
        assertTrue(engine.modelStorage().isEmpty())
    }

    @Test fun aiToolsReportUnsupported() {
        val engine = DesktopAudioEngine(File(dir, "data"))
        assertThrows(UnsupportedOnPlatformException::class.java) { engine.enhance("x", ProcessingListener.None) }
        assertTrue(!engine.supportsTranscribe)
    }
}
