package com.audiops.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiops.app.AudiopsState
import com.audiops.app.AudiopsViewModel
import com.audiops.app.Job
import com.audiops.app.MeetingDocument
import com.audiops.app.MeetingState
import com.audiops.app.SoundKind
import com.audiops.app.SoundProfile
import com.audiops.app.Stage
import com.audiops.app.Tab
import com.audiops.app.Tool
import com.audiops.app.ToolCategory
import com.audiops.app.TranscriptionModel
import com.audiops.app.UserSettings
import com.audiops.app.suggestedTool
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_choose
import com.audiops.shared.resources.action_record
import com.audiops.shared.resources.app_name
import com.audiops.shared.resources.badge_android_only
import com.audiops.shared.resources.banner_processing
import com.audiops.shared.resources.home_footer
import com.audiops.shared.resources.home_recent
import com.audiops.shared.resources.home_subtitle
import com.audiops.shared.resources.meeting_live
import com.audiops.shared.resources.meeting_speaker
import com.audiops.shared.resources.sounds_music
import com.audiops.shared.resources.sounds_noisy_speech
import com.audiops.shared.resources.sounds_other
import com.audiops.shared.resources.sounds_speech
import com.audiops.shared.resources.tab_activity
import com.audiops.shared.resources.tab_settings
import com.audiops.shared.resources.tab_tools
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Root of the app. Collects ViewModel state (lifecycle-aware) and passes plain state and
 * callbacks down to stateless screens (unidirectional data flow).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiopsScreen(viewModel: AudiopsViewModel, appVersion: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStorage by viewModel.modelStorage.collectAsStateWithLifecycle()
    val outputStorage by viewModel.outputStorage.collectAsStateWithLifecycle()
    val meeting by viewModel.meetingState.collectAsStateWithLifecycle()
    // Saved transcripts name speakers in the app's language.
    val speakerTemplate = stringResource(Res.string.meeting_speaker, SPEAKER_NUMBER)
    SideEffect { viewModel.speakerName = { number -> speakerTemplate.replace(SPEAKER_NUMBER, number.toString()) } }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text()) }
    }

    // Back on another tab returns to Tools (Android top-level convention).
    PlatformBackHandler(enabled = state.tool == null && state.tab != Tab.TOOLS) {
        if (!viewModel.back()) viewModel.selectTab(Tab.TOOLS)
    }
    // Back from a tool is predictive: the tool screen shrinks and rounds off as the gesture follows the finger.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PlatformPredictiveBackHandler(
        enabled = state.tool != null,
        onProgress = { backProgress = it },
        onCancel = { backProgress = 0f },
        onBack = { viewModel.back() },
    )
    LaunchedEffect(state.tool) { backProgress = 0f }

    // Opening a tool grows it out of the Tools tab on an expressive spring; closing it settles back.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state.tool,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(effects) + scaleIn(spatial, initialScale = 0.92f)) togetherWith
                    (fadeOut(effects) + scaleOut(spatial, targetScale = 1.04f))
            } else {
                (fadeIn(effects) + scaleIn(spatial, initialScale = 1.04f)) togetherWith
                    (fadeOut(effects) + scaleOut(spatial, targetScale = 0.92f))
            }
        },
        label = "tool",
    ) { tool ->
        val progress = if (tool != null) backProgress else 0f
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val scale = 1f - progress * 0.1f
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape((progress * 32).dp)
                clip = progress > 0f
            },
        ) {
            if (tool == Tool.MEETING) {
                MeetingScreen(
                    state = meeting,
                    language = state.language,
                    model = effectiveTranscriptionModel(state.language, settings?.transcriptionModel ?: TranscriptionModel.AUTO),
                    latestJob = state.latestJob(tool),
                    snackbar = snackbar,
                    actions = MeetingActions(
                        onBack = { viewModel.back() },
                        onStart = viewModel::startMeeting,
                        onStop = viewModel::stopMeeting,
                        onDiscard = viewModel::discardMeeting,
                        onDismissError = viewModel::acknowledgeMeeting,
                        onMicrophoneDenied = { viewModel.recordFailed(null) },
                        onLanguage = viewModel::setLanguage,
                        onTranscriptionModel = viewModel::setTranscriptionModel,
                        jobCard = JobCardActions(
                            viewModel::cancel,
                            viewModel::saveOutput,
                            viewModel.saveFormats,
                            viewModel::saveAudio,
                            viewModel::saveSubtitles,
                            viewModel::notifyCopied,
                            viewModel::readText,
                            viewModel::speakers,
                            viewModel::nameSpeakers,
                        ),
                    ),
                )
            } else if (tool == Tool.SPEAK) {
                SpeakScreen(
                    latestJob = state.latestJob(tool),
                    snackbar = snackbar,
                    actions = ToolActions(
                        onBack = { viewModel.back() },
                        onChoose = viewModel::chooseInput,
                        onLanguage = viewModel::setLanguage,
                        onTranscriptionModel = viewModel::setTranscriptionModel,
                        onStart = { spec -> viewModel.start(tool, spec) },
                        onCancel = viewModel::cancel,
                        onSave = viewModel::saveOutput,
                        saveFormats = viewModel.saveFormats,
                        onSaveAudio = viewModel::saveAudio,
                        onSaveSubtitles = viewModel::saveSubtitles,
                        onCopied = viewModel::notifyCopied,
                        readText = viewModel::readText,
                        speakers = viewModel::speakers,
                        onNameSpeakers = viewModel::nameSpeakers,
                    ),
                )
            } else if (tool != null) {
                ToolScreen(
                    state = ToolScreenState(
                        tool = tool,
                        input = state.input,
                        waveform = state.waveform,
                        waveformLoading = state.waveformLoading,
                        inputProblem = state.inputProblem,
                        language = state.language,
                        transcriptionModel = settings?.transcriptionModel ?: TranscriptionModel.AUTO,
                        latestJob = state.latestJob(tool),
                    ),
                    snackbar = snackbar,
                    actions = ToolActions(
                        onBack = { viewModel.back() },
                        onChoose = viewModel::chooseInput,
                        onLanguage = viewModel::setLanguage,
                        onTranscriptionModel = viewModel::setTranscriptionModel,
                        onStart = { spec -> viewModel.start(tool, spec) },
                        onCancel = viewModel::cancel,
                        onSave = viewModel::saveOutput,
                        saveFormats = viewModel.saveFormats,
                        onSaveAudio = viewModel::saveAudio,
                        onSaveSubtitles = viewModel::saveSubtitles,
                        onCopied = viewModel::notifyCopied,
                        readText = viewModel::readText,
                        speakers = viewModel::speakers,
                        onNameSpeakers = viewModel::nameSpeakers,
                    ),
                )
            } else {
                Shell(
                    tab = state.tab,
                    activeJobs = state.activeJobs.size,
                    snackbar = snackbar,
                    onTab = viewModel::selectTab,
                ) { tab ->
                    when (tab) {
                        Tab.TOOLS -> ToolsTab(
                            state = state,
                            meeting = meeting,
                            onShowMeeting = { viewModel.open(Tool.MEETING) },
                            unsupported = viewModel.unsupportedTools,
                            onOpen = viewModel::open,
                            onShowActivity = { viewModel.selectTab(Tab.ACTIVITY) },
                            onChoose = viewModel::chooseInput,
                            onRecordFailed = viewModel::recordFailed,
                        )

                        Tab.ACTIVITY -> ActivityTab(
                            jobs = state.jobs,
                            actions = ActivityActions(
                                onCancel = viewModel::cancel,
                                onDelete = viewModel::delete,
                                onClear = viewModel::clearHistory,
                                onReopen = viewModel::reopen,
                                onSave = viewModel::saveOutput,
                                saveFormats = viewModel.saveFormats,
                                onSaveAudio = viewModel::saveAudio,
                                onSaveSubtitles = viewModel::saveSubtitles,
                                onCopied = viewModel::notifyCopied,
                                readText = viewModel::readText,
                                speakers = viewModel::speakers,
                                onNameSpeakers = viewModel::nameSpeakers,
                            ),
                        )

                        Tab.SETTINGS -> SettingsScreen(
                            settings = settings ?: UserSettings(),
                            appVersion = appVersion,
                            modelStorage = modelStorage,
                            outputStorage = outputStorage,
                            busy = state.activeJobs.isNotEmpty(),
                            showTranscription = Tool.TRANSCRIBE !in viewModel.unsupportedTools,
                            voices = state.rememberedVoices,
                            actions = SettingsActions(
                                onThemeMode = viewModel::setThemeMode,
                                onDynamicColor = viewModel::setDynamicColor,
                                onPureBlack = viewModel::setPureBlack,
                                onKeepScreenOn = viewModel::setKeepScreenOn,
                                onDefaultLanguage = viewModel::setDefaultLanguage,
                                onTranscriptionModel = viewModel::setTranscriptionModel,
                                onDeleteModel = viewModel::deleteModel,
                                onForgetVoice = viewModel::forgetVoice,
                                onClearHistory = viewModel::clearHistory,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private data class TabItem(val tab: Tab, val icon: ImageVector, val label: StringResource)

private val tabs = listOf(
    TabItem(Tab.TOOLS, Icons.Rounded.Dashboard, Res.string.tab_tools),
    TabItem(Tab.ACTIVITY, Icons.Rounded.History, Res.string.tab_activity),
    TabItem(Tab.SETTINGS, Icons.Rounded.Settings, Res.string.tab_settings),
)

/**
 * Top-level navigation: an Expressive short navigation bar on phones, a navigation rail
 * on wide windows (tablets). Tabs fade through each other.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Shell(tab: Tab, activeJobs: Int, snackbar: SnackbarHostState, onTab: (Tab) -> Unit, content: @Composable (Tab) -> Unit) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            // The status bar inset is left to each tab's title bar, which then draws behind the
            // status bar in its own (scroll-dependent) colour; the navigation bars handle the rest.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!wide) {
                    ShortNavigationBar {
                        tabs.forEach { item ->
                            ShortNavigationBarItem(
                                selected = tab == item.tab,
                                onClick = { onTab(item.tab) },
                                icon = { TabIcon(item, activeJobs) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(12.dp))
                        tabs.forEach { item ->
                            NavigationRailItem(
                                selected = tab == item.tab,
                                onClick = { onTab(item.tab) },
                                icon = { TabIcon(item, activeJobs) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { (fadeIn(effects) + scaleIn(initialScale = 0.98f)) togetherWith fadeOut(effects) },
                    modifier = Modifier.weight(1f),
                    label = "tab",
                ) { content(it) }
            }
        }
    }
}

@Composable
private fun TabIcon(item: TabItem, activeJobs: Int) {
    if (item.tab == Tab.ACTIVITY && activeJobs > 0) {
        BadgedBox(badge = { Badge { Text(activeJobs.toString()) } }) { Icon(item.icon, contentDescription = null) }
    } else {
        Icon(item.icon, contentDescription = null)
    }
}

/** The Tools tab: title bar (with a link to Activity while jobs run), the recording, and tools grouped by category. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolsTab(
    state: AudiopsState,
    meeting: MeetingState,
    onShowMeeting: () -> Unit,
    unsupported: Set<Tool>,
    onOpen: (Tool) -> Unit,
    onShowActivity: () -> Unit,
    onChoose: (id: String, name: String) -> Unit,
    onRecordFailed: (detail: String?) -> Unit,
) {
    val pickRecording = rememberAudioPicker(onChoose)
    val recorder = rememberRecorder(onChoose, onRecordFailed)
    KeepScreenOn(recorder.state.recording)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleSubtitle = rememberTitleBarSubtitle()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.app_name), fontWeight = FontWeight.Medium) },
                subtitle = { titleSubtitle.Text(stringResource(Res.string.home_subtitle)) },
                expandedHeight = titleSubtitle.expandedHeight(),
                actions = {
                    if (meeting is MeetingState.Live) MeetingAction(meeting.elapsedMs, onShowMeeting)
                    if (state.activeJobs.isNotEmpty()) ProcessingAction(state.activeJobs.size, onShowActivity)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // Each category fills its rows: tiles stretch to share the width, so wide windows
        // (tablets) have no empty cells at the end of a row.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth - 32.dp + TileGap) / (TileMinWidth + TileGap)).toInt().coerceAtLeast(1)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                item(key = "recording") {
                    // With nothing chosen, the header's own buttons start the flow; the card appears once there is a recording.
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = state.input == null && !recorder.state.recording,
                        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                        label = "recording",
                    ) { empty ->
                        if (empty) {
                            InputActions(onChoose = pickRecording, recorder = recorder)
                        } else {
                            InputCard(
                                input = state.input,
                                waveform = state.waveform,
                                loading = state.waveformLoading,
                                problem = state.inputProblem,
                                selection = 0f..1f,
                                onChoose = pickRecording,
                                recorder = recorder,
                            )
                        }
                    }
                }
                // What the recording sounds like, and the tool worth trying for it.
                item(key = "sounds") {
                    val sounds = state.sounds
                    AnimatedVisibility(
                        visible = sounds != null && state.input != null && (sounds.tags.isNotEmpty() || sounds.kind != SoundKind.OTHER),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        if (sounds != null) SoundSuggestion(sounds, unsupported, onOpen)
                    }
                }
                val recent = state.recentTools(limit = columns)
                if (recent.isNotEmpty()) {
                    item(key = "title-recent") { SectionLabel(stringResource(Res.string.home_recent)) }
                    item(key = "recent") { ToolRow(recent, columns, unsupported, onOpen) }
                }
                ToolCategory.entries.forEach { category ->
                    item(key = "title-${category.name}") { SectionLabel(stringResource(category.title)) }
                    Tool.entries.filter { it.category == category }.chunked(columns).forEach { row ->
                        item(key = row.joinToString { it.name }) {
                            ToolRow(row, columns, unsupported, onOpen, featured = category == ToolCategory.AI)
                        }
                    }
                }
                item(key = "footer") {
                    Text(
                        stringResource(Res.string.home_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                    )
                }
            }
        }
    }
}

/** What the recording sounds like ("Sounds like speech", the main sounds) and a button for the suggested tool. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoundSuggestion(sounds: SoundProfile, unsupported: Set<Tool>, onOpen: (Tool) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tool = sounds.kind.suggestedTool?.takeIf { it !in unsupported }
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (container, content) = (tool?.category ?: ToolCategory.UTILITIES).colors(colors)
            ShapedIcon(
                icon = Icons.Rounded.GraphicEq,
                shape = MaterialShapes.SoftBurst.toShape(),
                container = container,
                content = content,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (sounds.kind) {
                            SoundKind.SPEECH -> Res.string.sounds_speech
                            SoundKind.NOISY_SPEECH -> Res.string.sounds_noisy_speech
                            SoundKind.MUSIC -> Res.string.sounds_music
                            SoundKind.OTHER -> Res.string.sounds_other
                        },
                    ),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                if (sounds.tags.isNotEmpty()) {
                    Text(
                        sounds.tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (tool != null) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { onOpen(tool) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(tool.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(tool.title), maxLines = 1)
                }
            }
        }
    }
}

private val TileMinWidth = 160.dp
private val TileHeight = 76.dp
private val FeaturedTileHeight = 148.dp
private val TileGap = 10.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp).semantics { heading() },
    )
}

/** One row of tiles. Tiles share the width, and an incomplete row keeps every tile the same width. */
@Composable
private fun ToolRow(tools: List<Tool>, columns: Int, unsupported: Set<Tool>, onOpen: (Tool) -> Unit, featured: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        tools.forEach { tool ->
            val modifier = Modifier.weight(1f).fillMaxHeight()
            if (featured) {
                FeaturedToolTile(
                    tool,
                    available = tool !in unsupported,
                    onClick = { onOpen(tool) },
                    modifier = modifier,
                    wide = tools.size < columns,
                )
            } else {
                ToolTile(tool, available = tool !in unsupported, onClick = { onOpen(tool) }, modifier = modifier)
            }
        }
        // A featured tile left alone in its row spans the row; compact tiles keep their width.
        if (!featured) repeat(columns - tools.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Tiles shrink a little while pressed and spring back, so a tap feels physical. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A larger tile for the AI tools, the heart of the app: the tool's shape, its name and what it does.
 * The tonal container comes from the tool's category, so the group reads as one family.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeaturedToolTile(tool: Tool, available: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = if (available) tool.category.colors(colors) else colors.surfaceContainerHighest to colors.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        enabled = available,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = if (wide) TileHeight else FeaturedTileHeight).pressScale(interaction),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHigh,
            disabledContainerColor = colors.surfaceContainerLowest,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        val icon = @Composable {
            ShapedIcon(icon = tool.icon, shape = tool.shape.toShape(), container = container, content = content, size = 52.dp)
        }
        val words = @Composable {
            Text(
                stringResource(tool.title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (available) stringResource(tool.description) else stringResource(Res.string.badge_android_only),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Alone in its row, the tile lies on its side rather than leaving an empty half.
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { words() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                icon()
                Spacer(Modifier.weight(1f).heightIn(min = 14.dp))
                words()
            }
        }
    }
}

/** A compact tile: the tool's shape and its name. The tool screen repeats the name with its description. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolTile(tool: Tool, available: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = if (available) tool.category.colors(colors) else colors.surfaceContainerHighest to colors.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        enabled = available,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = TileHeight).pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        // Unavailable tools recede (lower-emphasis container) instead of turning lighter.
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainerLowest,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(icon = tool.icon, shape = tool.shape.toShape(), container = container, content = content, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(tool.title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!available) {
                    Text(
                        stringResource(Res.string.badge_android_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Choose file and Record, shown under the title while no recording is chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputActions(onChoose: () -> Unit, recorder: Recorder) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onChoose,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.action_choose), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (recorder.available) {
            FilledTonalButton(
                onClick = recorder::toggle,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.action_record), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Top-bar link back to a live meeting: a red dot and the running time. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeetingAction(elapsedMs: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.SmallContentPadding,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer),
        modifier = Modifier.padding(end = 8.dp).heightIn(min = 40.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.error, CircleShape))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("${stringResource(Res.string.meeting_live)} ${MeetingDocument.clock(elapsedMs)}")
    }
}

/** Top-bar link to Activity while jobs run, so progress is one tap away from the Tools tab. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingAction(count: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.SmallContentPadding,
        modifier = Modifier.padding(end = 8.dp).heightIn(min = 40.dp),
    ) {
        Text(stringResource(Res.string.banner_processing, count))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = stringResource(Res.string.tab_activity),
            modifier = Modifier.size(ButtonDefaults.SmallIconSize),
        )
    }
}

/** Stands in for the number in the localized "Speaker N" text. */
private const val SPEAKER_NUMBER = "{n}"
