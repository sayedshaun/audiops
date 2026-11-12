package com.audiops.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.R
import com.audiops.app.Work
import com.audiops.app.queryDisplayName
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/** Brand mark: the waveform on a primary rounded square. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Waveform(Modifier.size(width = 24.dp, height = 20.dp), MaterialTheme.colorScheme.onPrimary, bars = 9)
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

/** Hero banner with a gradient surface and decorative waveform. */
@Composable
fun HeroBanner(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
                MaterialTheme.shapes.extraLarge,
            )
            .padding(24.dp),
    ) {
        Column {
            Text(
                title,
                color = colors.onPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(body, color = colors.onPrimary.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Waveform(Modifier.fillMaxWidth().height(36.dp), colors.onPrimary.copy(alpha = 0.7f), bars = 43)
        }
    }
}

/** Numbered step heading used in the tool screens. */
@Composable
fun StepTitle(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Shows the selected recording, or an empty state, with a button to pick another. */
@Composable
fun RecordingCard(name: String?, enabled: Boolean, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(colors.primaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name ?: stringResource(R.string.recording_none),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (name != null) R.string.recording_ready else R.string.recording_pick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onChoose,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (name != null) R.string.action_change else R.string.action_choose))
            }
        }
    }
}

/** Progress for running work. Announced to screen readers as it changes. */
@Composable
fun WorkStatus(running: Work.Running) {
    val fraction = running.progress
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(running.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = colors.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Result panel with a headline, supporting text and actions. */
@Composable
fun ResultCard(title: String, body: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * Returns a function that opens the system audio picker. [onPicked] receives the document
 * URI and its display name.
 */
@Composable
fun rememberAudioPicker(onPicked: (Uri, String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onPicked(uri, context.queryDisplayName(uri))
    }
    return { launcher.launch(arrayOf("audio/*")) }
}

/** Base name plus suffix and extension for a suggested save name, such as `clip-enhanced.wav`. */
fun derivedFileName(source: String?, suffix: String, extension: String): String {
    val base = source?.substringBeforeLast('.', source)?.takeIf { it.isNotBlank() } ?: "recording"
    return "$base-$suffix.$extension"
}

/**
 * Scaffold shared by the tool screens: a top bar with back navigation and a scrolling
 * body constrained to a readable width.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    busy: Boolean,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}
