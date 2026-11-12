package com.audiops.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiops.app.AudiopsViewModel
import com.audiops.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import com.audiops.app.Tool
import com.audiops.app.UserSettings

private enum class Destination(val depth: Int) { HOME(0), ENHANCE(1), TRANSCRIBE(1), SETTINGS(1) }

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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text()) }
    }

    PlatformBackHandler(enabled = state.settingsOpen || (state.tool != null && !state.busy)) { viewModel.back() }

    val destination = when {
        state.settingsOpen -> Destination.SETTINGS
        state.tool == Tool.ENHANCE -> Destination.ENHANCE
        state.tool == Tool.TRANSCRIBE -> Destination.TRANSCRIBE
        else -> Destination.HOME
    }

    // Shared-axis X transition driven by the expressive (spring) motion scheme.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val direction = if (targetState.depth >= initialState.depth) 1 else -1
            (fadeIn(effects) + slideInHorizontally(spatial) { it / 8 * direction }) togetherWith
                (fadeOut(effects) + slideOutHorizontally(spatial) { -it / 8 * direction })
        },
        label = "destination",
    ) { target ->
        when (target) {
            Destination.HOME -> HomeScreen(
                snackbar = snackbar,
                onOpen = viewModel::open,
                onOpenSettings = viewModel::openSettings,
            )
            Destination.ENHANCE -> EnhanceScreen(
                state = state,
                snackbar = snackbar,
                actions = EnhanceActions(
                    onBack = viewModel::back,
                    onChoose = viewModel::chooseInput,
                    onEnhance = viewModel::startEnhance,
                    onCancel = viewModel::cancel,
                    onSave = viewModel::saveEnhanced,
                    onDiscard = viewModel::discardEnhanced,
                ),
            )
            Destination.TRANSCRIBE -> TranscribeScreen(
                state = state,
                snackbar = snackbar,
                actions = TranscribeActions(
                    onBack = viewModel::back,
                    onChoose = viewModel::chooseInput,
                    onLanguage = viewModel::setLanguage,
                    onTranscribe = viewModel::startTranscribe,
                    onCancel = viewModel::cancel,
                    onSave = viewModel::saveTranscript,
                ),
            )
            Destination.SETTINGS -> SettingsScreen(
                settings = settings ?: UserSettings(),
                appVersion = appVersion,
                modelStorage = modelStorage,
                busy = state.busy,
                snackbar = snackbar,
                actions = SettingsActions(
                    onBack = viewModel::back,
                    onThemeMode = viewModel::setThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onPureBlack = viewModel::setPureBlack,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                    onDefaultLanguage = viewModel::setDefaultLanguage,
                    onDeleteModels = viewModel::deleteModels,
                ),
            )
        }
    }
}

private data class ToolEntry(
    val icon: ImageVector,
    val title: StringResource,
    val description: StringResource,
    val tool: Tool?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(snackbar: SnackbarHostState, onOpen: (Tool) -> Unit, onOpenSettings: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val tools = listOf(
        ToolEntry(Icons.Rounded.AutoFixHigh, Res.string.tool_enhance_title, Res.string.tool_enhance_description, Tool.ENHANCE),
        ToolEntry(Icons.Rounded.RecordVoiceOver, Res.string.tool_transcribe_title, Res.string.tool_transcribe_description, Tool.TRANSCRIBE),
        ToolEntry(Icons.Rounded.GraphicEq, Res.string.tool_denoise_title, Res.string.tool_denoise_description, null),
    )
    val iconShapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.SoftBurst)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                subtitle = { Text(stringResource(Res.string.home_subtitle)) },
                actions = {
                    OnDeviceBadge(Modifier.padding(end = 4.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(Res.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            // Connected list: 2 dp gaps, as in Android 16+ grouped surfaces.
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    HeroBanner(
                        title = stringResource(Res.string.home_hero_title),
                        body = stringResource(Res.string.home_hero_body),
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(Res.string.home_section_tools),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(tools) { index, entry ->
                ToolCard(
                    icon = entry.icon,
                    iconShape = iconShapes[index].toShape(),
                    title = stringResource(entry.title),
                    description = stringResource(entry.description),
                    badge = if (entry.tool == null) stringResource(Res.string.badge_soon) else null,
                    shape = groupShape(index, tools.size),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    onClick = entry.tool?.let { tool -> { onOpen(tool) } },
                )
            }
            item {
                Text(
                    stringResource(Res.string.home_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 8.dp, top = 20.dp),
                )
            }
        }
    }
}

/** Shape for item [index] of a connected group: large outer corners, small inner corners. */
fun groupShape(index: Int, count: Int, outer: androidx.compose.ui.unit.Dp = 28.dp, inner: androidx.compose.ui.unit.Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun OnDeviceBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, color = colors.secondaryContainer, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSecondaryContainer)
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(Res.string.badge_on_device),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer,
            )
        }
    }
}

/** A tool in the connected list. [onClick] null means the tool is not available yet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolCard(
    icon: ImageVector,
    iconShape: Shape,
    title: String,
    description: String,
    badge: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = onClick != null
    Card(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = icon,
                shape = iconShape,
                container = if (enabled) colors.primaryContainer else colors.surfaceContainerHighest,
                content = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                    )
                    if (badge != null) {
                        Spacer(Modifier.size(8.dp))
                        Surface(color = colors.tertiaryContainer, shape = CircleShape) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onTertiaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (enabled) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.primary)
            }
        }
    }
}
