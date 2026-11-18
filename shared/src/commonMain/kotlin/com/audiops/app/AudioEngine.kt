package com.audiops.app

/**
 * Platform boundary for audio work. The Android app supplies the implementation
 * (most of it shared through `JvmAudioEngine`); shared code only talks to this.
 *
 * Everything except the `supports*` properties is blocking and is called on a worker
 * thread via [runOnWorker]; implementations stop promptly when the thread is interrupted.
 * Sources are platform document ids (content URIs on Android).
 */
interface AudioEngine {
    /** Whether this platform can run AI enhancement. */
    val supportsEnhance: Boolean

    /** Whether this platform can run transcription. */
    val supportsTranscribe: Boolean

    /** Whether this platform can run text to speech. */
    val supportsSpeak: Boolean

    /** Whether this platform can separate music into stems. */
    val supportsSeparate: Boolean get() = false

    /**
     * Splits music into stems. Returns the paths of one WAV file per [SeparatedStem], in that order,
     * in app storage.
     */
    fun separate(source: String, listener: ProcessingListener): List<String> = throw UnsupportedOnPlatformException()

    /** AI enhancement. Returns the path of a WAV file in app storage. */
    fun enhance(source: String, listener: ProcessingListener): String

    /**
     * Speech to text with [model]. [language] is a Whisper code (`bn`, `ur`) or empty for automatic
     * detection. Returns the path of a UTF-8 text file in app storage.
     */
    fun transcribe(source: String, language: String, model: TranscriptionModel, listener: ProcessingListener): String

    /** Formats [saveAudio] can write on this platform (WAV is always first). */
    val saveFormats: List<SaveFormat> get() = listOf(SaveFormat.WAV)

    /** Saves the WAV file at [path] to [destination] as [format]. Encoding is done as it is written. */
    fun saveAudio(path: String, format: SaveFormat, destination: String) {
        if (format == SaveFormat.WAV) copyFile(path, destination) else throw UnsupportedOnPlatformException()
    }

    /** The SRT subtitle file stored next to the transcript at [transcriptPath], or null if there is none. */
    fun subtitlesFor(transcriptPath: String): String? = null

    /** Text to speech with the voice [voiceId] ([TtsVoice.id]). Returns the path of a WAV file in app storage. */
    fun speak(text: String, voiceId: String, speed: Float, listener: ProcessingListener): String

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

    /** Bytes used by each downloaded model; models that are not downloaded are absent. */
    fun modelStorage(): Map<AiModel, Long>

    /** Deletes one downloaded model; it downloads again on next use. */
    fun deleteModel(model: AiModel)

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
