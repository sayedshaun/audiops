package com.audiops.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.audiops.app.dsp.ScnetSeparator
import com.audiops.app.dsp.SpectrumNetwork
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.log10

/**
 * Runs the real SCNet model through [ScnetSeparator] and compares it with a Python reference
 * (numpy STFT/iSTFT and onnxruntime) on the same clip. It only runs when these are set:
 * `SCNET_MODEL` (scnet.int8.onnx), `SCNET_MIX` and `SCNET_REFERENCE` (raw little-endian float32 files
 * of one 485,100-sample mix and its four stems, mono). It is a check for developers, not part of CI.
 */
class ScnetRealModelTest {
    private fun floats(file: File): FloatArray {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(bytes.remaining()).also { bytes.get(it) }
    }

    @Test fun matchesThePythonReference() {
        val model = System.getenv("SCNET_MODEL")
        val mix = System.getenv("SCNET_MIX")
        val reference = System.getenv("SCNET_REFERENCE")
        assumeTrue("set SCNET_MODEL, SCNET_MIX and SCNET_REFERENCE to run", model != null && mix != null && reference != null)

        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        environment.createSession(model, options).use { session ->
            val network = SpectrumNetwork { input ->
                val shape = longArrayOf(1, 4, ScnetSeparator.BINS.toLong(), ScnetSeparator.FRAMES.toLong())
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
                    session.run(mapOf("mix_spec" to tensor)).use { result ->
                        val buffer = (result[0] as OnnxTensor).floatBuffer
                        FloatArray(buffer.remaining()).also { buffer.get(it) }
                    }
                }
            }
            val samples = floats(File(mix))
            val expected = floats(File(reference))
            val stems = Array(4) { ArrayList<Float>() }
            val started = System.nanoTime()
            ScnetSeparator(network, 0.5f).separate(samples) { stem, piece -> piece.forEach { stems[stem.ordinal].add(it) } }
            println("separated ${samples.size / 44100.0} s in ${(System.nanoTime() - started) / 1e9} s")
            for (s in 0 until 4) {
                var signal = 0.0
                var error = 0.0
                for (i in samples.indices) {
                    val e = expected[s * samples.size + i].toDouble()
                    val d = stems[s][i] - e
                    signal += e * e
                    error += d * d
                }
                val snr = 10 * log10(signal / error.coerceAtLeast(1e-30))
                println("stem $s: SNR against the reference ${"%.1f".format(snr)} dB")
                // Quiet stems (drums on a speech clip) have almost no energy, so only judge those with some.
                if (signal > 1.0) assertTrue("stem $s SNR $snr dB", snr > 40.0)
            }
        }
    }
}
