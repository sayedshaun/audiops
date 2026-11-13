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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.AudioInput
import com.audiops.app.AudioOperation
import com.audiops.app.AudioReport
import com.audiops.app.EqPreset
import com.audiops.app.InputProblem
import com.audiops.app.Job
import com.audiops.app.JobSpec
import com.audiops.app.JobStatus
import com.audiops.app.LoudnessTarget
import com.audiops.app.SpeechLanguage
import com.audiops.app.SupportedSampleRates
import com.audiops.app.Tool
import com.audiops.app.WaveformData
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.action_change
import com.audiops.shared.resources.action_choose
import com.audiops.shared.resources.action_copy
import com.audiops.shared.resources.action_reset
import com.audiops.shared.resources.action_save_text
import com.audiops.shared.resources.action_save_wav
import com.audiops.shared.resources.convert_hint
import com.audiops.shared.resources.denoise_hint
import com.audiops.shared.resources.enhance_hint
import com.audiops.shared.resources.level_light
import com.audiops.shared.resources.level_medium
import com.audiops.shared.resources.level_strong
import com.audiops.shared.resources.option_fade_in
import com.audiops.shared.resources.option_fade_out
import com.audiops.shared.resources.option_gain
import com.audiops.shared.resources.option_min_pause
import com.audiops.shared.resources.option_pitch
import com.audiops.shared.resources.option_silence_threshold
import com.audiops.shared.resources.option_speed
import com.audiops.shared.resources.option_strength
import com.audiops.shared.resources.option_trim_range
import com.audiops.shared.resources.recording_none
import com.audiops.shared.resources.recording_pick_hint
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
import com.audiops.shared.resources.step_language
import com.audiops.shared.resources.step_options
import com.audiops.shared.resources.step_recording
import com.audiops.shared.resources.transcribe_hint
import com.audiops.shared.resources.transcribe_language_hint
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
    val latestJob: Job?,
)

