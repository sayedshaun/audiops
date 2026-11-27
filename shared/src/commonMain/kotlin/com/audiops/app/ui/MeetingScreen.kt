package com.audiops.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.audiops.app.FailureKind
import com.audiops.app.Job
import com.audiops.app.MeetingDocument
import com.audiops.app.MeetingLine
import com.audiops.app.MeetingState
import com.audiops.app.SpeechLanguage
import com.audiops.app.Stage
import com.audiops.app.Tool
import com.audiops.app.TranscriptionModel
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.action_meeting_discard
import com.audiops.shared.resources.action_meeting_new
import com.audiops.shared.resources.action_meeting_start
import com.audiops.shared.resources.action_meeting_stop
import com.audiops.shared.resources.back
import com.audiops.shared.resources.error_no_speech
import com.audiops.shared.resources.error_out_of_memory
import com.audiops.shared.resources.error_unknown
import com.audiops.shared.resources.meeting_discard_body
import com.audiops.shared.resources.meeting_discard_title
import com.audiops.shared.resources.meeting_elapsed
import com.audiops.shared.resources.meeting_failed
import com.audiops.shared.resources.meeting_finishing
import com.audiops.shared.resources.meeting_intro_body
import com.audiops.shared.resources.meeting_intro_title
import com.audiops.shared.resources.meeting_jump_latest
import com.audiops.shared.resources.meeting_last
import com.audiops.shared.resources.meeting_listening
import com.audiops.shared.resources.meeting_listening_hint
import com.audiops.shared.resources.meeting_live
import com.audiops.shared.resources.meeting_name
import com.audiops.shared.resources.meeting_preparing
import com.audiops.shared.resources.meeting_screen_off
import com.audiops.shared.resources.meeting_speaker
import com.audiops.shared.resources.meeting_speakers
import com.audiops.shared.resources.stage_model_download_percent
import com.audiops.shared.resources.step_language
import com.audiops.shared.resources.step_model
import com.audiops.shared.resources.step_start
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Events from the live meeting screen. */
data class MeetingActions(
    val onBack: () -> Unit,
    /** Starts a meeting; microphone access has been granted. */
    val onStart: (name: String, speakerName: (Int) -> String) -> Unit,
    val onStop: () -> Unit,
    val onDiscard: () -> Unit,
    val onDismissError: () -> Unit,
    val onMicrophoneDenied: () -> Unit,
    val onLanguage: (SpeechLanguage) -> Unit,
    val onTranscriptionModel: (TranscriptionModel) -> Unit,
    val jobCard: JobCardActions,
)

