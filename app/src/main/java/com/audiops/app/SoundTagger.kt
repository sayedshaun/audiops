package com.audiops.app

import android.content.Context
import com.k2fsa.sherpa.onnx.AudioTagging
import com.k2fsa.sherpa.onnx.AudioTaggingConfig
import com.k2fsa.sherpa.onnx.AudioTaggingModelConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerAudioTaggingModelConfig
import java.io.File

/**
 * Tells what a recording sounds like: k2-fsa's small Zipformer audio tagger (AudioSet's 527
 * classes, INT8, about 27 MB, Apache License 2.0) through sherpa-onnx, over a few windows of
 * the recording ([SoundTags]). Class names come from AudioSet's label list (Google, CC BY 4.0).
 */
internal object SoundTagger {
    private const val REVISION = "c24f9d0fdf7d0b8c0c4f9733aa0cac51fda10c95"
    private const val BASE_URL = "https://huggingface.co/k2-fsa/sherpa-onnx-zipformer-small-audio-tagging-2024-04-15/resolve/$REVISION"
    private const val MODEL = "model.int8.onnx"
    private const val LABELS = "class_labels_indices.csv"
    private val digests = mapOf(
        MODEL to "69304b8a1b96bbe6b7d16c24079f0732c65faf3568a14cb82a8238c709afe76c",
        LABELS to "cdd1049833c4b86127c2773ac0d14a2754b6a6d0d1798002ed5c66e699708429",
    )

    /** Classes scored per window; enough to cover the noise classes [SoundTags] adds up. */
    private const val TOP_K = 40

    /** Folder under the model root (see `ModelFiles` for Settings → Downloaded models). */
    const val FOLDER = "audio-tagging"

    fun identify(context: Context, audio: File, listener: ProcessingListener): SoundProfile {
        val windows = SoundTags.windows(audio)
        if (windows.isEmpty()) return SoundProfile(SoundKind.OTHER, emptyList())
        val directory = ModelFiles.ensure(context.noBackupFilesDir, "$FOLDER/$REVISION", BASE_URL, digests, listener)
        val tagger = AudioTagging(
            config = AudioTaggingConfig(
                model = AudioTaggingModelConfig(
                    zipformer = OfflineZipformerAudioTaggingModelConfig(model = File(directory, MODEL).absolutePath),
                    numThreads = 2,
                    provider = "cpu",
                ),
                labels = File(directory, LABELS).absolutePath,
                topK = TOP_K,
            ),
        )
        try {
            val scores = windows.mapIndexed { index, samples ->
                throwIfCancelled()
                listener.onSection(index + 1, windows.size)
                val stream = tagger.createStream()
                try {
                    stream.acceptWaveform(samples, MEETING_RATE)
                    tagger.compute(stream, TOP_K).associate { it.name to it.prob }
                } finally {
                    stream.release()
                }
            }
            return SoundTags.profile(SoundTags.average(scores))
        } finally {
            tagger.release()
        }
    }
}
