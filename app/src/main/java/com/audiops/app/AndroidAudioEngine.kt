package com.audiops.app

import android.content.Context
import androidx.core.net.toUri
import java.io.File

/**
 * Android engine: MediaCodec decoding, LiteRT enhancement and sherpa-onnx transcription.
 * DSP tools, analysis, waveform and storage come from [JvmAudioEngine].
 */
class AndroidAudioEngine(context: Context) : JvmAudioEngine(
    workDir = File(context.applicationContext.cacheDir, "work"),
    outputDir = File(context.applicationContext.filesDir, "outputs"),
    modelRoot = context.applicationContext.noBackupFilesDir,
) {
    private val context = context.applicationContext

    override val supportsEnhance: Boolean = true
    override val supportsTranscribe: Boolean = true

    override fun decode(source: String): File = AudioDecoder.decode(context, source.toUri(), workDir)

    /** Runs in the isolated `:enhance` process (see [EnhanceService]) so a memory kill cannot close the app. */
    override fun enhance(source: String, listener: ProcessingListener): String = withDecoded(source) { pcm ->
        val output = newOutput("wav")
        EnhanceClient.run(context, pcm, output, listener)
        output.absolutePath
    }

    override fun transcribe(source: String, language: String, listener: ProcessingListener): String =
        withDecoded(source) { pcm -> storeTranscript(AudioPipelines.transcribe(context, pcm, language, listener)) }

    override fun copyFile(path: String, destination: String) {
        context.contentResolver.openOutputStream(destination.toUri())
            ?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
            ?: error("Could not open the destination.")
    }

    override fun writeText(text: String, destination: String) {
        context.contentResolver.openOutputStream(destination.toUri())
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { it.write(text) }
            ?: error("Could not open the destination.")
    }
}
