package com.audiops.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.audiops.app.dsp.AudioProcessor
import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/** Resampling chain from [rate] to [PIPELINE_RATE], or null when no conversion is needed. */
private fun resamplerFor(rate: Int): AudioProcessor? = if (rate == PIPELINE_RATE) {
    null
} else {
    ProcessorChain(
        listOfNotNull(
            if (rate > PIPELINE_RATE) FilterChain.antiAlias(rate, PIPELINE_RATE) else null,
            Resampler(rate.toDouble() / PIPELINE_RATE),
        ),
    )
}

/** Decodes device-supported formats into temporary mono 44.1 kHz PCM16 files. */
object AudioDecoder {
    /**
     * Decodes [uri] into a new raw PCM16 file in [directory]. The caller owns the file.
     */
    fun decode(context: Context, uri: Uri, directory: File = context.cacheDir): File {
        val file = File.createTempFile("decoded-", ".pcm", directory)
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
            var resampler: AudioProcessor? = null
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
                // Feed every free input buffer and drain every ready output buffer without
                // waiting; block briefly only when neither side made progress. (Blocking on
                // each dequeue made long files decode ~10x slower than real time allows.)
                while (!outputEnded) {
                    throwIfCancelled()
                    var progressed = false
                    while (!inputEnded) {
                        val index = codec.dequeueInputBuffer(0)
                        if (index < 0) break
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
                        progressed = true
                    }
                    while (!outputEnded) {
                        val index = codec.dequeueOutputBuffer(info, if (progressed) 0L else 5_000L)
                        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
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
                            continue
                        }
                        if (index < 0) continue
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
                        val frames = buffer.remaining() / (sampleBytes * channels)
                        val mono = FloatArray(frames)
                        for (f in 0 until frames) {
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
                            mono[f] = sum
                        }
                        // Anti-aliased cubic resampling onto the 44.1 kHz grid (shared DSP).
                        if (sourceIndex == 0L) resampler = resamplerFor(sampleRate)
                        sourceIndex += frames
                        for (x in resampler?.process(mono) ?: mono) emit(x)
                        outputEnded = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(index, false)
                        progressed = true
                    }
                }
                resampler?.flush()?.forEach(::emit)
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
}
