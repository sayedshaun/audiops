package com.audiops.app

import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the mono PCM16 WAV files Audiops writes and hands out little-endian PCM16 bytes for an
 * encoder, optionally converted to [targetRate]. Nothing is held in memory beyond one block.
 */
class PcmSource(file: File, targetRate: Int? = null) : AutoCloseable {
    private val input = RandomAccessFile(file, "r")
    private val remaining: Long

    /** Sample rate of the WAV file. */
    val sourceRate: Int

    /** Sample rate of the bytes returned by [read]. */
    val rate: Int
    private val converter: AudioProcessor?
    private var pending: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
    private var flushed = false

    init {
        val header = ByteArray(12)
        input.readFully(header)
        require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "Not a WAV file." }
        var rate = 0
        var channels = 0
        var bits = 0
        var dataSize = -1L
        while (dataSize < 0) {
            val chunk = ByteArray(8)
            require(input.read(chunk) == 8) { "WAV file has no audio data." }
            val id = String(chunk, 0, 4)
            val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL
            if (id == "fmt ") {
                val fmt = ByteArray(size.toInt())
                input.readFully(fmt)
                val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                b.short // format tag
                channels = b.short.toInt()
                rate = b.int
                b.int // byte rate
                b.short // block align
                bits = b.short.toInt()
            } else if (id == "data") {
                dataSize = minOf(size, input.length() - input.filePointer)
            } else {
                input.seek(input.filePointer + size + (size and 1))
            }
        }
        require(channels == 1 && bits == 16 && rate > 0) { "Only mono 16-bit WAV files can be exported." }
        sourceRate = rate
        remaining = dataSize
        this.rate = targetRate ?: rate
        converter = if (this.rate == rate) {
            null
        } else {
            ProcessorChain(
                listOfNotNull(
                    if (this.rate < rate) FilterChain.antiAlias(rate, this.rate) else null,
                    Resampler(rate.toDouble() / this.rate),
                ),
            )
        }
    }

    private var left = remaining

    /** Total samples of the WAV file at its own rate. */
    val sourceSamples: Long get() = remaining / 2

    /** Fills [buffer] (up to its remaining space) with PCM16 bytes. Returns the byte count, or -1 at the end. */
    fun read(buffer: ByteBuffer): Int {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val start = buffer.position()
        while (buffer.remaining() >= 2) {
            if (!pending.hasRemaining() && !refill()) break
            val count = minOf(buffer.remaining() and 1.inv(), pending.remaining())
            val slice = pending.slice().apply { limit(count) }
            buffer.put(slice)
            pending.position(pending.position() + count)
        }
        val written = buffer.position() - start
        return if (written == 0) -1 else written
    }

    /** Loads the next block into [pending]; false when the file and the converter are both exhausted. */
    private fun refill(): Boolean {
        while (true) {
            val samples = if (left > 0) {
                readBlock()
            } else if (!flushed) {
                flushConverter()
            } else {
                return false
            }
            if (samples.isEmpty()) continue
            pending = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) pending.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            pending.flip()
            return true
        }
    }

    private fun flushConverter(): FloatArray {
        flushed = true
        return converter?.flush() ?: FloatArray(0)
    }

    private fun readBlock(): FloatArray {
        val bytes = ByteArray(minOf(left, BLOCK_SAMPLES * 2L).toInt() and 1.inv())
        if (bytes.isEmpty()) {
            left = 0
            return FloatArray(0)
        }
        input.readFully(bytes)
        left -= bytes.size
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val block = FloatArray(bytes.size / 2) { shorts.short / 32768f }
        return converter?.process(block) ?: block
    }

    override fun close() = input.close()

    private companion object {
        const val BLOCK_SAMPLES = 4096
    }
}
