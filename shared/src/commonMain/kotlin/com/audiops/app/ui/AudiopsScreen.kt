package com.audiops.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiops.app.AudiopsState
import com.audiops.app.AudiopsViewModel
import com.audiops.app.Job
import com.audiops.app.Stage
import com.audiops.app.Tab
import com.audiops.app.Tool
import com.audiops.app.ToolCategory
import com.audiops.app.TranscriptionModel
import com.audiops.app.UserSettings
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_choose
import com.audiops.shared.resources.action_record
import com.audiops.shared.resources.app_name
import com.audiops.shared.resources.badge_android_only
import com.audiops.shared.resources.banner_processing
import com.audiops.shared.resources.home_footer
import com.audiops.shared.resources.home_subtitle
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text()) }
    }

    // Back closes the open tool; on another tab it returns to Tools (Android top-level convention).
    PlatformBackHandler(enabled = state.tool != null || state.tab != Tab.TOOLS) {
        if (!viewModel.back()) viewModel.selectTab(Tab.TOOLS)
    }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state.tool,
        transitionSpec = {
            val forward = targetState != null
            val direction = if (forward) 1 else -1
            (fadeIn(effects) + slideInHorizontally(spatial) { it / 8 * direction }) togetherWith
                (fadeOut(effects) + slideOutHorizontally(spatial) { -it / 8 * direction })
        },
        label = "tool",
    ) { tool ->
        if (tool == Tool.SPEAK) {
            SpeakScreen(
                latestJob = state.latestJob(tool),
                snackbar = snackbar,
                actions = ToolActions(
                    onBack = { viewModel.back() },
                    onChoose = viewModel::chooseInput,
                    onLanguage = viewModel::setLanguage,
                    onStart = { spec -> viewModel.start(tool, spec) },
                    onCancel = viewModel::cancel,
                    onSave = viewModel::saveOutput,
                    saveFormats = viewModel.saveFormats,
                    onSaveAudio = viewModel::saveAudio,
                    onSaveSubtitles = viewModel::saveSubtitles,
                    onCopied = viewModel::notifyCopied,
                    readText = viewModel::readText,
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
                    onStart = { spec -> viewModel.start(tool, spec) },
                    onCancel = viewModel::cancel,
                    onSave = viewModel::saveOutput,
                    saveFormats = viewModel.saveFormats,
                    onSaveAudio = viewModel::saveAudio,
                    onSaveSubtitles = viewModel::saveSubtitles,
                    onCopied = viewModel::notifyCopied,
                    readText = viewModel::readText,
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
                        ),
                    )

                    Tab.SETTINGS -> SettingsScreen(
                        settings = settings ?: UserSettings(),
                        appVersion = appVersion,
                        modelStorage = modelStorage,
                        outputStorage = outputStorage,
                        busy = state.activeJobs.isNotEmpty(),
                        showTranscription = Tool.TRANSCRIBE !in viewModel.unsupportedTools,
                        actions = SettingsActions(
                            onThemeMode = viewModel::setThemeMode,
                            onDynamicColor = viewModel::setDynamicColor,
                            onPureBlack = viewModel::setPureBlack,
                            onKeepScreenOn = viewModel::setKeepScreenOn,
                            onDefaultLanguage = viewModel::setDefaultLanguage,
                            onTranscriptionModel = viewModel::setTranscriptionModel,
                            onDeleteModel = viewModel::deleteModel,
                            onClearHistory = viewModel::clearHistory,
                        ),
                    )
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
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                subtitle = { Text(stringResource(Res.string.home_subtitle), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                actions = {
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
                ToolCategory.entries.forEach { category ->
                    item(key = "title-${category.name}") {
                        Text(
                            stringResource(category.title),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
                        )
                    }
                    Tool.entries.filter { it.category == category }.chunked(columns).forEach { row ->
                        item(key = row.joinToString { it.name }) {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                                row.forEach { tool ->
                                    ToolTile(
                                        tool,
                                        available = tool !in unsupported,
                                        onClick = { onOpen(tool) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                                // An incomplete last row keeps every tile the same width.
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
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

private val TileMinWidth = 168.dp
private val TileGap = 10.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolTile(tool: Tool, available: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = if (available) tool.category.colors(colors) else colors.surfaceContainerHighest to colors.onSurfaceVariant
    Card(
        onClick = onClick,
        enabled = available,
        modifier = modifier.heightIn(min = 164.dp),
        shape = MaterialTheme.shapes.extraLarge,
        // Unavailable tools recede (lower-emphasis container) instead of turning lighter.
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainerLowest,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(icon = tool.icon, shape = tool.shape.toShape(), container = container, content = content, size = 52.dp)
                if (!available) {
                    Spacer(Modifier.weight(1f))
                    Surface(color = colors.surfaceContainerHighest, shape = CircleShape) {
                        Text(
                            stringResource(Res.string.badge_android_only),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(tool.title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(tool.description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
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
