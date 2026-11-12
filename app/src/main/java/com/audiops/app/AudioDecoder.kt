package com.audiops.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/** Sample rate of every intermediate file: the enhancement model's native rate. */
internal const val PIPELINE_RATE = 44_100

private const val ENHANCE_WINDOW = PIPELINE_RATE * 20
private const val ENHANCE_OVERLAP = PIPELINE_RATE

/**
 * Throws [CancellationException] once the worker thread has been interrupted.
 * `runInterruptible` interrupts the thread when its coroutine is cancelled, so long
 * loops call this between units of work.
 */
internal fun throwIfCancelled() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("Processing cancelled")
}

/** Decodes device-supported formats into temporary mono 44.1 kHz PCM16 files. */
internal object AudioDecoder {
    /**
     * Returns a raw PCM16 file in the cache directory. The caller owns it and must delete it.
     */
    fun decode(context: Context, uri: Uri): File {
        val file = File.createTempFile("audio-input-", ".pcm", context.cacheDir)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This file does not contain a supported audio track.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            started = true
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var sourceIndex = 0L
            var nextOutput = 0.0
            var previous = 0f
            var written = 0L
            BufferedOutputStream(file.outputStream()).use { output ->
                val bytes = ByteArray(8192)
                var used = 0
                fun emit(value: Float) {
                    // WAV uses 32-bit chunk sizes; stop before exceeding them.
                    check(written < (0xffff_ffffL - 36L) / 2L) { "Audio is too long to save as WAV." }
                    val v = (value.coerceIn(-1f, 1f) * 32767f).roundToInt()
                    bytes[used++] = v.toByte()
                    bytes[used++] = (v shr 8).toByte()
                    if (used == bytes.size) {
                        output.write(bytes)
                        used = 0
                    }
                    written++
                }
                while (!outputEnded) {
                    throwIfCancelled()
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index) ?: error("Cannot read audio input.")
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val out = codec.outputFormat
                            val newChannels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val newRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            check(sourceIndex == 0L || (newChannels == channels && newRate == sampleRate)) {
                                "Audio format changed during playback."
                            }
                            channels = newChannels
                            sampleRate = newRate
                            require(channels in 1..32 && sampleRate > 0) { "Unsupported audio channel or sample rate." }
                            encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                        }
                        // INFO_TRY_AGAIN_LATER and other negative codes need no action; the loop retries.
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (index >= 0) {
                            val buffer = codec.getOutputBuffer(index) ?: error("Cannot decode audio output.")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val sampleBytes = when (encoding) {
                                AudioFormat.ENCODING_PCM_8BIT -> 1
                                AudioFormat.ENCODING_PCM_16BIT -> 2
                                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                else -> error("This audio encoding is not supported by the device decoder.")
                            }
                            require(buffer.remaining() % (sampleBytes * channels) == 0) { "Incomplete decoded audio frame." }
                            while (buffer.remaining() >= sampleBytes * channels) {
                                // Downmix to mono by averaging the channels of each frame.
                                var sum = 0f
                                repeat(channels) {
                                    val value = when (encoding) {
                                        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128f
                                        AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32768f
                                        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
                                        AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2147483648f
                                        else -> {
                                            val x = (buffer.get().toInt() and 255) or
                                                ((buffer.get().toInt() and 255) shl 8) or
                                                (buffer.get().toInt() shl 16)
                                            x / 8388608f
                                        }
                                    }
                                    require(value.isFinite()) { "Audio contains invalid samples." }
                                    sum += value / channels
                                }
                                // Linear resampling onto the 44.1 kHz output grid.
                                if (sourceIndex == 0L) {
                                    emit(sum)
                                    nextOutput = sampleRate.toDouble() / PIPELINE_RATE
                                } else {
                                    while (nextOutput <= sourceIndex) {
                                        val fraction = (nextOutput - (sourceIndex - 1)).toFloat().coerceIn(0f, 1f)
                                        emit(previous + (sum - previous) * fraction)
                                        nextOutput += sampleRate.toDouble() / PIPELINE_RATE
                                    }
                                }
                                previous = sum
                                sourceIndex++
                            }
                            outputEnded = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                if (used > 0) output.write(bytes, 0, used)
            }
            require(written > 0) { "The selected audio is empty." }
            return file
        } catch (e: Exception) {
            file.delete()
            // Cancellation and our own validation messages are passed through unchanged.
            if (e is CancellationException || e is IllegalArgumentException || e is IllegalStateException) throw e
            throw IllegalArgumentException("Could not decode this audio file on this device: ${e.localizedMessage}", e)
        } finally {
            if (started) runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

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
