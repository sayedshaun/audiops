package com.audiops.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiops.shared.resources.*

/** Height for primary actions: the Material 3 Expressive medium button size (56 dp). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PrimaryButtonHeight: Dp = ButtonDefaults.MediumContainerHeight

/** Padding that matches [PrimaryButtonHeight]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PrimaryButtonPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)

/** An icon inside an expressive shape (cookie, sunny, burst…), as used across the app. */
@Composable
fun ShapedIcon(
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
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

/** Decorative bars that suggest audio. Hidden from accessibility services. */
@Composable
fun Waveform(modifier: Modifier, color: Color, bars: Int) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val step = size.width / bars
        for (i in 0 until bars) {
            val envelope = 0.2f + 0.8f * kotlin.math.sin(Math.PI.toFloat() * (i + 1) / (bars + 1))
            val variation = 0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(i * 2.19f))
            val height = (size.height * envelope * variation).coerceAtLeast(3.dp.toPx())
            val x = (i + 0.5f) * step
            drawLine(
                color,
                Offset(x, (size.height - height) / 2),
                Offset(x, (size.height + height) / 2),
                strokeWidth = (step * 0.38f).coerceAtLeast(1.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Hero banner: primary-container surface with expressive shapes in the background. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroBanner(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLargeIncreased)
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))),
    ) {
        // Decorative shapes, hidden from accessibility services.
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .offset(x = 64.dp, y = (-56).dp)
                .graphicsLayer { rotationZ = 18f }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(colors.primary.copy(alpha = 0.14f))
                .clearAndSetSemantics { },
        )
        Box(
            Modifier
                .size(96.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-24).dp, y = 36.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(colors.tertiary.copy(alpha = 0.16f))
                .clearAndSetSemantics { },
        )
        Column(Modifier.padding(24.dp)) {
            Text(
                title,
                color = colors.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            Spacer(Modifier.height(8.dp))
            Text(body, color = colors.onPrimaryContainer.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Waveform(Modifier.fillMaxWidth().height(36.dp), colors.primary.copy(alpha = 0.6f), bars = 43)
        }
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

/** Shows the selected recording, or an empty state, with a button to pick another. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordingCard(name: String?, enabled: Boolean, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedIcon(
                    icon = Icons.Rounded.AudioFile,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    container = colors.primaryContainer,
                    content = colors.onPrimaryContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name ?: stringResource(Res.string.recording_none),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (name != null) Res.string.recording_ready else Res.string.recording_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onChoose,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PrimaryButtonPadding,
                modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
            ) {
                Text(stringResource(if (name != null) Res.string.action_change else Res.string.action_choose))
            }
        }
    }
}

/**
 * Progress for running work: the expressive loading indicator next to the status, and a
 * wavy progress bar. Announced to screen readers as it changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkStatus(status: String, progress: Float?) {
    val target = progress
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress",
    )
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (target != null) {
            LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                    .padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
    }
}