/**
 * Live meeting: an introduction and Start; while running, the live transcript with speakers,
 * the time and a level meter; afterwards the transcript and recording (also kept in Activity).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeetingScreen(
    state: MeetingState,
    language: SpeechLanguage,
    model: TranscriptionModel,
    latestJob: Job?,
    snackbar: SnackbarHostState,
    actions: MeetingActions,
) {
    val tool = Tool.MEETING
    val running = state is MeetingState.Preparing || state is MeetingState.Live || state is MeetingState.Finishing
    // A meeting that finishes while this screen is open is shown first, above Start: it is the
    // newest meeting in history that was not there when the meeting started.
    var finishedHere by rememberSaveable { mutableStateOf<String?>(null) }
    var before by rememberSaveable { mutableStateOf<String?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (running) {
            before = latestJob?.id
            started = true
        }
    }
    LaunchedEffect(latestJob?.id) {
        if (started && !running && latestJob != null && latestJob.id != before) finishedHere = latestJob.id
    }
    val speakerTemplate = stringResource(Res.string.meeting_speaker, NUMBER)
    val nameTemplate = stringResource(Res.string.meeting_name, DATE)
    val requestMicrophone = rememberMicrophonePermission { granted ->
        if (granted) {
            finishedHere = null
            actions.onStart(nameTemplate) { number -> speakerTemplate.replace(NUMBER, number.toString()) }
        } else {
            actions.onMicrophoneDenied()
        }
    }

    if (running) {
        LiveMeeting(state, snackbar, actions)
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(tool.title)) },
                subtitle = { Text(stringResource(tool.description)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state is MeetingState.Failed) ErrorCard(state.text())
                val justFinished = latestJob?.takeIf { it.id == finishedHere }
                if (justFinished != null) {
                    JobCard(justFinished, actions.jobCard)
                    Button(
                        onClick = {
                            actions.onDismissError()
                            requestMicrophone()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.action_meeting_new))
                    }
                } else {
                    // What is spoken, then the model for it, then Start.
                    StepTitle(1, stringResource(Res.string.step_language))
                    LanguageField(selected = language, onSelect = actions.onLanguage)
                    Spacer(Modifier.height(12.dp))
                    StepTitle(2, stringResource(Res.string.step_model))
                    TranscriptionModelChoice(language, model, actions.onTranscriptionModel)
                    Spacer(Modifier.height(12.dp))
                    StepTitle(3, stringResource(Res.string.step_start))
                    MeetingIntro {
                        actions.onDismissError()
                        requestMicrophone()
                    }
                    if (latestJob != null) {
                        Text(
                            stringResource(Res.string.meeting_last),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                        )
                        JobCard(latestJob, actions.jobCard)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeetingIntro(onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapedIcon(
                icon = Tool.MEETING.icon,
                shape = Tool.MEETING.shape.toShape(),
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                size = 88.dp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(Res.string.meeting_intro_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.meeting_intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.action_meeting_start))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.meeting_screen_off),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The meeting while it is starting, running and finishing: header, transcript, and Stop and save. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveMeeting(state: MeetingState, snackbar: SnackbarHostState, actions: MeetingActions) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val lines = when (state) {
        is MeetingState.Live -> state.lines
        is MeetingState.Finishing -> state.lines
        else -> emptyList()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Tool.MEETING.title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
        bottomBar = { LiveControls(state, onStop = actions.onStop, onDiscard = { confirmDiscard = true }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                when (state) {
                    is MeetingState.Preparing -> Preparing(state)

                    is MeetingState.Live -> {
                        LiveHeader(state)
                        Transcript(lines, state.partial, Modifier.weight(1f))
                    }

                    is MeetingState.Finishing -> {
                        Transcript(lines, null, Modifier.weight(1f))
                    }

                    else -> Unit
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.meeting_discard_title)) },
            text = { Text(stringResource(Res.string.meeting_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onDiscard()
                }) { Text(stringResource(Res.string.action_meeting_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Preparing(state: MeetingState.Preparing) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val progress = state.progress
        if (progress?.stage == Stage.DOWNLOADING_MODEL) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(20.dp)) {
                    WorkStatus(
                        status = stringResource(
                            Res.string.stage_model_download_percent,
                            progress.index,
                            progress.total,
                            (progress.subFraction * 100).toInt(),
                        ),
                        progress = progress.fraction,
                        downloading = true,
                        seed = "meeting",
                    )
                }
            }
        } else {
            LoadingIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.meeting_preparing),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Live badge, elapsed time, speakers and a level meter. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveHeader(state: MeetingState.Live) {
    val colors = MaterialTheme.colorScheme
    val clock = MeetingDocument.clock(state.elapsedMs)
    val elapsedLabel = stringResource(Res.string.meeting_elapsed, clock)
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors.error, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.meeting_live), style = MaterialTheme.typography.labelLarge, color = colors.error)
                Spacer(Modifier.weight(1f))
                // Shown once someone has spoken; "0 speakers" would read like a fault.
                if (state.speakers > 0) {
                    Surface(color = colors.secondaryContainer, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                pluralStringResource(Res.plurals.meeting_speakers, state.speakers, state.speakers),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                clock,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { contentDescription = elapsedLabel },
            )
            Spacer(Modifier.height(8.dp))
            LevelMeter(state.level, state.elapsedMs, Modifier.fillMaxWidth().height(28.dp))
        }
    }
}

/** Recent microphone levels as bars, newest on the right. Decorative. */
@Composable
private fun LevelMeter(level: Float, time: Long, modifier: Modifier) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(LEVEL_BARS) { add(0f) } } }
    LaunchedEffect(time) {
        history.removeAt(0)
        history.add(level)
    }
    val color = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.clearAndSetSemantics { }) {
        val step = size.width / LEVEL_BARS
        val stroke = (step * 0.5f).coerceAtLeast(2f)
        history.forEachIndexed { i, value ->
            val height = (size.height * value).coerceAtLeast(stroke)
            val x = (i + 0.5f) * step
            drawLine(
                if (value > 0.04f) color else quiet,
                Offset(x, (size.height - height) / 2),
                Offset(x, (size.height + height) / 2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Consecutive lines of one speaker, shown as one turn. */
private data class Turn(val speaker: Int, val startMs: Long, val text: String)

private fun turns(lines: List<MeetingLine>): List<Turn> {
    val turns = mutableListOf<Turn>()
    for (line in lines) {
        val last = turns.lastOrNull()
        if (last != null && last.speaker == line.speaker) {
            turns[turns.lastIndex] = last.copy(text = last.text + " " + line.text)
        } else {
            turns += Turn(line.speaker, line.startMs, line.text)
        }
    }
    return turns
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Transcript(lines: List<MeetingLine>, partial: String?, modifier: Modifier) {
    val turns = remember(lines) { turns(lines) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    val count = turns.size + (if (partial != null) 1 else 0)
    // Follow new words unless the reader scrolled up; scrolling back to the end follows again.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, more) ->
            if (scrolling) follow = !more
        }
    }
    LaunchedEffect(count, partial, turns.lastOrNull()?.text) {
        if (follow && count > 0) listState.animateScrollToItem(count - 1, Int.MAX_VALUE / 2)
    }
    if (count == 0) {
        Column(modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.meeting_listening),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.meeting_listening_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(modifier.fillMaxWidth()) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(turns.size) { index -> TurnRow(turns[index]) }
                if (partial != null) {
                    item(key = "partial") { PartialRow(partial) }
                }
            }
        }
        AnimatedVisibility(
            visible = !follow,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    follow = true
                    scope.launch { listState.animateScrollToItem(count - 1, Int.MAX_VALUE / 2) }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.meeting_jump_latest))
            }
        }
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    val (container, content) = speakerColors(turn.speaker)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Box(Modifier.size(36.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Text((turn.speaker + 1).toString(), style = MaterialTheme.typography.labelLarge, color = content)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(Res.string.meeting_speaker, turn.speaker + 1), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    MeetingDocument.clock(turn.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(turn.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Words of the utterance still in progress; they settle into a turn when the speaker pauses. */
@Composable
private fun PartialRow(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(36.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveControls(state: MeetingState, onStop: () -> Unit, onDiscard: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is MeetingState.Finishing) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.meeting_finishing),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    OutlinedButton(
                        onClick = onDiscard,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.action_meeting_discard))
                    }
                    Button(
                        onClick = onStop,
                        enabled = state is MeetingState.Live,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1.4f).heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.action_meeting_stop))
                    }
                }
            }
        }
    }
}

/** Tonal colours per speaker, from the theme, repeating after six speakers. */
@Composable
private fun speakerColors(speaker: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val palette = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.primaryFixedDim to c.onPrimaryFixed,
        c.tertiaryFixedDim to c.onTertiaryFixed,
        c.secondaryFixedDim to c.onSecondaryFixed,
    )
    return palette[speaker % palette.size]
}

@Composable
private fun MeetingState.Failed.text(): String = when (kind) {
    FailureKind.NO_SPEECH -> stringResource(Res.string.error_no_speech)
    FailureKind.OUT_OF_MEMORY -> stringResource(Res.string.error_out_of_memory)
    else -> stringResource(Res.string.meeting_failed, detail ?: stringResource(Res.string.error_unknown))
}

private const val LEVEL_BARS = 48
private const val NUMBER = "{n}"
private const val DATE = "%1\$s"
