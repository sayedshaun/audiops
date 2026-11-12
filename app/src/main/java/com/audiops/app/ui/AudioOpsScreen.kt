package com.audiops.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiops.app.AudioOpsViewModel
import com.audiops.app.R
import com.audiops.app.Tool

/** Root of the app: routes between the home screen and the tool screens. */
@Composable
fun AudioOpsScreen(viewModel: AudioOpsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = state.tool != null && !state.busy) { viewModel.back() }

    when (state.tool) {
        null -> HomeScreen(onOpen = viewModel::open, snackbar = snackbar)
        Tool.ENHANCE -> EnhanceScreen(state, viewModel, snackbar)
        Tool.TRANSCRIBE -> TranscribeScreen(state, viewModel, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (Tool) -> Unit, snackbar: SnackbarHostState) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                scrollBehavior = scrollBehavior,
                actions = { OnDeviceBadge(Modifier.padding(end = 12.dp)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    HeroBanner(
                        title = stringResource(R.string.home_hero_title),
                        body = stringResource(R.string.home_hero_body),
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.home_section_tools),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(
                listOf(
                    ToolEntry(
                        icon = Icons.Filled.AutoFixHigh,
                        title = R.string.tool_enhance_title,
                        description = R.string.tool_enhance_description,
                        enabled = true,
                        tool = Tool.ENHANCE,
                    ),
                    ToolEntry(
                        icon = Icons.Filled.GraphicEq,
                        title = R.string.tool_denoise_title,
                        description = R.string.tool_denoise_description,
                        enabled = false,
                        tool = null,
                    ),
                    ToolEntry(
                        icon = Icons.Filled.RecordVoiceOver,
                        title = R.string.tool_transcribe_title,
                        description = R.string.tool_transcribe_description,
                        enabled = true,
                        tool = Tool.TRANSCRIBE,
                    ),
                ),
            ) { entry ->
                ToolCard(
                    icon = entry.icon,
                    title = stringResource(entry.title),
                    description = stringResource(entry.description),
                    enabled = entry.enabled,
                    badge = if (entry.enabled) null else stringResource(R.string.badge_soon),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    onClick = { entry.tool?.let(onOpen) },
                )
            }
            item {
                Text(
                    stringResource(R.string.home_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

private data class ToolEntry(
    val icon: ImageVector,
    val title: Int,
    val description: Int,
    val enabled: Boolean,
    val tool: Tool?,
)

@Composable
private fun OnDeviceBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, color = colors.secondaryContainer, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSecondaryContainer)
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.badge_on_device),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    badge: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(
                        if (enabled) colors.primaryContainer else colors.surfaceVariant,
                        MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
        }
    }
}
