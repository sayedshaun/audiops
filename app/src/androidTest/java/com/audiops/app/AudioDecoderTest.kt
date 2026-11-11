package com.audiops.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioDecoderTest {
    @Test fun decodesCommonFormatsAndSampleRates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (name in listOf("stereo-48k.mp3", "stereo-48k.m4a", "mono-16k.wav")) {
            val source = File(context.cacheDir, "fixture-$name")
            instrumentation.context.assets.open(name).use { input -> source.outputStream().use { input.copyTo(it) } }
            val decoded = AudioDecoder.decode(context, Uri.fromFile(source))
            try {
                var count = 0
                var peak = 0f
                AudioDecoder.forEachChunk(decoded, 20) { samples ->
                    count += samples.size
                    samples.forEach { peak = maxOf(peak, abs(it)) }
                }
                assertTrue("$name duration: $count samples", count in 84_000..93_000)
                assertTrue("$name contains audible signal: $peak", peak > 0.02f)
            } finally { decoded.delete(); source.delete() }
        }
    }

    @Test fun longAudioIsSplitWithCorrectOverlap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("long-test", ".pcm", context.cacheDir)
        try {
            file.outputStream().use { out ->
                val second = ByteArray(44_100 * 2)
                repeat(22) { out.write(second) }
            }
            val transcribeSizes = mutableListOf<Int>()
            AudioDecoder.forEachChunk(file, 20) { transcribeSizes.add(it.size) }
            assertEquals(listOf(20 * 44_100, 2 * 44_100), transcribeSizes)
            val enhanceSizes = mutableListOf<Pair<Int, Boolean>>()
            AudioDecoder.forEachEnhancementChunk(file) { samples, last -> enhanceSizes.add(samples.size to last) }
            assertEquals(listOf(20 * 44_100 to false, 3 * 44_100 to true), enhanceSizes)
        } finally { file.delete() }
    }
}
