package com.audiops.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.Job
import com.audiops.app.JobStatus
import com.audiops.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Events from [ActivityTab]. */
data class ActivityActions(
    val onCancel: (jobId: String) -> Unit,
    val onDelete: (jobId: String) -> Unit,
    val onClear: () -> Unit,
    val onReopen: (Job) -> Unit,
    val onSave: (Job, destination: String?) -> Unit,
    val onCopied: () -> Unit,
    val readText: suspend (path: String) -> String?,
)

/** Running and queued jobs, then history. Tap a job for its result. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityTab(jobs: List<Job>, actions: ActivityActions) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val active = jobs.filter { it.status.active }
    val finished = jobs.filter { !it.status.active }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.tab_activity)) },
                subtitle = { Text(stringResource(Res.string.activity_subtitle)) },
                actions = {
                    if (finished.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(Res.string.action_clear_history))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (jobs.isEmpty()) {
            EmptyActivity(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (active.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.activity_in_progress)) }
                    itemsIndexed(active, key = { _, job -> job.id }) { index, job ->
                        JobRow(job, groupShapeFor(index, active.size), onClick = { selectedId = job.id })
                    }
                }
                if (finished.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.activity_recent)) }
                    itemsIndexed(finished, key = { _, job -> job.id }) { index, job ->
                        JobRow(job, groupShapeFor(index, finished.size), onClick = { selectedId = job.id })
                    }
                }
            }
        }
    }

    val selected = jobs.firstOrNull { it.id == selectedId }
    if (selected != null) JobSheet(selected, actions, onDismiss = { selectedId = null })
    // The job was deleted or cleared while its sheet was open: forget the selection.
    LaunchedEffect(selectedId, selected == null) { if (selected == null) selectedId = null }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(Res.string.clear_history_title)) },
            text = { Text(stringResource(Res.string.clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.onClear()
                }) { Text(stringResource(Res.string.action_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

private fun groupShapeFor(index: Int, count: Int) = groupShape(index, count, outer = 24.dp, inner = 6.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobRow(job: Job, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = job.tool.category.colors(colors)
    val supporting = when (job.status) {
        JobStatus.QUEUED, JobStatus.RUNNING -> "${stringResource(job.tool.title)} · ${job.progressText()}"
        JobStatus.FAILED -> "${stringResource(job.tool.title)} · ${stringResource(job.status.title)}"
        else -> listOfNotNull(
            stringResource(job.tool.title),
            job.spec.summary(),
            relativeTime(job.finishedAt ?: job.createdAt),
        ).joinToString(" · ")
    }
    ListItem(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().clip(shape).clickable(onClick = onClick),
        headlineContent = { Text(job.inputName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { ShapedIcon(job.tool.icon, job.tool.shape.toShape(), container, content, size = 44.dp) },
        trailingContent = { StatusIndicator(job) },
        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
    )
}

@Composable
private fun StatusIndicator(job: Job) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        when (job.status) {
            JobStatus.RUNNING -> {
                val fraction = job.progress?.fraction
                if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            JobStatus.QUEUED -> Icon(Icons.Rounded.Schedule, stringResource(Res.string.status_queued), tint = colors.onSurfaceVariant)
            JobStatus.DONE -> Icon(Icons.Rounded.CheckCircle, stringResource(Res.string.status_done), tint = colors.primary)
            JobStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, stringResource(Res.string.status_failed), tint = colors.error)
            JobStatus.CANCELLED -> Icon(Icons.Rounded.Block, stringResource(Res.string.status_cancelled), tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobSheet(job: Job, actions: ActivityActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val (container, content) = job.tool.category.colors(colors)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(job.tool.icon, job.tool.shape.toShape(), container, content, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.inputName, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(stringResource(job.tool.title), job.spec.summary(), relativeTime(job.finishedAt ?: job.createdAt))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            JobCard(job, JobCardActions(actions.onCancel, actions.onSave, actions.onCopied, actions.readText))
            if (!job.status.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            actions.onReopen(job)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_open_tool), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            actions.onDelete(job.id)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_delete), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyActivity(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapedIcon(Icons.Rounded.History, MaterialShapes.Cookie12Sided.toShape(), colors.secondaryContainer, colors.onSecondaryContainer, size = 112.dp)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.activity_empty_title), style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.activity_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
