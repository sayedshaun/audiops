package com.audiops.app

import android.content.Context
import androidx.core.net.toUri
import java.io.File

/** Android audio engine: MediaCodec decoding, LiteRT enhancement, sherpa-onnx transcription. */
class AndroidAudioEngine(context: Context) : AudioEngine {
    private val context = context.applicationContext

    override val supportsEnhance: Boolean = true
    override val supportsTranscribe: Boolean = true

    override fun enhance(source: String, listener: ProcessingListener): String =
        AudioPipelines.enhance(context, source.toUri(), listener).absolutePath

    override fun transcribe(source: String, language: String, listener: ProcessingListener): String =
        AudioPipelines.transcribe(context, source.toUri(), language, listener)

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

    override fun deleteTemporary(path: String) {
        File(path).delete()
    }

    override fun modelStorageBytes(): Long = ModelFiles.storageBytes(context.noBackupFilesDir)

    override fun deleteModels() = ModelFiles.deleteAll(context.noBackupFilesDir)
}
