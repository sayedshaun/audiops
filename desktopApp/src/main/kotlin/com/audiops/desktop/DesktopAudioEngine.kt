package com.audiops.desktop

import com.audiops.app.AudioEngine
import com.audiops.app.ModelFiles
import com.audiops.app.ProcessingListener
import com.audiops.app.UnsupportedOnPlatformException
import java.io.File

/**
 * Desktop (Linux, Windows) audio engine. The shared UI, settings, storage and file saving
 * already work; decoding and inference are not wired up yet, so the tools report
 * "not available on this platform yet". Next step: sherpa-onnx's JVM build for
 * transcription and a JVM decoder (for example JavaCV/FFmpeg) for input files.
 */
class DesktopAudioEngine(private val modelRoot: File) : AudioEngine {
    override val supportsEnhance: Boolean = false
    override val supportsTranscribe: Boolean = false

    override fun enhance(source: String, listener: ProcessingListener): String = throw UnsupportedOnPlatformException()

    override fun transcribe(source: String, language: String, listener: ProcessingListener): String =
        throw UnsupportedOnPlatformException()

    override fun copyFile(path: String, destination: String) {
        File(path).copyTo(File(destination), overwrite = true)
    }

    override fun writeText(text: String, destination: String) {
        File(destination).writeText(text, Charsets.UTF_8)
    }

    override fun deleteTemporary(path: String) {
        File(path).delete()
    }

    override fun modelStorageBytes(): Long = ModelFiles.storageBytes(modelRoot)

    override fun deleteModels() = ModelFiles.deleteAll(modelRoot)
}