/** Events from [ToolScreen]. */
data class ToolActions(
    val onBack: () -> Unit,
    val onChoose: (id: String, name: String) -> Unit,
    val onLanguage: (SpeechLanguage) -> Unit,
    val onStart: (JobSpec) -> Unit,
    val onCancel: (jobId: String) -> Unit,
    val onSave: (Job, destination: String?) -> Unit,
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
        StepTitle(1, stringResource(Res.string.step_recording))
        Spacer(Modifier.height(12.dp))
        InputCard(
            input = state.input,
            waveform = state.waveform,
            loading = state.waveformLoading,
            problem = state.inputProblem,
            selection = if (tool == Tool.TRIM) options.trimStart..options.trimEnd else 0f..1f,
            onChoose = pickRecording,
        )

        if (tool.hasOptions) {
            Spacer(Modifier.height(28.dp))
            StepTitle(2, stringResource(if (tool == Tool.TRANSCRIBE) Res.string.step_language else Res.string.step_options))
            Spacer(Modifier.height(12.dp))
            OptionsCard {
                ToolOptionsEditor(
                    tool = tool,
                    options = options,
                    durationMs = state.waveform?.durationMs,
                    language = state.language,
                    onLanguage = actions.onLanguage,
                    onChange = { options = it },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        val ready = state.input != null && state.inputProblem == null
        Button(
            onClick = { actions.onStart(options.toSpec(tool, state.language, state.waveform?.durationMs)) },
            enabled = ready,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PrimaryButtonPadding,
            modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(tool.icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(tool.action), style = MaterialTheme.typography.titleMedium)
        }
        if (tool == Tool.ENHANCE || tool == Tool.TRANSCRIBE) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (tool == Tool.ENHANCE) Res.string.enhance_hint else Res.string.transcribe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = job != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            if (job != null) {
                Column(Modifier.bringIntoViewRequester(jobCardRequester)) {
                    Spacer(Modifier.height(24.dp))
                    JobCard(job = job, actions = JobCardActions(actions.onCancel, actions.onSave, actions.onCopied, actions.readText))
                }
            }
        }
    }
}

private val Tool.hasOptions: Boolean get() = this != Tool.ENHANCE && this != Tool.ANALYZE

@Composable
private fun OptionsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

/** Recording picker with the waveform preview and a play button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputCard(
    input: AudioInput?,
    waveform: WaveformData?,
    loading: Boolean,
    problem: InputProblem?,
    selection: ClosedFloatingPointRange<Float>,
    onChoose: () -> Unit,
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
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onChoose,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(if (input != null) Res.string.action_change else Res.string.action_choose))
            }
        }
    }
}

/** Tool option values. One instance holds every tool's settings; each tool reads its own. */
data class ToolOptions(
    val denoiseLevel: Int = 1, // 0 light, 1 medium, 2 strong
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
) {
    fun toSpec(tool: Tool, language: SpeechLanguage, durationMs: Long?): JobSpec = when (tool) {
        Tool.ENHANCE -> JobSpec.Enhance

        Tool.TRANSCRIBE -> JobSpec.Transcribe(language)

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
    }

    private fun process(op: AudioOperation) = JobSpec.Process(listOf(op))
}

/** Saves [ToolOptions] as a list of primitives for rememberSaveable. */
private val ToolOptionsSaver = androidx.compose.runtime.saveable.Saver<ToolOptions, List<Any>>(
    save = {
        listOf(
            it.denoiseLevel, it.silenceThresholdDb, it.silenceMinSeconds, it.trimStart, it.trimEnd,
            it.fadeInSeconds, it.fadeOutSeconds, it.volumeMode, it.loudnessTarget.name, it.gainDb,
            it.speed, it.semitones, it.eqPreset.name, it.sampleRate,
        )
    },
    restore = {
        ToolOptions(
            denoiseLevel = it[0] as Int, silenceThresholdDb = it[1] as Float, silenceMinSeconds = it[2] as Float,
            trimStart = it[3] as Float, trimEnd = it[4] as Float, fadeInSeconds = it[5] as Float,
            fadeOutSeconds = it[6] as Float, volumeMode = it[7] as Int,
            loudnessTarget = LoudnessTarget.valueOf(it[8] as String), gainDb = it[9] as Float,
            speed = it[10] as Float, semitones = it[11] as Float, eqPreset = EqPreset.valueOf(it[12] as String),
            sampleRate = it[13] as Int,
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
    onLanguage: (SpeechLanguage) -> Unit,
    onChange: (ToolOptions) -> Unit,
) {
    when (tool) {
        Tool.TRANSCRIBE -> {
            ConnectedChoice(
                options = SpeechLanguage.entries,
                selected = language,
                label = { stringResource(it.label) },
                onSelect = onLanguage,
            )
            Hint(stringResource(Res.string.transcribe_language_hint))
        }

        Tool.DENOISE -> {
            OptionLabel(stringResource(Res.string.option_strength))
            ConnectedChoice(
                options = listOf(0, 1, 2),
                selected = options.denoiseLevel,
                label = { stringResource(listOf(Res.string.level_light, Res.string.level_medium, Res.string.level_strong)[it]) },
                onSelect = { onChange(options.copy(denoiseLevel = it)) },
            )
            Hint(stringResource(Res.string.denoise_hint))
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
            )
            when (options.volumeMode) {
                0 -> Hint(stringResource(Res.string.volume_peak_hint))

                1 -> {
                    ConnectedChoice(
                        options = LoudnessTarget.entries,
                        selected = options.loudnessTarget,
                        label = { stringResource(it.title) },
                        onSelect = { onChange(options.copy(loudnessTarget = it)) },
                    )
                    Hint(stringResource(Res.string.volume_loudness_hint, options.loudnessTarget.lufs.roundToInt()))
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
                    ToggleButton(
                        checked = options.sampleRate == rate,
                        onCheckedChange = { onChange(options.copy(sampleRate = rate)) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                    ) { Text(stringResource(Res.string.sample_rate_khz, decimal(rate / 1000f, 2))) }
                }
            }
            Hint(stringResource(Res.string.convert_hint))
        }

        Tool.ENHANCE, Tool.ANALYZE -> Unit
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A continuous slider whose value snaps to multiples of [step] in code. (Discrete sliders
 * with many steps draw a dense row of tick marks in Material 3 Expressive.)
 */
@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit) {
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
fun <T> ConnectedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
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
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
            ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Events from a [JobCard]. */
data class JobCardActions(
    val onCancel: (jobId: String) -> Unit,
    val onSave: (Job, destination: String?) -> Unit,
    val onCopied: () -> Unit,
    val readText: suspend (path: String) -> String?,
)

/** A job's live progress, or its result: player and Save, transcript, or report. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JobCard(job: Job, actions: JobCardActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (job.status) {
        JobStatus.QUEUED, JobStatus.RUNNING -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(20.dp)) {
                WorkStatus(job.progressText(), job.progress?.fraction)
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
            job.outputPath != null -> AudioResultCard(job, actions)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudioResultCard(job: Job, actions: JobCardActions) {
    val path = job.outputPath ?: return
    val saveAs = rememberFileSaver("audio/wav") { actions.onSave(job, it) }
    ResultCard(
        title = stringResource(Res.string.result_ready_title),
        body = job.spec.summary() ?: stringResource(Res.string.result_ready_body),
    ) {
        PlayerBar(path)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { saveAs(derivedFileName(job.inputName, job.tool.name.lowercase(), "wav")) },
            shapes = ButtonDefaults.shapes(),
            contentPadding = PrimaryButtonPadding,
            modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_save_wav))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(Res.string.transcript_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(8.dp))
            SelectionContainer { Text(text.orEmpty(), style = MaterialTheme.typography.bodyLarge) }
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
private fun ResultHaptics(job: Job?) {
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
