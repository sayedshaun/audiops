package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * The two small models a live meeting needs besides speech recognition, both run through
 * sherpa-onnx and pinned to a Hugging Face commit:
 *
 * - Silero VAD v5 (MIT, Silero Team): finds where speech starts and stops.
 * - CAM++ speaker verification, Chinese and English, 200k speakers (Apache License 2.0,
 *   Alibaba 3D-Speaker): a voice embedding per utterance, to tell speakers apart.
 */
internal object MeetingModels {
    private const val VAD_REVISION = "193243f7d961b15e6de789d3f90cb0ee867e7b62"
    private const val VAD_URL = "https://huggingface.co/deepghs/silero-vad-onnx/resolve/$VAD_REVISION"
    private const val VAD_FILE = "silero_vad.onnx"
    private val vadDigests = mapOf(VAD_FILE to "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f")

    private const val SPEAKER_REVISION = "a8055d6daef723a6a4ecf4de233708575bf4994f"
    private const val SPEAKER_URL = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/$SPEAKER_REVISION"
    private const val SPEAKER_FILE = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"
    private val speakerDigests = mapOf(SPEAKER_FILE to "53cab20ba8f2eb2f6e2d89ecc18e88ef3222169b61b4419cbc13666744a4574b")

    /** Speech shorter than this is ignored (clicks, coughs). */
    private const val MIN_SPEECH_SECONDS = 0.25f

    /** A pause this long ends an utterance. */
    private const val MIN_SILENCE_SECONDS = 0.6f

    /** Longer speech is cut here, so text keeps appearing while someone talks at length. */
    private const val MAX_SPEECH_SECONDS = 12f

    /** [MAX_SPEECH_SECONDS] in milliseconds: an utterance this long was cut, not ended by a pause. */
    const val MAX_SPEECH_MS = (MAX_SPEECH_SECONDS * 1000).toLong()

    fun vad(context: Context, listener: ProcessingListener): Vad {
        val directory = ModelFiles.ensure(context.noBackupFilesDir, "silero-vad/$VAD_REVISION", VAD_URL, vadDigests, listener)
        return Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = File(directory, VAD_FILE).absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = MIN_SILENCE_SECONDS,
                    minSpeechDuration = MIN_SPEECH_SECONDS,
                    windowSize = 512,
                    maxSpeechDuration = MAX_SPEECH_SECONDS,
                ),
                sampleRate = MEETING_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
    }

    fun speakerExtractor(context: Context, listener: ProcessingListener): SpeakerEmbeddingExtractor {
        val directory = ModelFiles.ensure(
            context.noBackupFilesDir,
            "speaker-embedding/$SPEAKER_REVISION",
            SPEAKER_URL,
            speakerDigests,
            listener,
        )
        return SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(model = File(directory, SPEAKER_FILE).absolutePath, numThreads = 2, provider = "cpu"),
        )
    }
}
