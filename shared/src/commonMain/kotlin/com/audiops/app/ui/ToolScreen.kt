package com.audiops.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.AudioInput
import com.audiops.app.AudioOperation
import com.audiops.app.AudioReport
import com.audiops.app.EnhanceMode
import com.audiops.app.EqPreset
import com.audiops.app.InputProblem
import com.audiops.app.Job
import com.audiops.app.JobSpec
import com.audiops.app.JobStatus
import com.audiops.app.LoudnessTarget
import com.audiops.app.Preset
import com.audiops.app.SaveFormat
import com.audiops.app.SeparatedStem
import com.audiops.app.SpeechLanguage
import com.audiops.app.Stage
import com.audiops.app.SubtitleFormat
import com.audiops.app.SupportedSampleRates
import com.audiops.app.Tool
import com.audiops.app.TranscriptionModel
import com.audiops.app.WaveformData
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.action_change
import com.audiops.shared.resources.action_choose
import com.audiops.shared.resources.action_copy
import com.audiops.shared.resources.action_record
import com.audiops.shared.resources.action_reset
import com.audiops.shared.resources.action_save_srt
import com.audiops.shared.resources.action_save_stem
import com.audiops.shared.resources.action_save_text
import com.audiops.shared.resources.action_save_vtt
import com.audiops.shared.resources.action_save_wav
import com.audiops.shared.resources.action_stop_recording
import com.audiops.shared.resources.convert_hint
import com.audiops.shared.resources.denoise_hint
import com.audiops.shared.resources.level_light
import com.audiops.shared.resources.level_medium
import com.audiops.shared.resources.level_strong
import com.audiops.shared.resources.meeting_recording_title
import com.audiops.shared.resources.option_fade_in
import com.audiops.shared.resources.option_fade_out
import com.audiops.shared.resources.option_gain
import com.audiops.shared.resources.option_min_pause
import com.audiops.shared.resources.option_pitch
import com.audiops.shared.resources.option_silence_threshold
import com.audiops.shared.resources.option_speed
import com.audiops.shared.resources.option_strength
import com.audiops.shared.resources.option_trim_range
import com.audiops.shared.resources.recording_active
import com.audiops.shared.resources.recording_none
import com.audiops.shared.resources.recording_pick_hint
import com.audiops.shared.resources.recording_pick_on_tools
import com.audiops.shared.resources.recording_reading
import com.audiops.shared.resources.recording_ready
import com.audiops.shared.resources.report_clipping
import com.audiops.shared.resources.report_dc
import com.audiops.shared.resources.report_duration
import com.audiops.shared.resources.report_hint
import com.audiops.shared.resources.report_loudness
import com.audiops.shared.resources.report_peak
import com.audiops.shared.resources.report_rms
import com.audiops.shared.resources.report_silence
import com.audiops.shared.resources.report_title
import com.audiops.shared.resources.result_ready_body
import com.audiops.shared.resources.result_ready_title
import com.audiops.shared.resources.sample_rate_khz
import com.audiops.shared.resources.status_cancelled
import com.audiops.shared.resources.stems_ready_body
import com.audiops.shared.resources.stems_ready_title
import com.audiops.shared.resources.step_language
import com.audiops.shared.resources.step_model
import com.audiops.shared.resources.step_options
import com.audiops.shared.resources.step_recording
import com.audiops.shared.resources.transcript_title
import com.audiops.shared.resources.unit_dbfs
import com.audiops.shared.resources.unit_lufs
import com.audiops.shared.resources.volume_gain
import com.audiops.shared.resources.volume_loudness
import com.audiops.shared.resources.volume_loudness_hint
import com.audiops.shared.resources.volume_peak
import com.audiops.shared.resources.volume_peak_hint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Everything a tool screen needs, hoisted out of the ViewModel. */
data class ToolScreenState(
    val tool: Tool,
    val input: AudioInput?,
    val waveform: WaveformData?,
    val waveformLoading: Boolean,
    val inputProblem: InputProblem?,
    val language: SpeechLanguage,
    val transcriptionModel: TranscriptionModel = TranscriptionModel.PARAKEET,
    val latestJob: Job?,
)

