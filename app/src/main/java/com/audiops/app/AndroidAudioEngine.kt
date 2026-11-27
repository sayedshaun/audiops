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
    override val supportsIdentify: Boolean = true

    override fun identify(source: String, listener: ProcessingListener): SoundProfile =
        withDecoded(source) { pcm -> SoundTagger.identify(context, pcm, listener) }

    /** Adds the main sounds to the measurements, when the sound model can be had (it is small; offline, they are left out). */
    override fun analyze(source: String, listener: ProcessingListener): AudioReport {
        val report = super.analyze(source, listener)
        val sounds = runCatching { identify(source, ProcessingListener.None).tags }
            .onFailure { if (it is java.util.concurrent.CancellationException || it is InterruptedException) throw it }
            .getOrDefault(emptyList())
        return report.copy(sounds = sounds)
    }

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

    /**
     * Models that punctuate transcribe in fixed sections, as before. With speakers, or with a model
     * that writes no punctuation, the recording is cut at pauses instead: each utterance gets a
     * speaker, or becomes a sentence.
     */
    override fun transcribe(
        source: String,
        language: String,
        model: TranscriptionModel,
        speakers: Boolean,
        speakerName: (Int) -> String,
        listener: ProcessingListener,
    ): String = withDecoded(source) { pcm ->
        if (!speakers && model.punctuates) {
            storeTranscript(AudioPipelines.transcribe(context, pcm, language, model, listener))
        } else {
            val (lines, speakerVoices) = AudioPipelines.transcribeUtterances(context, pcm, language, model, speakers, listener)
            val finished = if (model.punctuates) lines else SpeechSentences.punctuate(lines, language, MeetingModels.MAX_SPEECH_MS)
            if (speakers) {
                storeSpeakerTranscript(SpeakerTranscript.of(finished, speakerVoices, voices), speakerName)
            } else {
                storeTranscript(SpeechTranscription.plain(finished, language))
            }
        }
    }

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
