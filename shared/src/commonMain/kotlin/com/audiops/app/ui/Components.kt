package com.audiops.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiops.app.SaveFormat
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_more_formats
import com.audiops.shared.resources.action_save
import com.audiops.shared.resources.action_save_format
import com.audiops.shared.resources.back
import org.jetbrains.compose.resources.stringResource

/** Height for primary actions: the Material 3 Expressive medium button size (56 dp). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PrimaryButtonHeight: Dp = ButtonDefaults.MediumContainerHeight

/** Padding that matches [PrimaryButtonHeight]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PrimaryButtonPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)

/** An icon inside an expressive shape (cookie, sunny, burst…), as used across the app. */
@Composable
fun ShapedIcon(icon: ImageVector, shape: Shape, container: Color, content: Color, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.45f))
    }
}

/** Numbered step heading. The number sits in an expressive cookie shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepTitle(number: Int, title: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(MaterialShapes.Cookie4Sided.toShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
    }
}

/**
 * What a running job looks like: the status text and, under it, a progress bar while a model downloads,
 * or the recording's waveform filling in while it is processed ([peaks] from the recording, or a stable
 * shape from [seed] when there is none). Announced to screen readers as it changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkStatus(status: String, progress: Float?, downloading: Boolean = false, peaks: FloatArray? = null, seed: String = "") {
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress",
    )
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (downloading) {
                LoadingIndicator(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            !downloading -> WaveformProgress(progress, peaks, seed)
            progress != null -> LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            else -> LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Save for an audio result. With one format it is a single button; with several it is an
 * Expressive split button: the main part saves straight away in the first format (lossless WAV),
 * the arrow opens the other formats with a line on what each is for.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveButton(formats: List<SaveFormat>, modifier: Modifier = Modifier, onSave: (SaveFormat) -> Unit) {
    if (formats.size <= 1) {
        Button(
            onClick = { onSave(formats.first()) },
            shapes = ButtonDefaults.shapes(),
            contentPadding = PrimaryButtonPadding,
            modifier = modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_save), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { onSave(formats.first()) },
                    modifier = Modifier.heightIn(min = PrimaryButtonHeight),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.action_save_format, stringResource(formats.first().label)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            },
            trailingButton = {
                val more = stringResource(Res.string.action_more_formats)
                SplitButtonDefaults.TrailingButton(
                    checked = open,
                    onCheckedChange = { open = it },
                    modifier = Modifier.heightIn(min = PrimaryButtonHeight).semantics { contentDescription = more },
                ) {
                    val rotation by animateFloatAsState(
                        if (open) 180f else 0f,
                        MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "arrow",
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            rotationZ =
                                rotation
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            formats.forEach { format ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(format.label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(format.hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onSave(format)
                    },
                )
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(message, color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Result panel with a sunny-shaped success mark, headline, supporting text and actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultCard(title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedIcon(
                    icon = Icons.Rounded.CheckCircle,
                    shape = MaterialShapes.Sunny.toShape(),
                    container = colors.secondary,
                    content = colors.onSecondary,
                    size = 48.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = colors.onSecondaryContainer)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Base name plus suffix and extension for a suggested save name, such as `clip-enhanced.wav`. */
fun derivedFileName(source: String?, suffix: String, extension: String): String {
    val base = source?.substringBeforeLast('.', source)?.takeIf { it.isNotBlank() } ?: "recording"
    return "$base-$suffix.$extension"
}

/**
 * Scaffold shared by the tool screens: a collapsing flexible top bar with back navigation
 * and a scrolling body constrained to a readable width.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolScaffold(
    title: String,
    subtitle: String,
    busy: Boolean,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    /** Actions that float over the bottom of the screen (an Expressive floating toolbar), or null. */
    floatingToolbar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleSubtitle = rememberTitleBarSubtitle()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Medium) },
                subtitle = { titleSubtitle.Text(subtitle) },
                expandedHeight = titleSubtitle.expandedHeight(),
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            AnimatedVisibility(
                visible = floatingToolbar != null,
                enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.8f),
                exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.8f),
            ) {
                floatingToolbar?.invoke()
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // Room below the content for the floating toolbar, so it never covers the last line.
                    .padding(top = 20.dp, bottom = if (floatingToolbar != null) 104.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
    }
}

// Connected pair for plain buttons: pill outer edge, tight inner corners; squarer when pressed.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val ConnectedLeading = ButtonShapes(
    shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15),
    pressedShape = RoundedCornerShape(12.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val ConnectedTrailing = ButtonShapes(
    shape = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50),
    pressedShape = RoundedCornerShape(12.dp),
)

/** Shape for item [index] of a connected group: large outer corners, small inner corners. */
fun groupShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
