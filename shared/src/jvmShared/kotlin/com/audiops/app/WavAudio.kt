package com.audiops.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Streaming mono PCM16 WAV output. */
class WavWriter(file: File) : AutoCloseable {
    private val output = RandomAccessFile(file, "rw")
    private var bytesWritten = 0L

    init { output.setLength(0); output.write(ByteArray(44)) }

    fun append(samples: FloatArray) {
        val newSize = bytesWritten + samples.size.toLong() * 2
        require(newSize <= 0xffff_ffffL - 36L) { "Audio is too long to save as WAV." }
        val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            require(sample.isFinite()) { "Enhanced audio contains invalid samples." }
            if (buffer.remaining() < 2) { output.write(buffer.array(), 0, buffer.position()); buffer.clear() }
            buffer.putShort((sample.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        }
        output.write(buffer.array(), 0, buffer.position())
        bytesWritten = newSize
    }

    override fun close() {
        try {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt((36L + bytesWritten).toInt()).put("WAVEfmt ".toByteArray())
            header.putInt(16).putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(bytesWritten.toInt())
            output.seek(0)
            output.write(header.array())
        } finally { output.close() }
    }
}
