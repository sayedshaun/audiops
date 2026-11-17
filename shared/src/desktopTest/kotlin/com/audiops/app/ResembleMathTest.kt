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

    @Test fun timeMappingMatchesUpstreamsTableAtSixteenSteps() {
        // The table upstream's 32-evaluation solver used (and this app used until the mapping became a formula).
        val upstream = doubleArrayOf(
            0.0, 0.15483690, 0.28779418, 0.40196359, 0.5, 0.58418312, 0.65647053, 0.71854319, 0.77184451,
            0.81761395, 0.85691582, 0.89066404, 0.91964338, 0.94452772, 0.96589571, 0.98424425, 1.0,
        )
        val times = MidpointSolver.times(MidpointSolver.UPSTREAM_STEPS)
        assertEquals(upstream.size, times.size)
        for (i in upstream.indices) assertEquals("time $i", upstream[i], times[i], 1e-7)
    }

    @Test fun anyStepCountSpansZeroToOneAndCountsTwoEvaluationsPerStep() {
        for (steps in listOf(1, 2, 4, 8, 16, 32)) {
            val times = MidpointSolver.times(steps)
            assertEquals(0.0, times.first(), 1e-12)
            assertEquals(1.0, times.last(), 1e-12)
            assertTrue((1 until times.size).all { times[it] > times[it - 1] })
            var evaluations = 0
            val result = MidpointSolver.integrate(floatArrayOf(1f), steps) { _, _ ->
                evaluations++
                floatArrayOf(2f)
            }
            assertEquals("evaluations for $steps steps", 2 * steps, evaluations)
            assertEquals(3f, result[0], 1e-5f) // dx/dt = 2 over [0, 1]
        }
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
