package com.audiops.app

import com.audiops.app.dsp.AudioAnalyzer
import com.audiops.app.dsp.DspPipeline
import com.audiops.app.dsp.PcmFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** The presets run through the real DSP pipeline and land on their loudness target. */
class PresetTest {
    private lateinit var dir: File

    @Before fun create() {
        dir = File.createTempFile("preset", "").apply {
            delete()
            mkdirs()
        }
    }

    @After fun remove() {
        dir.deleteRecursively()
    }

    /** Eight seconds of speech-like bursts (440 Hz and 880 Hz) with pauses, over steady noise. */
    private fun speechLike(): FloatArray {
        val random = Random(7)
        return FloatArray(PIPELINE_RATE * 8) { i ->
            val t = i.toDouble() / PIPELINE_RATE
            val burst = if ((t % 2.0) < 1.2) 0.25 * sin(2 * PI * 440 * t) + 0.12 * sin(2 * PI * 880 * t) else 0.0
            (burst + 0.02 * (random.nextDouble() * 2 - 1)).toFloat()
        }
    }

    private fun readWav(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray((bytes.size - 44) / 2) { buffer.short / 32768f }
    }

    @Test fun everyPresetHasOperationsAndSurvivesJson() {
        val json = Json
        Preset.entries.forEach { preset ->
            assertTrue(preset.name, preset.operations.size in 2..4)
            val spec: JobSpec = JobSpec.Process(preset.operations)
            assertEquals(spec, json.decodeFromString<JobSpec>(json.encodeToString(spec)))
        }
    }

    @Test fun everyPresetReachesItsLoudnessTargetWithoutClipping() {
        val pcm = File(dir, "in.pcm")
        PcmFile.Writer(pcm).use { it.write(speechLike()) }
        Preset.entries.forEach { preset ->
            val output = File(dir, "${preset.name}.wav")
            DspPipeline.run(pcm, preset.operations, output, dir, ProcessingListener.None)
            val samples = readWav(output)
            assertTrue("${preset.name} produced no audio", samples.isNotEmpty())
            assertTrue("${preset.name} has invalid samples", samples.all { it.isFinite() })
            assertTrue("${preset.name} clips", samples.maxOf { abs(it) } <= 1f)
            val target = (preset.operations.last() as AudioOperation.NormalizeLoudness).target.lufs
            val analyzer = AudioAnalyzer(PIPELINE_RATE).apply { add(samples) }
            val measured = analyzer.integratedLoudness()
            assertTrue("${preset.name} could not be measured", measured != null)
            assertEquals("${preset.name} loudness", target, measured!!, 2f)
        }
    }

    @Test fun presetsThatRemoveSilenceShortenTheRecording() {
        val pcm = File(dir, "in.pcm")
        PcmFile.Writer(pcm).use { it.write(speechLike()) }
        val output = File(dir, "short.wav")
        DspPipeline.run(pcm, Preset.VOICE_MESSAGE.operations, output, dir, ProcessingListener.None)
        assertTrue(readWav(output).size < PIPELINE_RATE * 8 - PIPELINE_RATE)
    }
}
