package com.audiops.app

import android.content.Context
import androidx.core.net.toUri
import com.audiops.app.dsp.AiDenoiser
import java.io.File

/**
 * Android engine: MediaCodec decoding, LiteRT enhancement and sherpa-onnx transcription.
 * DSP tools, analysis, waveform and storage come from [JvmAudioEngine].
 */
class AndroidAudioEngine(context: Context) :
    JvmAudioEngine(
        workDir = File(context.applicationContext.cacheDir, "work"),
        outputDir = File(context.applicationContext.filesDir, "outputs"),
        modelRoot = context.applicationContext.noBackupFilesDir,
    ) {
    private val context = context.applicationContext

    /** Noise reduction uses DPDFNet (AI) on Android instead of spectral subtraction. */
    override fun aiDenoiser(): AiDenoiser = AiDenoiser { strength, listener -> DpdfNetDenoiser.processor(context, strength, listener) }

    override val supportsEnhance: Boolean = true
    override val supportsTranscribe: Boolean = true
    override val supportsSpeak: Boolean = true
    override val supportsSeparate: Boolean = true

    override fun decode(source: String): File = AudioDecoder.decode(context, source.toUri(), workDir)

    /** Runs in the isolated `:enhance` process (see [EnhanceService]) so a memory kill cannot close the app. */
    override fun enhance(source: String, listener: ProcessingListener): String = withDecoded(source) { pcm ->
        val output = newOutput("wav")
        EnhanceClient.run(context, pcm, output, listener)
        output.absolutePath
    }

    /** Music separation also runs in the isolated worker process: one segment needs about 2 GB. */
    override fun separate(source: String, listener: ProcessingListener): List<String> = withDecoded(source) { pcm ->
        val outputs = SeparatedStem.entries.map { newOutput("wav") }
        EnhanceClient.separate(context, pcm, outputs, listener)
        outputs.map { it.absolutePath }
    }

    override fun transcribe(source: String, language: String, model: TranscriptionModel, listener: ProcessingListener): String =
        withDecoded(source) { pcm -> storeTranscript(AudioPipelines.transcribe(context, pcm, language, model, listener)) }

    override val saveFormats: List<SaveFormat> = AudioEncoder.formats

    override fun saveAudio(path: String, format: SaveFormat, destination: String) {
        if (format == SaveFormat.WAV) copyFile(path, destination) else AudioEncoder.encode(context, File(path), format, destination.toUri())
    }

    override fun speak(text: String, voiceId: String, speed: Float, listener: ProcessingListener): String {
        val voice = requireNotNull(ttsVoice(voiceId)) { "Unknown voice: $voiceId" }
        val output = newOutput("wav")
        try {
            SpeechSynthesizer.speak(context, text, voice, speed, output, listener)
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
        return output.absolutePath
    }

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
