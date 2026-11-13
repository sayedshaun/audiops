package com.audiops.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Groups shown on the Tools tab. */
enum class ToolCategory { AI, CLEAN_UP, EDIT, EFFECTS, UTILITIES }

/** Every tool in the app. Labels, icons and descriptions are resolved by the UI. */
enum class Tool(val category: ToolCategory) {
    ENHANCE(ToolCategory.AI),
    TRANSCRIBE(ToolCategory.AI),
    DENOISE(ToolCategory.CLEAN_UP),
    SILENCE(ToolCategory.CLEAN_UP),
    TRIM(ToolCategory.EDIT),
    FADE(ToolCategory.EDIT),
    VOLUME(ToolCategory.EDIT),
    SPEED(ToolCategory.EFFECTS),
    EQUALIZER(ToolCategory.EFFECTS),
    CONVERT(ToolCategory.UTILITIES),
    ANALYZE(ToolCategory.UTILITIES),
}

/** Spoken languages for transcription. Labels are resolved by the UI. */
@Serializable
enum class SpeechLanguage(val code: String) { AUTO(""), BANGLA("bn"), ENGLISH("en"), HINDI("hi") }

/** Equalizer presets, each a fixed chain of filters. */
@Serializable
enum class EqPreset { VOICE_CLARITY, BASS_BOOST, TREBLE_BOOST, WARM, REMOVE_RUMBLE, TELEPHONE }

/** Loudness normalization targets. */
@Serializable
enum class LoudnessTarget(val lufs: Float) { STREAMING(-14f), PODCAST(-16f), BROADCAST(-23f) }

/** Output sample rates offered by Convert. */
val SupportedSampleRates = listOf(8_000, 16_000, 22_050, 44_100, 48_000)

/**
 * One step of DSP processing. Operations are serializable so jobs can be stored in history.
 * Times are in milliseconds on the timeline the operation receives.
 */
@Serializable
sealed interface AudioOperation {
    @Serializable
    @SerialName("trim")
    data class Trim(val startMs: Long, val endMs: Long) : AudioOperation

    @Serializable
    @SerialName("gain")
    data class Gain(val db: Float) : AudioOperation

    /** Peak normalization to [peakDb] dBFS. */
    @Serializable
    @SerialName("normalize_peak")
    data class NormalizePeak(val peakDb: Float = -1f) : AudioOperation

    /** Integrated-loudness normalization (ITU-R BS.1770) with a peak limiter. */
    @Serializable
    @SerialName("normalize_loudness")
    data class NormalizeLoudness(val target: LoudnessTarget) : AudioOperation

    @Serializable
    @SerialName("fade")
    data class Fade(val inMs: Long, val outMs: Long) : AudioOperation

    @Serializable
    @SerialName("equalizer")
    data class Equalizer(val preset: EqPreset) : AudioOperation

    /** Tempo and pitch. [speed] 1 = unchanged; [semitones] 0 = unchanged. */
    @Serializable
    @SerialName("speed_pitch")
    data class SpeedPitch(val speed: Float, val semitones: Float) : AudioOperation

    /** Spectral noise reduction. [strength] in 0..1. */
    @Serializable
    @SerialName("denoise")
    data class Denoise(val strength: Float) : AudioOperation

    @Serializable
    @SerialName("remove_silence")
    data class RemoveSilence(val thresholdDb: Float, val minSilenceMs: Long, val paddingMs: Long) : AudioOperation

    @Serializable
    @SerialName("resample")
    data class Resample(val sampleRate: Int) : AudioOperation
}

/** What a job does. */
@Serializable
sealed interface JobSpec {
    @Serializable
    @SerialName("enhance")
    data object Enhance : JobSpec

    @Serializable
    @SerialName("transcribe")
    data class Transcribe(val language: SpeechLanguage) : JobSpec

    @Serializable
    @SerialName("process")
    data class Process(val operations: List<AudioOperation>) : JobSpec

    @Serializable
    @SerialName("analyze")
    data object Analyze : JobSpec
}

@Serializable
enum class JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
    ;

    val active: Boolean get() = this == QUEUED || this == RUNNING
}

/** Why a job failed; the UI turns this into localized text. */
@Serializable
enum class FailureKind { PROCESSING, OUT_OF_MEMORY, NO_SPEECH, UNSUPPORTED, INTERRUPTED }

/** Measurements reported by Analyze. */
@Serializable
data class AudioReport(
    val durationMs: Long,
    val sampleRate: Int,
    val peakDb: Float,
    val rmsDb: Float,
    /** Integrated loudness (ITU-R BS.1770), or null when the audio is too quiet or short. */
    val loudnessLufs: Float?,
    val clippedSamples: Long,
    val dcOffset: Float,
    /** Share of 10 ms frames below -50 dBFS, 0..1. */
    val silenceRatio: Float,
)

/** Where a running job is. Not persisted. [subFraction] is progress within item [index]. */
data class JobProgress(val stage: Stage, val index: Int = 0, val total: Int = 0, val subFraction: Float = 0f) {
    /** Fraction done, or null while unknown. */
    val fraction: Float? get() = if (total > 0) ((index - 1 + subFraction) / total).coerceIn(0f, 1f) else null
}

enum class Stage { QUEUED, PREPARING, DOWNLOADING_MODEL, PROCESSING }

/** A unit of work and, once finished, its result. Persisted as history. */
@Serializable
data class Job(
    val id: String,
    val tool: Tool,
    val inputId: String,
    val inputName: String,
    val spec: JobSpec,
    val status: JobStatus,
    val createdAt: Long,
    val finishedAt: Long? = null,
    /** Processed audio (WAV) or, for transcription, the transcript text file. */
    val outputPath: String? = null,
    /** First characters of a transcript, for lists. */
    val preview: String? = null,
    val report: AudioReport? = null,
    val failure: FailureKind? = null,
    val failureDetail: String? = null,
    @Transient val progress: JobProgress? = null,
)

/** A recording chosen by the user. [id] is a platform document id (URI or file path). */
data class AudioInput(val id: String, val name: String)

/** Downsampled peaks for drawing a waveform, plus the recording length. */
data class WaveformData(val peaks: FloatArray, val durationMs: Long) {
    override fun equals(other: Any?): Boolean = other is WaveformData && durationMs == other.durationMs && peaks.contentEquals(other.peaks)

    override fun hashCode(): Int = 31 * peaks.contentHashCode() + durationMs.hashCode()
}

/** One-shot snackbar messages. */
sealed interface UiMessage {
    data class JobFinished(val tool: Tool) : UiMessage
    data class JobFailed(val tool: Tool) : UiMessage
    data object Saved : UiMessage
    data class SaveFailed(val detail: String?) : UiMessage
    data object Cancelled : UiMessage
    data object ModelsDeleted : UiMessage
    data object HistoryCleared : UiMessage
    data object Copied : UiMessage
}
