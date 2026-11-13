package com.audiops.app

/**
 * Platform boundary for audio work. Android and desktop each supply one implementation
 * (most of it shared through `JvmAudioEngine`); shared code only talks to this.
 *
 * Everything except the `supports*` properties is blocking and is called on a worker
 * thread via [runOnWorker]; implementations stop promptly when the thread is interrupted.
 * Sources are platform document ids (content URIs on Android, file paths on desktop).
 */
interface AudioEngine {
    /** Whether this platform can run AI enhancement. */
    val supportsEnhance: Boolean

    /** Whether this platform can run transcription. */
    val supportsTranscribe: Boolean

    /** AI enhancement. Returns the path of a WAV file in app storage. */
    fun enhance(source: String, listener: ProcessingListener): String

    /** Speech to text. Returns the path of a UTF-8 text file in app storage. */
    fun transcribe(source: String, language: String, listener: ProcessingListener): String

    /** Runs DSP [operations] in order. Returns the path of a WAV file in app storage. */
    fun process(source: String, operations: List<AudioOperation>, listener: ProcessingListener): String

    /** Measures loudness, peak, clipping and silence. */
    fun analyze(source: String, listener: ProcessingListener): AudioReport

    /** Peaks for drawing [buckets] bars, plus the duration. Decodes the source (cached). */
    fun waveform(source: String, buckets: Int): WaveformData

    /** Reads a text file written by this engine. */
    fun readText(path: String): String

    /** Copies an app file to the user-chosen [destination]. */
    fun copyFile(path: String, destination: String)

    /** Writes [text] as UTF-8 to the user-chosen [destination]. */
    fun writeText(text: String, destination: String)

    /** Deletes an app file (an output or temporary file). */
    fun deleteFile(path: String)

    /** Bytes used by downloaded models. */
    fun modelStorageBytes(): Long

    /** Deletes every downloaded model; they download again on next use. */
    fun deleteModels()

    /** Bytes used by processed files kept for the Activity tab. */
    fun outputStorageBytes(): Long
}

/** Reports progress of long-running work. Methods are called on the worker thread. */
interface ProcessingListener {
    /** A model file is being downloaded. [index] is 1-based. */
    fun onModelDownload(index: Int, total: Int) {}

    /** Download progress within model file [index]: [fileFraction] in 0..1. */
    fun onModelDownloadProgress(index: Int, total: Int, fileFraction: Float) {}

    /** Work is [index] of [total] done (sections, or percent with total = 100). */
    fun onSection(index: Int, total: Int) {}

    object None : ProcessingListener
}

/** Thrown by engines for tools or formats the current platform does not support yet. */
class UnsupportedOnPlatformException(message: String = "Not available on this platform yet.") : UnsupportedOperationException(message)
