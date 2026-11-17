package com.audiops.app

import android.content.Context
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Text to speech. Supertonic 3 voices (Supertone, INT8, 31 languages) and Piper voices run through
 * sherpa-onnx (Piper with espeak-ng, whose data ships in the APK and is unpacked on first use);
 * PocketTTS voices (Kyutai, INT8) run through MediaPipe's TextToSpeech task. Text is synthesized in sentence groups ([TextChunks]) so progress
 * shows and Cancel takes effect between them. The engine is released after each job.
 */
internal object SpeechSynthesizer {
    private const val GAP_SECONDS = 0.15f

    /** Denoising steps per Supertonic sentence group; more is slower with little gain. */
    private const val SUPERTONIC_STEPS = 5

    private const val ESPEAK_ASSET = "espeak-ng-data.zip"

    /** Bump when the bundled espeak-ng-data.zip changes, so it is unpacked again. */
    private const val ESPEAK_VERSION = "1"

    fun speak(context: Context, text: String, voice: TtsVoice, speed: Float, output: File, listener: ProcessingListener) {
        val directory = ModelFiles.ensure(
            context.noBackupFilesDir,
            folder(voice),
            "https://huggingface.co/${voice.repo}/resolve/${voice.revision}",
            voice.files,
            listener,
        )
        val pieces = TextChunks.split(text)
        require(pieces.isNotEmpty()) { "There is no text to read." }
        throwIfCancelled()
        when (voice.engine) {
            TtsEngine.POCKET -> pocket(context, directory, voice, speed, pieces, output, listener)
            TtsEngine.PIPER -> piper(context, directory, voice, speed, pieces, output, listener)
            TtsEngine.SUPERTONIC -> supertonic(directory, voice, speed, pieces, output, listener)
        }
    }

    /** Where a voice's files live under the model root (see `ModelFiles` for Settings → Downloaded models). */
    private fun folder(voice: TtsVoice): String = when (voice.engine) {
        // One folder for every PocketTTS language file, so they share a revision.
        TtsEngine.POCKET -> "pocket-tts/${voice.revision}"

        TtsEngine.PIPER -> "piper-voices/${voice.repo.substringAfter('/')}/${voice.revision}"

        // One folder for every Supertonic language and voice.
        TtsEngine.SUPERTONIC -> "supertonic-3/${voice.revision}"
    }

    private fun pocket(
        context: Context,
        directory: File,
        voice: TtsVoice,
        speed: Float,
        pieces: List<String>,
        output: File,
        listener: ProcessingListener,
    ) {
        val model = File(directory, voice.files.keys.single())
        val voiceId = voice.id.removePrefix("pocket-")
        val options = TextToSpeechOptions(
            baseOptions = BaseOptions.builder().setModelAssetPath(model.absolutePath).setDelegate(Delegate.CPU).build(),
            defaultVoiceId = voiceId,
            defaultLocale = voice.language,
            speakingRate = speed,
            cacheDir = File(context.cacheDir, "mediapipe-tts").apply { mkdirs() }.absolutePath,
        )
        TextToSpeech.create(options).use { tts ->
            val synthesis = SynthesisOptions(voiceId = voiceId, locale = voice.language)
            write(output, pieces, listener) { piece ->
                val result = tts.synthesize(piece, synthesis)
                result.samples to result.sampleRateHz
            }
        }
    }

    private fun supertonic(
        directory: File,
        voice: TtsVoice,
        speed: Float,
        pieces: List<String>,
        output: File,
        listener: ProcessingListener,
    ) {
        fun path(name: String) = File(directory, name).absolutePath
        val tts = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = path("duration_predictor.int8.onnx"),
                        textEncoder = path("text_encoder.int8.onnx"),
                        vectorEstimator = path("vector_estimator.int8.onnx"),
                        vocoder = path("vocoder.int8.onnx"),
                        ttsJson = path("tts.json"),
                        unicodeIndexer = path("unicode_indexer.bin"),
                        voiceStyle = path("voice.bin"),
                    ),
                    numThreads = 4,
                    provider = "cpu",
                ),
            ),
        )
        try {
            // The language code goes to the model in `extra`; the voice is one of its ten preset styles.
            val generation = GenerationConfig(
                speed = speed,
                sid = supertonicSpeaker(voice),
                numSteps = SUPERTONIC_STEPS,
                extra = mapOf("lang" to voice.id.removePrefix("supertonic-").substringBefore('-')),
            )
            write(output, pieces, listener) { piece ->
                val audio = tts.generateWithConfig(piece, generation)
                audio.samples to audio.sampleRate
            }
        } finally {
            tts.release()
        }
    }

    private fun piper(
        context: Context,
        directory: File,
        voice: TtsVoice,
        speed: Float,
        pieces: List<String>,
        output: File,
        listener: ProcessingListener,
    ) {
        val model = voice.files.keys.single { it.endsWith(".onnx") }
        val tts = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(directory, model).absolutePath,
                        tokens = File(directory, "tokens.txt").absolutePath,
                        dataDir = espeakData(context).absolutePath,
                    ),
                    numThreads = 4,
                    provider = "cpu",
                ),
            ),
        )
        try {
            write(output, pieces, listener) { piece ->
                val audio = tts.generate(piece, 0, speed)
                audio.samples to audio.sampleRate
            }
        } finally {
            tts.release()
        }
    }

    /** Synthesizes each piece in turn and appends it, with a short pause, to one WAV file. */
    private fun write(output: File, pieces: List<String>, listener: ProcessingListener, synthesize: (String) -> Pair<FloatArray, Int>) {
        var writer: WavWriter? = null
        try {
            pieces.forEachIndexed { index, piece ->
                throwIfCancelled()
                listener.onSection(index + 1, pieces.size)
                val (samples, rate) = synthesize(piece)
                val wav = writer ?: WavWriter(output, rate).also { writer = it }
                if (index > 0) wav.append(FloatArray((rate * GAP_SECONDS).toInt()))
                wav.append(samples)
            }
            checkNotNull(writer) { "No audio was produced." }
        } finally {
            writer?.close()
        }
    }

    /** Unpacks the bundled espeak-ng data (GPL-3.0) once; it is shared by every Piper voice. */
    private fun espeakData(context: Context): File {
        val root = File(context.noBackupFilesDir, "espeak-ng-data")
        val marker = File(root, ".complete-$ESPEAK_VERSION")
        if (marker.isFile) return File(root, "espeak-ng-data")
        root.deleteRecursively()
        check(root.mkdirs()) { "Could not create speech data storage." }
        val base = root.canonicalPath + File.separator
        ZipInputStream(context.assets.open(ESPEAK_ASSET).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name)
                check(target.canonicalPath.startsWith(base)) { "Invalid speech data." }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        marker.createNewFile()
        return File(root, "espeak-ng-data")
    }
}
