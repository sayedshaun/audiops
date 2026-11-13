package com.audiops.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/** Sample rate of every intermediate file: the enhancement model's native rate. */
const val PIPELINE_RATE = 44_100

private const val ENHANCE_WINDOW = PIPELINE_RATE * 20
private const val ENHANCE_OVERLAP = PIPELINE_RATE

/**
 * Throws [CancellationException] once the worker thread has been interrupted.
 * `runInterruptible` interrupts the thread when its coroutine is cancelled, so long
 * loops call this between units of work.
 */
fun throwIfCancelled() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("Processing cancelled")
}

/**
 * Reads the intermediate 44.1 kHz mono PCM16 files in sections, without holding a whole
 * recording in memory. Shared by every platform engine.
 */
object AudioChunks {
    /** Number of fixed-length sections [forEachChunk] yields for [file]. */
    fun sectionCount(file: File, seconds: Int): Int {
        require(seconds > 0)
        val window = PIPELINE_RATE.toLong() * seconds
        return ((file.length() / 2 + window - 1) / window).toInt().coerceAtLeast(1)
    }

    /** Calls [block] with consecutive sections without holding the whole recording in memory. */
    fun forEachChunk(file: File, seconds: Int, block: (FloatArray) -> Unit) {
        require(seconds > 0)
        val capacity = PIPELINE_RATE * seconds * 2
        val bytes = ByteArray(capacity)
        file.inputStream().buffered().use { input ->
            while (true) {
                throwIfCancelled()
                var size = 0
                while (size < capacity) {
                    val count = input.read(bytes, size, capacity - size)
                    if (count < 0) break
                    size += count
                }
                if (size == 0) break
                check(size % 2 == 0) { "Invalid decoded audio." }
                val buffer = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.LITTLE_ENDIAN)
                block(FloatArray(size / 2) { buffer.short / 32768f })
            }
        }
    }

    /** Number of overlapping windows [forEachEnhancementChunk] yields for [file]. */
    fun enhancementSectionCount(file: File): Int {
        val total = file.length() / 2
        val window = ENHANCE_WINDOW.toLong()
        val step = (ENHANCE_WINDOW - ENHANCE_OVERLAP).toLong()
        if (total <= window) return 1
        return (1 + (total - window + step - 1) / step).toInt()
    }

    /**
     * Calls [block] with 20-second windows that overlap by one second, so the caller can
     * crossfade the joins. [block] receives `last = true` for the final window.
     */
    fun forEachEnhancementChunk(file: File, block: (FloatArray, Boolean) -> Unit) {
        val step = ENHANCE_WINDOW - ENHANCE_OVERLAP
        RandomAccessFile(file, "r").use { input ->
            val total = input.length() / 2
            var start = 0L
            while (start < total) {
                throwIfCancelled()
                val count = minOf(ENHANCE_WINDOW.toLong(), total - start).toInt()
                val bytes = ByteArray(count * 2)
                input.seek(start * 2)
                input.readFully(bytes)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val samples = FloatArray(count) { buffer.short / 32768f }
                val last = start + count >= total
                block(samples, last)
                if (last) break
                start += step
            }
        }
    }
}
