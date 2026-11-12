package com.audiops.app.ui

import androidx.compose.runtime.Composable
import com.audiops.app.AudioOperation
import com.audiops.app.EqPreset
import com.audiops.app.FailureKind
import com.audiops.app.InputProblem
import com.audiops.app.Job
import com.audiops.app.JobSpec
import com.audiops.app.JobStatus
import com.audiops.app.LoudnessTarget
import com.audiops.app.SpeechLanguage
import com.audiops.app.Stage
import com.audiops.app.Tool
import com.audiops.app.ToolCategory
import com.audiops.app.UiMessage
import com.audiops.app.currentTimeMillis
import com.audiops.shared.resources.*
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

val SpeechLanguage.label: StringResource
    get() = when (this) {
        SpeechLanguage.AUTO -> Res.string.language_auto
        SpeechLanguage.BANGLA -> Res.string.language_bangla
        SpeechLanguage.ENGLISH -> Res.string.language_english
        SpeechLanguage.HINDI -> Res.string.language_hindi
    }

val Tool.title: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.tool_enhance_title
        Tool.TRANSCRIBE -> Res.string.tool_transcribe_title
        Tool.DENOISE -> Res.string.tool_denoise_title
        Tool.SILENCE -> Res.string.tool_silence_title
        Tool.TRIM -> Res.string.tool_trim_title
        Tool.FADE -> Res.string.tool_fade_title
        Tool.VOLUME -> Res.string.tool_volume_title
        Tool.SPEED -> Res.string.tool_speed_title
        Tool.EQUALIZER -> Res.string.tool_equalizer_title
        Tool.CONVERT -> Res.string.tool_convert_title
        Tool.ANALYZE -> Res.string.tool_analyze_title
    }

val Tool.description: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.tool_enhance_description
        Tool.TRANSCRIBE -> Res.string.tool_transcribe_description
        Tool.DENOISE -> Res.string.tool_denoise_description
        Tool.SILENCE -> Res.string.tool_silence_description
        Tool.TRIM -> Res.string.tool_trim_description
        Tool.FADE -> Res.string.tool_fade_description
        Tool.VOLUME -> Res.string.tool_volume_description
        Tool.SPEED -> Res.string.tool_speed_description
        Tool.EQUALIZER -> Res.string.tool_equalizer_description
        Tool.CONVERT -> Res.string.tool_convert_description
        Tool.ANALYZE -> Res.string.tool_analyze_description
    }

/** Primary button label on the tool screen. */
val Tool.action: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.action_enhance
        Tool.TRANSCRIBE -> Res.string.action_transcribe
        Tool.ANALYZE -> Res.string.action_analyze
        else -> Res.string.action_process
    }

val ToolCategory.title: StringResource
    get() = when (this) {
        ToolCategory.AI -> Res.string.category_ai
        ToolCategory.CLEAN_UP -> Res.string.category_clean_up
        ToolCategory.EDIT -> Res.string.category_edit
        ToolCategory.EFFECTS -> Res.string.category_effects
        ToolCategory.UTILITIES -> Res.string.category_utilities
    }

val EqPreset.title: StringResource
    get() = when (this) {
        EqPreset.VOICE_CLARITY -> Res.string.eq_voice_clarity
        EqPreset.BASS_BOOST -> Res.string.eq_bass_boost
        EqPreset.TREBLE_BOOST -> Res.string.eq_treble_boost
        EqPreset.WARM -> Res.string.eq_warm
        EqPreset.REMOVE_RUMBLE -> Res.string.eq_remove_rumble
        EqPreset.TELEPHONE -> Res.string.eq_telephone
    }

val LoudnessTarget.title: StringResource
    get() = when (this) {
        LoudnessTarget.STREAMING -> Res.string.loudness_streaming
        LoudnessTarget.PODCAST -> Res.string.loudness_podcast
        LoudnessTarget.BROADCAST -> Res.string.loudness_broadcast
    }

val JobStatus.title: StringResource
    get() = when (this) {
        JobStatus.QUEUED -> Res.string.status_queued
        JobStatus.RUNNING -> Res.string.status_running
        JobStatus.DONE -> Res.string.status_done
        JobStatus.FAILED -> Res.string.status_failed
        JobStatus.CANCELLED -> Res.string.status_cancelled
    }

/** Status line for a running or queued job, such as "Processing… 42%". */
@Composable
fun Job.progressText(): String {
    val p = progress ?: return stringResource(status.title)
    return when (p.stage) {
        Stage.QUEUED -> stringResource(Res.string.stage_queued)
        Stage.PREPARING -> stringResource(Res.string.stage_preparing)
        Stage.DOWNLOADING_MODEL -> stringResource(
            Res.string.stage_model_download_percent, p.index, p.total, (p.subFraction * 100).toInt(),
        )
        Stage.PROCESSING -> if (p.total == 100) {
            stringResource(Res.string.stage_percent, p.index)
        } else {
            stringResource(Res.string.stage_section, p.index, p.total)
        }
    }
}

