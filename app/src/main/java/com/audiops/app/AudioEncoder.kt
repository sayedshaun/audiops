package com.audiops.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Saves Audiops' WAV outputs as M4A (AAC-LC) or Opus with the device's own MediaCodec encoders,
 * streaming the audio through the encoder so memory use stays flat. Android has no MP3 encoder.
 */
internal object AudioEncoder {
    private const val TIMEOUT_US = 10_000L
    private val opusRates = setOf(8_000, 12_000, 16_000, 24_000, 48_000)

    /** WAV always; M4A on every device; Opus on Android 10+ when the device has an Opus encoder. */
    val formats: List<SaveFormat> = buildList {
        add(SaveFormat.WAV)
        if (hasEncoder(MediaFormat.MIMETYPE_AUDIO_AAC)) add(SaveFormat.M4A)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasEncoder(MediaFormat.MIMETYPE_AUDIO_OPUS)) add(SaveFormat.OPUS)
    }

    private fun hasEncoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }

    fun encode(context: Context, wav: File, format: SaveFormat, destination: Uri) {
        require(format in formats && format != SaveFormat.WAV) { "This device cannot save $format." }
        val opus = format == SaveFormat.OPUS
        val mime = if (opus) MediaFormat.MIMETYPE_AUDIO_OPUS else MediaFormat.MIMETYPE_AUDIO_AAC
        val rate = PcmSource(wav).use { if (opus && it.sourceRate !in opusRates) 48_000 else it.sourceRate }
        // Opus only takes certain rates; anything else is converted to 48 kHz.
        PcmSource(wav, targetRate = rate).use { run(context, it, mime, rate, opus, destination) }
    }

    private fun run(context: Context, source: PcmSource, mime: String, rate: Int, opus: Boolean, destination: Uri) {
        val format = MediaFormat.createAudioFormat(mime, rate, 1).apply {
            setInteger(
                MediaFormat.KEY_BIT_RATE,
                if (opus) {
                    64_000
                } else if (rate >= 32_000) {
                    128_000
                } else {
                    96_000
                },
            )
            if (!opus) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        val descriptor = context.contentResolver.openFileDescriptor(destination, "w") ?: error("Could not open the destination.")
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val container = if (opus) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            muxer = MediaMuxer(descriptor.fileDescriptor, container)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var inputDone = false
            var outputDone = false
            var samplesFed = 0L
            while (!outputDone) {
                throwIfCancelled()
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index) ?: error("No encoder input buffer.")
                        buffer.clear()
                        val bytes = source.read(buffer)
                        val timeUs = samplesFed * 1_000_000L / rate
                        if (bytes <= 0) {
                            codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, bytes, timeUs, 0)
                            samplesFed += bytes / 2
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    out >= 0 -> {
                        val data: ByteBuffer? = codec.getOutputBuffer(out)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted && data != null) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { descriptor.close() }
        }
    }
}
