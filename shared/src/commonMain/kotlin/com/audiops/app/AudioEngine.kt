package com.audiops.app

/**
 * Platform boundary for audio work. Each platform supplies one implementation: Android
 * (MediaCodec, LiteRT, sherpa-onnx) today, desktop later. Shared code only talks to this.
 *
 * All methods except the `supports*` properties are blocking and are called on a worker
 * thread via `runInterruptible`; implementations must stop promptly when the thread is
 * interrupted (see `throwIfCancelled`).
 */
interface AudioEngine {
    /** Whether this platform can run enhancement. */
    val supportsEnhance: Boolean

    /** Whether this platform can run transcription. */
    val supportsTranscribe: Boolean

    /**
     * Enhances the recording identified by [source] (a platform document id: a content URI
     * on Android, a file path on desktop) and returns the path of a temporary WAV file.
     */
    fun enhance(source: String, listener: ProcessingListener): String

    /** Transcribes [source]. [language] is `bn` for Bangla, a Whisper code, or empty for auto. */
    fun transcribe(source: String, language: String, listener: ProcessingListener): String

    /** Copies the file at [path] to the user-chosen [destination] (platform document id). */
    fun copyFile(path: String, destination: String)

    /** Writes [text] as UTF-8 to the user-chosen [destination]. */
    fun writeText(text: String, destination: String)

    /** Deletes a temporary file created by this engine. */
    fun deleteTemporary(path: String)

    /** Bytes used by downloaded models. */
    fun modelStorageBytes(): Long

    /** Deletes every downloaded model; they download again on next use. */
    fun deleteModels()
}

/** Reports progress of long-running work. Methods are called on the worker thread. */
interface ProcessingListener {
    /** A model file is being downloaded. [index] is 1-based. */
    fun onModelDownload(index: Int, total: Int) {}

    /** A section of the recording is being processed. [index] is 1-based. */
    fun onSection(index: Int, total: Int) {}

    object None : ProcessingListener
}

/** Thrown by engines for tools the current platform does not support yet. */
class UnsupportedOnPlatformException(message: String = "Not available on this platform yet.") :
    UnsupportedOperationException(message)