/** Events from [ToolScreen]. */
data class ToolActions(
    val onBack: () -> Unit,
    val onChoose: (id: String, name: String) -> Unit,
    val onLanguage: (SpeechLanguage) -> Unit,
    val onTranscriptionModel: (TranscriptionModel) -> Unit = {},
    val onStart: (JobSpec) -> Unit,
    val onCancel: (jobId: String) -> Unit,
    val onSave: (Job, destination: String?) -> Unit,
    val saveFormats: List<SaveFormat>,
    val onSaveAudio: (Job, SaveFormat, destination: String?) -> Unit,
    val onSaveSubtitles: (Job, SubtitleFormat, destination: String?) -> Unit,
    val onCopied: () -> Unit,
    val readText: suspend (path: String) -> String?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolScreen(state: ToolScreenState, snackbar: SnackbarHostState, actions: ToolActions) {
    val tool = state.tool
    val pickRecording = rememberAudioPicker(actions.onChoose)
    // Option values survive rotation; each tool keeps its own.
    var options by rememberSaveable(tool, stateSaver = ToolOptionsSaver) { mutableStateOf(ToolOptions()) }
    val job = state.latestJob
    ResultHaptics(job)
    // Starting a job adds its card below the button, often off-screen: scroll it into view
    // when it appears and when it turns into a (taller) result.
    val jobCardRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(job?.id, job?.status) {
        if (job != null) {
            delay(120) // let the card enter the layout first
            jobCardRequester.bringIntoView()
        }
    }

    ToolScaffold(
        title = stringResource(tool.title),
        subtitle = stringResource(tool.description),
        busy = false,
        onBack = actions.onBack,
        snackbar = snackbar,
    ) {
        // Language tools start with what is spoken, then the model for it.
        val languageSteps = if (tool == Tool.TRANSCRIBE) 2 else 0
        if (tool == Tool.TRANSCRIBE) {
            StepTitle(1, stringResource(Res.string.step_language))
            Spacer(Modifier.height(12.dp))
            LanguageField(selected = state.language, onSelect = actions.onLanguage)
            Spacer(Modifier.height(28.dp))
            StepTitle(2, stringResource(Res.string.step_model))
            Spacer(Modifier.height(12.dp))
            TranscriptionModelChoice(state.language, state.transcriptionModel, actions.onTranscriptionModel)
            Spacer(Modifier.height(28.dp))
        }
        StepTitle(languageSteps + 1, stringResource(Res.string.step_recording))
        Spacer(Modifier.height(12.dp))
        InputCard(
            input = state.input,
            waveform = state.waveform,
            loading = state.waveformLoading,
            problem = state.inputProblem,
            selection = if (tool == Tool.TRIM) options.trimStart..options.trimEnd else 0f..1f,
            onChoose = null,
            recorder = null,
            onChooseWhenEmpty = pickRecording,
        )

        val ready = state.input != null && state.inputProblem == null
        val action: @Composable () -> Unit = {
            Button(
                onClick = { actions.onStart(options.toSpec(tool, state.language, state.transcriptionModel, state.waveform?.durationMs)) },
                enabled = ready,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PrimaryButtonPadding,
                modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
            ) {
                Icon(tool.icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(tool.action), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (tool.hasOptions) {
            Spacer(Modifier.height(28.dp))
            StepTitle(languageSteps + 2, stringResource(Res.string.step_options))
            Spacer(Modifier.height(12.dp))
            OptionsCard {
                ToolOptionsEditor(
                    tool = tool,
                    options = options,
                    durationMs = state.waveform?.durationMs,
                    language = state.language,
                    transcriptionModel = state.transcriptionModel,
                    onLanguage = actions.onLanguage,
                    onChange = { options = it },
                )
                action()
            }
        }

        if (!tool.hasOptions) {
            Spacer(Modifier.height(28.dp))
            OptionsCard { action() }
        }

        AnimatedVisibility(visible = job != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            if (job != null) {
                Column(Modifier.bringIntoViewRequester(jobCardRequester)) {
                    Spacer(Modifier.height(24.dp))
                    JobCard(
                        job = job,
                        actions = JobCardActions(
                            actions.onCancel,
                            actions.onSave,
                            actions.saveFormats,
                            actions.onSaveAudio,
                            actions.onSaveSubtitles,
                            actions.onCopied,
                            actions.readText,
                        ),
                        // The waveform of the recording this job works on, if it is still the one on screen.
                        peaks = state.waveform?.peaks?.takeIf { state.input?.id == job.inputId },
                    )
                }
            }
        }
    }
}

private val Tool.hasOptions: Boolean get() = this != Tool.ANALYZE && this != Tool.SEPARATE && this != Tool.TRANSCRIBE

@Composable
internal fun OptionsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

/** Shown while recording: a status line with the time, a level meter and Stop. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingPanel(state: RecorderState, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.recording_active),
                style = MaterialTheme.typography.titleMedium,
                color = colors.error,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(formatDuration(state.elapsedMs), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.level },
            modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { },
            color = colors.error,
            trackColor = colors.errorContainer,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStop,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
            contentPadding = PrimaryButtonPadding,
            modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_stop_recording), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The current recording with its waveform preview and a play button. With [onChoose] and [recorder]
 * it is the global recording card on the Tools tab (choose a file or record); without them it is the
 * read-only summary a tool screen shows, with [onChooseWhenEmpty] as the only button, shown while no
 * recording is chosen yet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InputCard(
    input: AudioInput?,
    waveform: WaveformData?,
    loading: Boolean,
    problem: InputProblem?,
    selection: ClosedFloatingPointRange<Float>,
    onChoose: (() -> Unit)?,
    recorder: Recorder?,
    onChooseWhenEmpty: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val player = LocalAudioPlayer.current
    val playerState by player.state.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            // The first recording of a session has no file yet: the recording panel alone says what is happening.
            val firstRecording = input == null && recorder?.state?.recording == true
            if (!firstRecording) {
                Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                    ShapedIcon(
                        icon = Icons.Rounded.AudioFile,
                        shape = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape(),
                        container = colors.primaryContainer,
                        content = colors.onPrimaryContainer,
                        size = 48.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            input?.name ?: stringResource(Res.string.recording_none),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                input == null && onChoose == null -> stringResource(Res.string.recording_pick_on_tools)
                                input == null -> stringResource(Res.string.recording_pick_hint)
                                waveform != null -> formatDuration(waveform.durationMs)
                                loading -> stringResource(Res.string.recording_reading)
                                else -> stringResource(Res.string.recording_ready)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (input != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    when {
                        waveform != null -> WaveformView(
                            peaks = waveform.peaks,
                            selection = selection,
                            playhead = if (playerState.id == input.id && playerState.durationMs > 0) {
                                playerState.positionMs.toFloat() / playerState.durationMs
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )

                        loading -> LoadingIndicator(Modifier.size(48.dp))

                        problem != null -> Text(
                            problem.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                    }
                }
                if (waveform != null) {
                    Spacer(Modifier.height(8.dp))
                    PlayerBar(input.id)
                }
            }
            if (onChoose == null || recorder == null) {
                if (input == null && onChooseWhenEmpty != null) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onChooseWhenEmpty,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(Res.string.action_choose))
                    }
                }
                return@Column
            }
            if (!firstRecording) Spacer(Modifier.height(16.dp))
            val recording = recorder.state
            if (recording.recording) {
                RecordingPanel(recording, onStop = recorder::toggle)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onChoose,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(if (input != null) Res.string.action_change else Res.string.action_choose))
                    }
                    if (recorder.available) {
                        FilledTonalButton(
                            onClick = recorder::toggle,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_record))
                        }
                    }
                }
            }
        }
    }
}

/** Tool option values. One instance holds every tool's settings; each tool reads its own. */
data class ToolOptions(
    val denoiseLevel: Int = 1, // 0 light, 1 medium, 2 strong
    val enhanceMode: Int = 0, // 0 fast, 1 studio
    val silenceThresholdDb: Float = -40f,
    val silenceMinSeconds: Float = 0.8f,
    val trimStart: Float = 0f,
    val trimEnd: Float = 1f,
    val fadeInSeconds: Float = 2f,
    val fadeOutSeconds: Float = 3f,
    val volumeMode: Int = 0, // 0 peak, 1 loudness, 2 gain
    val loudnessTarget: LoudnessTarget = LoudnessTarget.PODCAST,
    val gainDb: Float = 3f,
    val speed: Float = 1f,
    val semitones: Float = 0f,
    val eqPreset: EqPreset = EqPreset.VOICE_CLARITY,
    val sampleRate: Int = 16_000,
    val preset: Preset = Preset.PODCAST,
) {
    fun toSpec(tool: Tool, language: SpeechLanguage, model: TranscriptionModel, durationMs: Long?): JobSpec = when (tool) {
        Tool.ENHANCE -> JobSpec.Enhance(if (enhanceMode == 0) EnhanceMode.FAST else EnhanceMode.STUDIO)

        // Text to speech has its own screen (SpeakScreen) and never builds a spec here.
        Tool.SPEAK -> error("Text to speech uses SpeakScreen")

        // Live meetings have their own screen (MeetingScreen) and are recorded, not queued.
        Tool.MEETING -> error("Live meetings use MeetingScreen")

        Tool.SEPARATE -> JobSpec.Separate

        Tool.TRANSCRIBE -> JobSpec.Transcribe(language, effectiveTranscriptionModel(language, model))

        Tool.ANALYZE -> JobSpec.Analyze

        Tool.DENOISE -> process(AudioOperation.Denoise(listOf(0.35f, 0.6f, 0.85f)[denoiseLevel]))

        Tool.SILENCE -> process(AudioOperation.RemoveSilence(silenceThresholdDb, (silenceMinSeconds * 1000).roundToLong(), 120))

        Tool.TRIM -> {
            val total = durationMs ?: 0L
            process(AudioOperation.Trim((trimStart * total).roundToLong(), (trimEnd * total).roundToLong()))
        }

        Tool.FADE -> process(AudioOperation.Fade((fadeInSeconds * 1000).roundToLong(), (fadeOutSeconds * 1000).roundToLong()))

        Tool.VOLUME -> process(
            when (volumeMode) {
                0 -> AudioOperation.NormalizePeak(-1f)
                1 -> AudioOperation.NormalizeLoudness(loudnessTarget)
                else -> AudioOperation.Gain(gainDb)
            },
        )

        Tool.SPEED -> process(AudioOperation.SpeedPitch(speed, semitones))

        Tool.EQUALIZER -> process(AudioOperation.Equalizer(eqPreset))

        Tool.CONVERT -> process(AudioOperation.Resample(sampleRate))

        Tool.PRESETS -> JobSpec.Process(preset.operations)
    }

    private fun process(op: AudioOperation) = JobSpec.Process(listOf(op))
}

/** Saves [ToolOptions] as a list of primitives for rememberSaveable. */
private val ToolOptionsSaver = androidx.compose.runtime.saveable.Saver<ToolOptions, List<Any>>(
    save = {
        listOf(
            it.denoiseLevel, it.silenceThresholdDb, it.silenceMinSeconds, it.trimStart, it.trimEnd,
            it.fadeInSeconds, it.fadeOutSeconds, it.volumeMode, it.loudnessTarget.name, it.gainDb,
            it.speed, it.semitones, it.eqPreset.name, it.sampleRate, it.preset.name, it.enhanceMode,
        )
    },
    restore = {
        ToolOptions(
            denoiseLevel = it[0] as Int, silenceThresholdDb = it[1] as Float, silenceMinSeconds = it[2] as Float,
            trimStart = it[3] as Float, trimEnd = it[4] as Float, fadeInSeconds = it[5] as Float,
            fadeOutSeconds = it[6] as Float, volumeMode = it[7] as Int,
            loudnessTarget = LoudnessTarget.valueOf(it[8] as String), gainDb = it[9] as Float,
            speed = it[10] as Float, semitones = it[11] as Float, eqPreset = EqPreset.valueOf(it[12] as String),
            sampleRate = it[13] as Int, preset = Preset.valueOf(it[14] as String), enhanceMode = it[15] as Int,
        )
    },
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ToolOptionsEditor(
    tool: Tool,
    options: ToolOptions,
    durationMs: Long?,
    language: SpeechLanguage,
    transcriptionModel: TranscriptionModel,
    onLanguage: (SpeechLanguage) -> Unit,
    onChange: (ToolOptions) -> Unit,
) {
    when (tool) {
        Tool.DENOISE -> {
            OptionLabel(stringResource(Res.string.option_strength))
            ConnectedChoice(
                options = listOf(0, 1, 2),
                selected = options.denoiseLevel,
                label = { stringResource(listOf(Res.string.level_light, Res.string.level_medium, Res.string.level_strong)[it]) },
                onSelect = { onChange(options.copy(denoiseLevel = it)) },
                hint = { stringResource(Res.string.denoise_hint) },
            )
        }

        Tool.SILENCE -> {
            LabeledSlider(
                label = stringResource(Res.string.option_silence_threshold, signed(options.silenceThresholdDb)),
                value = options.silenceThresholdDb,
                range = -60f..-20f,
                step = 1f,
                onChange = { onChange(options.copy(silenceThresholdDb = it)) },
            )
            LabeledSlider(
                label = stringResource(Res.string.option_min_pause, decimal(options.silenceMinSeconds, 1)),
                value = options.silenceMinSeconds,
                range = 0.3f..3f,
                step = 0.1f,
                onChange = { onChange(options.copy(silenceMinSeconds = it)) },
            )
        }

        Tool.TRIM -> {
            val total = durationMs ?: 0L
            OptionLabel(
                stringResource(
                    Res.string.option_trim_range,
                    formatDuration((options.trimStart * total).roundToLong()),
                    formatDuration((options.trimEnd * total).roundToLong()),
                    formatDuration(((options.trimEnd - options.trimStart) * total).roundToLong()),
                ),
            )
            RangeSlider(
                value = options.trimStart..options.trimEnd,
                onValueChange = { r ->
                    // Keep at least 1% selected so the result is never empty.
                    val start = r.start.coerceIn(0f, 0.99f)
                    onChange(options.copy(trimStart = start, trimEnd = r.endInclusive.coerceIn(start + 0.01f, 1f)))
                },
                enabled = durationMs != null,
            )
        }

        Tool.FADE -> {
            LabeledSlider(
                label = stringResource(Res.string.option_fade_in, decimal(options.fadeInSeconds, 1)),
                value = options.fadeInSeconds,
                range = 0f..10f,
                step = 0.5f,
                onChange = { onChange(options.copy(fadeInSeconds = it)) },
            )
            LabeledSlider(
                label = stringResource(Res.string.option_fade_out, decimal(options.fadeOutSeconds, 1)),
                value = options.fadeOutSeconds,
                range = 0f..10f,
                step = 0.5f,
                onChange = { onChange(options.copy(fadeOutSeconds = it)) },
            )
        }

        Tool.VOLUME -> {
            ConnectedChoice(
                options = listOf(0, 1, 2),
                selected = options.volumeMode,
                label = { stringResource(listOf(Res.string.volume_peak, Res.string.volume_loudness, Res.string.volume_gain)[it]) },
                onSelect = { onChange(options.copy(volumeMode = it)) },
                hint = { if (it == 0) stringResource(Res.string.volume_peak_hint) else null },
            )
            when (options.volumeMode) {
                0 -> Unit

                1 -> {
                    ConnectedChoice(
                        options = LoudnessTarget.entries,
                        selected = options.loudnessTarget,
                        label = { stringResource(it.title) },
                        onSelect = { onChange(options.copy(loudnessTarget = it)) },
                        hint = { stringResource(Res.string.volume_loudness_hint, it.lufs.roundToInt()) },
                    )
                }

                else -> LabeledSlider(
                    label = stringResource(Res.string.option_gain, signed(options.gainDb)),
                    value = options.gainDb,
                    range = -20f..20f,
                    step = 0.5f,
                    onChange = { onChange(options.copy(gainDb = it)) },
                )
            }
        }

        Tool.SPEED -> {
            LabeledSlider(
                label = stringResource(Res.string.option_speed, decimal(options.speed, 2)),
                value = options.speed,
                range = 0.5f..2f,
                step = 0.05f,
                onChange = { onChange(options.copy(speed = it)) },
            )
            LabeledSlider(
                label = stringResource(Res.string.option_pitch, signed(options.semitones)),
                value = options.semitones,
                range = -12f..12f,
                step = 1f,
                onChange = { onChange(options.copy(semitones = it)) },
            )
            TextButton(onClick = { onChange(options.copy(speed = 1f, semitones = 0f)) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_reset))
            }
        }

        Tool.PRESETS -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.forEach { preset ->
                    HintTip(stringResource(preset.description)) {
                        ToggleButton(
                            checked = options.preset == preset,
                            onCheckedChange = { onChange(options.copy(preset = preset)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                        ) { Text(stringResource(preset.title)) }
                    }
                }
            }
        }

        Tool.EQUALIZER -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EqPreset.entries.forEach { preset ->
                ToggleButton(
                    checked = options.eqPreset == preset,
                    onCheckedChange = { onChange(options.copy(eqPreset = preset)) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                ) { Text(stringResource(preset.title)) }
            }
        }

        Tool.CONVERT -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportedSampleRates.forEach { rate ->
                    HintTip(stringResource(Res.string.convert_hint)) {
                        ToggleButton(
                            checked = options.sampleRate == rate,
                            onCheckedChange = { onChange(options.copy(sampleRate = rate)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                        ) { Text(stringResource(Res.string.sample_rate_khz, decimal(rate / 1000f, 2))) }
                    }
                }
            }
        }

        Tool.ENHANCE -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnhanceMode.entries.forEach { mode ->
                    HintTip(stringResource(mode.description)) {
                        ToggleButton(
                            checked = options.enhanceMode == mode.ordinal,
                            onCheckedChange = { onChange(options.copy(enhanceMode = mode.ordinal)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                        ) { Text(stringResource(mode.title)) }
                    }
                }
            }
        }

        Tool.ANALYZE, Tool.SPEAK, Tool.SEPARATE, Tool.MEETING, Tool.TRANSCRIBE -> Unit
    }
}

@Composable
internal fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A continuous slider whose value snaps to multiples of [step] in code. (Discrete sliders
 * with many steps draw a dense row of tick marks in Material 3 Expressive.)
 */
@Composable
internal fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit) {
    Column {
        OptionLabel(label)
        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = (kotlin.math.round((raw - range.start) / step) * step + range.start).coerceIn(range)
                if (snapped != value) onChange(snapped)
            },
            valueRange = range,
        )
    }
}

/** Connected toggle buttons for a single choice (Material 3 Expressive button group). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    hint: @Composable (T) -> String? = { null },
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            HintTip(hint(option), Modifier.weight(1f)) {
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Events from a [JobCard]. */
data class JobCardActions(
    val onCancel: (jobId: String) -> Unit,
    val onSave: (Job, destination: String?) -> Unit,
    val saveFormats: List<SaveFormat>,
    val onSaveAudio: (Job, SaveFormat, destination: String?) -> Unit,
    val onSaveSubtitles: (Job, SubtitleFormat, destination: String?) -> Unit,
    val onCopied: () -> Unit,
    val readText: suspend (path: String) -> String?,
)

/** A job's live progress (over the recording's [peaks] when known), or its result: player and Save, transcript, or report. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JobCard(job: Job, actions: JobCardActions, modifier: Modifier = Modifier, peaks: FloatArray? = null) {
    val colors = MaterialTheme.colorScheme
    when (job.status) {
        JobStatus.QUEUED, JobStatus.RUNNING -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(20.dp)) {
                WorkStatus(
                    status = job.progressText(),
                    progress = job.progress?.fraction,
                    downloading = job.progress?.stage == Stage.DOWNLOADING_MODEL,
                    peaks = peaks,
                    seed = job.id,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { actions.onCancel(job.id) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        }

        JobStatus.FAILED -> ErrorCard(job.failureText())

        // Cancelling was the user's choice, not a failure: neutral styling, not the error colours.
        JobStatus.CANCELLED -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Block, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.status_cancelled), color = colors.onSurfaceVariant)
            }
        }

        JobStatus.DONE -> when {
            job.report != null -> ReportCard(job.report)
            job.spec is JobSpec.Transcribe -> TranscriptCard(job, actions)
            job.spec is JobSpec.Meeting -> MeetingResultCard(job, actions)
            job.spec is JobSpec.Separate && job.stemPaths != null -> StemsCard(job, actions)
            job.outputPath != null -> AudioResultCard(job, actions)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudioResultCard(job: Job, actions: JobCardActions) {
    val path = job.outputPath ?: return
    val saveWav = rememberFileSaver(SaveFormat.WAV.mimeType) { actions.onSave(job, it) }
    val saveM4a = rememberFileSaver(SaveFormat.M4A.mimeType) { actions.onSaveAudio(job, SaveFormat.M4A, it) }
    val saveOpus = rememberFileSaver(SaveFormat.OPUS.mimeType) { actions.onSaveAudio(job, SaveFormat.OPUS, it) }
    ResultCard(
        title = stringResource(Res.string.result_ready_title),
        body = job.spec.summary() ?: stringResource(Res.string.result_ready_body),
    ) {
        PlayerBar(path)
        Spacer(Modifier.height(12.dp))
        // The format is asked only now, when saving: a menu under the button lists what this device can write.
        SaveButton(actions.saveFormats) { format ->
            val name = derivedFileName(job.inputName, job.tool.name.lowercase(), format.extension)
            when (format) {
                SaveFormat.WAV -> saveWav(name)
                SaveFormat.M4A -> saveM4a(name)
                SaveFormat.OPUS -> saveOpus(name)
            }
        }
    }
}

/** The stems of a separation: each one plays and saves on its own, in the format chosen above them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StemsCard(job: Job, actions: JobCardActions) {
    val stems = job.stemPaths ?: return
    var pending by remember { mutableIntStateOf(0) }
    fun stemJob(index: Int) = job.copy(outputPath = stems[index])
    val saveWav = rememberFileSaver(SaveFormat.WAV.mimeType) { actions.onSave(stemJob(pending), it) }
    val saveM4a = rememberFileSaver(SaveFormat.M4A.mimeType) { actions.onSaveAudio(stemJob(pending), SaveFormat.M4A, it) }
    val saveOpus = rememberFileSaver(SaveFormat.OPUS.mimeType) { actions.onSaveAudio(stemJob(pending), SaveFormat.OPUS, it) }
    ResultCard(title = stringResource(Res.string.stems_ready_title), body = stringResource(Res.string.stems_ready_body)) {
        SeparatedStem.entries.forEachIndexed { index, stem ->
            if (index < stems.size) {
                Spacer(Modifier.height(if (index == 0) 4.dp else 16.dp))
                Text(stringResource(stem.label), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                PlayerBar(stems[index])
                Spacer(Modifier.height(8.dp))
                // The format is asked only when saving this stem.
                SaveButton(actions.saveFormats) { format ->
                    pending = index
                    val name = derivedFileName(job.inputName, stem.fileSuffix, format.extension)
                    when (format) {
                        SaveFormat.WAV -> saveWav(name)
                        SaveFormat.M4A -> saveM4a(name)
                        SaveFormat.OPUS -> saveOpus(name)
                    }
                }
            }
        }
    }
}

/** A finished meeting: its transcript (with speakers), then the recording to play and save. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeetingResultCard(job: Job, actions: JobCardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TranscriptCard(job, actions)
        val audio = job.audioPath ?: return@Column
        val recording = job.copy(outputPath = audio)
        val saveWav = rememberFileSaver(SaveFormat.WAV.mimeType) { actions.onSave(recording, it) }
        val saveM4a = rememberFileSaver(SaveFormat.M4A.mimeType) { actions.onSaveAudio(recording, SaveFormat.M4A, it) }
        val saveOpus = rememberFileSaver(SaveFormat.OPUS.mimeType) { actions.onSaveAudio(recording, SaveFormat.OPUS, it) }
        ResultCard(title = stringResource(Res.string.meeting_recording_title), body = job.spec.summary().orEmpty()) {
            PlayerBar(audio)
            Spacer(Modifier.height(12.dp))
            SaveButton(actions.saveFormats) { format ->
                val name = derivedFileName(job.inputName, "recording", format.extension)
                when (format) {
                    SaveFormat.WAV -> saveWav(name)
                    SaveFormat.M4A -> saveM4a(name)
                    SaveFormat.OPUS -> saveOpus(name)
                }
            }
        }
    }
}

/**
 * A meeting transcript ([com.audiops.app.MeetingDocument.text]): each turn's first line, the speaker
 * and time, stands out above what they said.
 */
@Composable
private fun MeetingTranscriptText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        text.split("\n\n").filter { it.isNotBlank() }.forEach { turn ->
            Column {
                Text(
                    turn.substringBefore('\n'),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(turn.substringAfter('\n', ""), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TranscriptCard(job: Job, actions: JobCardActions) {
    val path = job.outputPath ?: return
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val text by produceState<String?>(initialValue = job.preview, path) { value = actions.readText(path) ?: job.preview }
    val saveAs = rememberFileSaver("text/plain") { actions.onSave(job, it) }
    val saveSrt = rememberFileSaver("application/x-subrip") { actions.onSaveSubtitles(job, SubtitleFormat.SRT, it) }
    val saveVtt = rememberFileSaver("text/vtt") { actions.onSaveSubtitles(job, SubtitleFormat.VTT, it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(Res.string.transcript_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                if (job.spec is JobSpec.Meeting) {
                    MeetingTranscriptText(
                        text.orEmpty(),
                    )
                } else {
                    Text(text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(text.orEmpty()))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        actions.onCopied()
                    },
                    shapes = ConnectedLeading,
                    modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_copy))
                }
                FilledTonalButton(
                    onClick = { saveAs(derivedFileName(job.inputName, "transcript", "txt")) },
                    shapes = ConnectedTrailing,
                    modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_save_text))
                }
            }
            if (job.subtitlesPath != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    FilledTonalButton(
                        onClick = { saveSrt(derivedFileName(job.inputName, "subtitles", "srt")) },
                        shapes = ConnectedLeading,
                        modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_save_srt))
                    }
                    FilledTonalButton(
                        onClick = { saveVtt(derivedFileName(job.inputName, "subtitles", "vtt")) },
                        shapes = ConnectedTrailing,
                        modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_save_vtt))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportCard(report: AudioReport) {
    val rows = listOf(
        Res.string.report_duration to formatDuration(report.durationMs),
        Res.string.report_loudness to (report.loudnessLufs?.let { stringResource(Res.string.unit_lufs, decimal(it, 1)) } ?: "—"),
        Res.string.report_peak to stringResource(Res.string.unit_dbfs, decimal(report.peakDb, 1)),
        Res.string.report_rms to stringResource(Res.string.unit_dbfs, decimal(report.rmsDb, 1)),
        Res.string.report_clipping to report.clippedSamples.toString(),
        Res.string.report_silence to "${(report.silenceRatio * 100).roundToInt()}%",
        Res.string.report_dc to decimal(report.dcOffset * 100, 2) + "%",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.report_title), style = MaterialTheme.typography.titleLargeEmphasized)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                    Text(stringResource(label), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
            }
            Hint(stringResource(Res.string.report_hint))
        }
    }
}

/** A confirm buzz when a job finishes, a reject buzz when it fails. */
@Composable
internal fun ResultHaptics(job: Job?) {
    val haptics = LocalHapticFeedback.current
    val status = job?.status
    var last by rememberSaveable(job?.id) { mutableStateOf(status) }
    LaunchedEffect(job?.id, status) {
        if (status != last) {
            when (status) {
                JobStatus.DONE -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                JobStatus.FAILED -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
            last = status
        }
    }
}
