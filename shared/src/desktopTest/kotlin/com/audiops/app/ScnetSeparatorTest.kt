package com.audiops.app

import com.audiops.app.dsp.ScnetSeparator
import com.audiops.app.dsp.SpectrumNetwork
import com.audiops.app.dsp.Stem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.sin

/**
 * Everything around SCNet's network, checked with a stand-in network that gives each stem a fixed
 * share of the input spectrum: the stems must then be exactly those shares of the (mean-free) mix,
 * which exercises the STFT, inverse STFT, segmenting, fades, overlap-add and trimming together.
 */
class ScnetSeparatorTest {
    private val shares = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
    private val plane = ScnetSeparator.BINS * ScnetSeparator.FRAMES

    private val sharing = SpectrumNetwork { input ->
        FloatArray(16 * plane).also { out ->
            for (s in 0 until 4) {
                for (c in 0 until 4) {
                    for (i in 0 until plane) out[(4 * s + c) * plane + i] = input[c * plane + i] * shares[s]
                }
            }
        }
    }

    private fun signal(n: Int) = FloatArray(n) { i -> 0.3f * sin(i * 0.013f) + 0.2f * sin(i * 0.31f) + 0.05f }

    private fun run(mix: FloatArray, step: Float): Array<FloatArray> {
        val pieces = Array(4) { ArrayList<Float>() }
        ScnetSeparator(sharing, step).separate(mix) { stem, piece -> piece.forEach { pieces[stem.ordinal].add(it) } }
        return Array(4) { pieces[it].toFloatArray() }
    }

    private fun check(n: Int, step: Float) {
        val mix = signal(n)
        val mean = mix.average().toFloat()
        val stems = run(mix, step)
        for (s in 0 until 4) {
            assertEquals("length of stem $s for $n samples at step $step", n, stems[s].size)
            var worst = 0f
            for (i in 0 until n) worst = maxOf(worst, abs(stems[s][i] - shares[s] * (mix[i] - mean)))
            assertTrue("stem $s off by $worst for $n samples at step $step", worst < 1e-4f)
        }
    }

    @Test fun aShortClipIsOneSegment() = check(30_000, 0.5f)

    @Test fun exactlyOneSegment() = check(ScnetSeparator.SEGMENT, 0.5f)

    @Test fun twoAndAHalfSegmentsAtTheHalfStep() = check(ScnetSeparator.SEGMENT * 5 / 2, 0.5f)

    @Test fun threeSegmentsAtTheReferenceStep() = check(ScnetSeparator.SEGMENT * 3, 0.25f)

    @Test fun segmentCountFollowsTheStep() {
        val half = ScnetSeparator(sharing, 0.5f)
        assertEquals(1, half.segmentCount(1))
        assertEquals(1, half.segmentCount(ScnetSeparator.SEGMENT))
        assertEquals(2, half.segmentCount(ScnetSeparator.SEGMENT + 1))
        // Long audio is reflected out by a segment less a step on each side.
        val long = ScnetSeparator.SEGMENT * 4
        assertTrue(half.segmentCount(long) > ScnetSeparator.SEGMENT * 4 / (ScnetSeparator.SEGMENT / 2))
        assertTrue(ScnetSeparator(sharing, 0.25f).segmentCount(long) > half.segmentCount(long))
    }

    @Test fun progressReportsEverySegmentAndStemsComeInTimeOrder() {
        val separator = ScnetSeparator(sharing, 0.5f)
        val mix = signal(ScnetSeparator.SEGMENT * 2)
        val seen = ArrayList<Pair<Int, Int>>()
        val perStem = IntArray(4)
        separator.separate(mix, { done, total -> seen += done to total }) { stem, piece -> perStem[stem.ordinal] += piece.size }
        assertEquals((1..separator.segmentCount(mix.size)).map { it to separator.segmentCount(mix.size) }, seen)
        assertEquals(List(4) { mix.size }, perStem.toList())
        assertEquals(Stem.entries.size, 4)
    }

    @Test fun cancellationStopsBetweenSegments() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(CancellationException::class.java) { ScnetSeparator(sharing).separate(signal(1000)) { _, _ -> } }
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun emptyAudioIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScnetSeparator(sharing).separate(FloatArray(0)) { _, _ -> } }
    }
}
