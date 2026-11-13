package com.audiops.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResembleMathTest {
    @Test fun fileNamesFollowTheExportedDtypes() {
        assertEquals("encoder-float32.tflite", HubModels.fileName("encoder", "fp32"))
        assertEquals("encoder-fp16weights.tflite", HubModels.fileName("encoder", "fp16"))
        assertEquals("encoder-int8-dynamic.tflite", HubModels.fileName("encoder", "int8"))
        assertEquals("vocoder-int8-weights.tflite", HubModels.fileName("vocoder", "int8"))
        assertEquals("vocoder-mobile-fp16.tflite", HubModels.fileName("vocoder", "mobile"))
        assertEquals("encoder-int8-dynamic.tflite", HubModels.fileName("encoder", "mobile"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownDtypeIsRejected() {
        HubModels.fileName("encoder", "bf16")
    }

    @Test fun midpointSolverIsExactForConstantVelocity() {
        // dx/dt = c integrates to x0 + c over t in [0, 1].
        val result = MidpointSolver.integrate(floatArrayOf(1f, -2f)) { _, _ -> floatArrayOf(0.5f, 0.5f) }
        assertEquals(1.5f, result[0], 1e-5f)
        assertEquals(-1.5f, result[1], 1e-5f)
    }

    @Test fun melAndDenoiserFeaturesHaveTheModelShapes() {
        val zeroFilters = ByteBuffer.allocate(1025 * 128 * 4).order(ByteOrder.LITTLE_ENDIAN).array()
        val features = AudioFeatures(zeroFilters)
        val tone = FloatArray(AudioFeatures.SAMPLES) { i -> 0.25f * kotlin.math.sin(i * 0.05f) }

        val mel = features.mel(tone)
        assertEquals(AudioFeatures.MEL_BINS * AudioFeatures.FRAMES, mel.size)
        assertTrue(mel.all { it.isFinite() })

        val spectral = features.denoiserFeatures(tone)
        assertEquals(3 * AudioFeatures.BINS * AudioFeatures.FRAMES, spectral.size)

        val waveform = features.denoiseWaveform(spectral, FloatArray(spectral.size))
        assertEquals(AudioFeatures.SAMPLES, waveform.size)
        assertTrue(waveform.all { it.isFinite() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun melFilterBankMustHaveTheExpectedSize() {
        AudioFeatures(ByteArray(16))
    }
}
