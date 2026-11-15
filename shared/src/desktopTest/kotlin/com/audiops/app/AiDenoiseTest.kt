package com.audiops.app

import com.audiops.app.dsp.AiDenoiser
import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.ChunkedEnhancer
import com.audiops.app.dsp.DspPipeline
import com.audiops.app.dsp.PcmFile
import com.audiops.app.dsp.ResampledProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

class AiDenoiseTest {
    private fun feed(processor: AudioProcessor, signal: FloatArray, block: Int): FloatArray {
        val out = ArrayList<Float>()
        var i = 0
        while (i < signal.size) {
            processor.process(signal.copyOfRange(i, minOf(signal.size, i + block))).forEach { out += it }
            i += block
        }
        processor.flush().forEach { out += it }
        return out.toFloatArray()
    }

    private val signal = FloatArray(10_007) { sin(it * 0.01f) + 0.1f * ((it * 7919) % 13 - 6) / 6f }

    @Test fun chunkedIdentityReturnsTheInputExactly() {
        for (block in listOf(1, 97, 4096, 20_000)) {
            val out = feed(ChunkedEnhancer(body = 1000, warmup = 300, lookahead = 50) { it.copyOf() }, signal, block)
            assertArrayEquals("block $block", signal, out, 0f)
        }
    }

    @Test fun eachChunkSeesItsHistoryAndLookahead() {
        // Each call returns its own length at every position, so the output shows which window produced each sample.
        val windows = ArrayList<Int>()
        val out = feed(
            ChunkedEnhancer(body = 1000, warmup = 300, lookahead = 50) { chunk ->
                windows += chunk.size
                FloatArray(chunk.size) { chunk.size.toFloat() }
            },
            signal,
            512,
        )
        assertEquals(signal.size, out.size)
        // First chunk: no history; later chunks: 300 history + 1000 body + 50 lookahead (less near the end).
        assertEquals(1050, windows.first())
        assertEquals(1350, windows[1])
        assertTrue(windows.all { it <= 1350 })
        assertEquals(1050f, out[0])
        assertEquals(1350f, out[1500])
    }

    @Test fun resampledProcessorKeepsTheLength() {
        val sine = FloatArray(10_007) { sin(it * 0.01f) }
        val out = feed(ResampledProcessor(48_000, ChunkedEnhancer(body = 4800, warmup = 960, lookahead = 96) { it.copyOf() }), sine, 1024)
        assertTrue("length ${out.size}", abs(out.size - sine.size) <= 2)
        // A slow sine survives the 44.1 -> 48 -> 44.1 kHz round trip.
        for (i in 200 until sine.size - 200) assertEquals(sine[i], out[i], 0.02f)
    }

    @Test fun pipelineUsesTheAiDenoiserWhenGiven() {
        val dir = File.createTempFile("ai-denoise", "").apply {
            delete()
            mkdirs()
        }
        try {
            val input = File(dir, "in.pcm")
            PcmFile.Writer(input).use { it.write(FloatArray(44_100) { i -> 0.5f * sin(i * 0.05f) }) }
            var strengthSeen = -1f
            val halver = AiDenoiser { strength, _ ->
                strengthSeen = strength
                object : AudioProcessor {
                    override fun process(input: FloatArray) = FloatArray(input.size) { input[it] * 0.5f }
                }
            }
            val output = File(dir, "out.wav")
            DspPipeline.run(input, listOf(AudioOperation.Denoise(0.6f)), output, dir, ProcessingListener.None, halver)
            assertEquals(0.6f, strengthSeen)
            val samples = readWav(output)
            assertEquals(44_100, samples.size)
            assertEquals(0.25f * sin(1000 * 0.05f), samples[1000], 0.001f)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun readWav(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray((bytes.size - 44) / 2) { buffer.short / 32768f }
    }
}