@Composable
fun Job.failureText(): String = when (failure) {
    FailureKind.OUT_OF_MEMORY -> stringResource(Res.string.error_out_of_memory)
    FailureKind.NO_SPEECH -> stringResource(Res.string.error_no_speech)
    FailureKind.UNSUPPORTED -> failureDetail ?: stringResource(Res.string.error_unsupported_platform)
    FailureKind.INTERRUPTED -> stringResource(Res.string.error_interrupted)
    FailureKind.PROCESSING, null -> stringResource(Res.string.error_processing_failed, failureDetail ?: stringResource(Res.string.error_unknown))
}

@Composable
fun InputProblem.text(): String = when (this) {
    InputProblem.Unsupported -> stringResource(Res.string.error_unsupported_format)
    is InputProblem.Unreadable -> stringResource(Res.string.error_unreadable, detail ?: stringResource(Res.string.error_unknown))
}

/** Short description of what a job did, such as "Peak −1 dB" or "1.25× · +2 st". */
@Composable
fun JobSpec.summary(): String? = when (this) {
    JobSpec.Enhance, JobSpec.Analyze -> null
    is JobSpec.Transcribe -> stringResource(language.label)
    is JobSpec.Process -> operations.mapNotNull { it.summary() }.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun AudioOperation.summary(): String? = when (this) {
    is AudioOperation.Trim -> "${formatDuration(startMs)} – ${formatDuration(endMs)}"
    is AudioOperation.Gain -> stringResource(Res.string.summary_gain, signed(db))
    is AudioOperation.NormalizePeak -> stringResource(Res.string.summary_peak, signed(peakDb))
    is AudioOperation.NormalizeLoudness -> stringResource(Res.string.summary_loudness, target.lufs.roundToInt())
    is AudioOperation.Fade -> stringResource(Res.string.summary_fade, seconds(inMs), seconds(outMs))
    is AudioOperation.Equalizer -> stringResource(preset.title)
    is AudioOperation.SpeedPitch -> stringResource(Res.string.summary_speed, decimal(speed, 2), signed(semitones))
    is AudioOperation.Denoise -> stringResource(Res.string.summary_strength, (strength * 100).roundToInt())
    is AudioOperation.RemoveSilence -> stringResource(Res.string.summary_silence, signed(thresholdDb))
    is AudioOperation.Resample -> stringResource(Res.string.summary_rate, decimal(sampleRate / 1000f, 2))
}

suspend fun UiMessage.text(): String = when (this) {
    is UiMessage.JobFinished -> getString(Res.string.message_job_finished, getString(tool.title))
    is UiMessage.JobFailed -> getString(Res.string.message_job_failed, getString(tool.title))
    UiMessage.Saved -> getString(Res.string.message_saved)
    is UiMessage.SaveFailed -> getString(Res.string.error_save, detail ?: getString(Res.string.error_unknown))
    UiMessage.Cancelled -> getString(Res.string.message_cancelled)
    UiMessage.ModelsDeleted -> getString(Res.string.message_models_deleted)
    UiMessage.HistoryCleared -> getString(Res.string.message_history_cleared)
    UiMessage.Copied -> getString(Res.string.message_copied)
}

/** "Just now", "5 min ago", "3 h ago", "2 d ago". */
@Composable
fun relativeTime(epochMs: Long): String {
    val minutes = (currentTimeMillis() - epochMs).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> stringResource(Res.string.time_just_now)
        minutes < 60 -> stringResource(Res.string.time_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(Res.string.time_hours, (minutes / 60).toInt())
        else -> stringResource(Res.string.time_days, (minutes / (60 * 24)).toInt())
    }
}

/** m:ss.d for editing, or h:mm:ss for long durations. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}.$tenths"
}

private fun two(v: Long) = if (v < 10) "0$v" else "$v"

/** "+2.5", "−3", "0" with a true minus sign. */
fun signed(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10f
    val text = if (rounded == rounded.toInt().toFloat()) abs(rounded.toInt()).toString() else decimal(abs(rounded), 1)
    return when {
        rounded > 0 -> "+$text"
        rounded < 0 -> "−$text"
        else -> "0"
    }
}

/** Fixed decimals without platform formatting APIs. */
fun decimal(value: Float, digits: Int): String {
    var factor = 1
    repeat(digits) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToInt()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(digits, '0').trimEnd('0')
    val sign = if (value < 0 && scaled != 0) "\u2212" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

private fun seconds(ms: Long): String = decimal(ms / 1000f, 1)
