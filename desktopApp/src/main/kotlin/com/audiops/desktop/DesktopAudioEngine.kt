package com.audiops.desktop

import com.audiops.app.JvmAudioEngine
import com.audiops.app.PIPELINE_RATE
import com.audiops.app.ProcessingListener
import com.audiops.app.UnsupportedOnPlatformException
import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.PcmFile
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import com.audiops.app.throwIfCancelled
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * Desktop (Linux, Windows) engine. Every DSP tool, analysis and the waveform work here;
 * decoding uses Java Sound, which reads WAV, AIFF and AU. AI enhancement and transcription
 * are Android-only for now (next: sherpa-onnx's JVM build, an FFmpeg-based decoder).
 */
class DesktopAudioEngine(dataDir: File) :
    JvmAudioEngine(
        workDir = File(System.getProperty("java.io.tmpdir"), "audiops-work"),
        outputDir = File(dataDir, "outputs"),
        modelRoot = File(dataDir, "models"),
    ) {
    override val supportsEnhance: Boolean = false
    override val supportsTranscribe: Boolean = false

    override fun enhance(source: String, listener: ProcessingListener): String = throw UnsupportedOnPlatformException()

    override fun transcribe(source: String, language: String, listener: ProcessingListener): String = throw UnsupportedOnPlatformException()

    override fun decode(source: String): File {
        val stream = try {
            AudioSystem.getAudioInputStream(File(source))
        } catch (e: UnsupportedAudioFileException) {
            throw UnsupportedOnPlatformException("This format is not supported on desktop yet. Use WAV or AIFF.")
        }
        val sourceFormat = stream.format
        val pcmFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sourceFormat.sampleRate,
            16,
            sourceFormat.channels,
            sourceFormat.channels * 2,
            sourceFormat.sampleRate,
            false,
        )
        val pcm = AudioSystem.getAudioInputStream(pcmFormat, stream)
        val channels = pcmFormat.channels
        val rate = pcmFormat.sampleRate.toInt()
        val resample = if (rate == PIPELINE_RATE) {
            null
        } else {
            ProcessorChain(
                listOfNotNull(
                    if (rate > PIPELINE_RATE) FilterChain.antiAlias(rate, PIPELINE_RATE) else null,
                    Resampler(rate.toDouble() / PIPELINE_RATE),
                ),
            )
        }
        val output = File.createTempFile("decoded-", ".pcm", workDir)
        try {
            PcmFile.Writer(output).use { writer ->
                pcm.use { input ->
                    val frameBytes = channels * 2
                    val bytes = ByteArray(frameBytes * 8192)
                    while (true) {
                        throwIfCancelled()
                        val read = input.readNBytes(bytes, 0, bytes.size)
                        if (read <= 0) break
                        val frames = read / frameBytes
                        val buffer = ByteBuffer.wrap(bytes, 0, frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val mono = FloatArray(frames) {
                            var sum = 0f
                            repeat(channels) { sum += buffer.short / 32768f }
                            sum / channels
                        }
                        writer.write(resample?.process(mono) ?: mono)
                        if (read < bytes.size) break
                    }
                    resample?.let { writer.write(it.flush()) }
                }
            }
            require(PcmFile.sampleCount(output) > 0) { "The selected audio is empty." }
            return output
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    override fun copyFile(path: String, destination: String) {
        File(path).copyTo(File(destination), overwrite = true)
    }

    override fun writeText(text: String, destination: String) {
        File(destination).writeText(text, Charsets.UTF_8)
    }
}
