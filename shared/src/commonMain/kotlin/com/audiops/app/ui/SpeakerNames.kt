package com.audiops.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.Job
import com.audiops.app.SpeakerInfo
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_save_names
import com.audiops.shared.resources.meeting_speaker
import com.audiops.shared.resources.speakers_name_label
import com.audiops.shared.resources.speakers_remember
import com.audiops.shared.resources.speakers_remember_hint
import com.audiops.shared.resources.speakers_sheet_body
import com.audiops.shared.resources.speakers_sheet_title
import com.audiops.shared.resources.speakers_voice_too_short
import org.jetbrains.compose.resources.stringResource

/**
 * Names the speakers of a transcript: one field per speaker under the first thing they said, and
 * whether to remember the named voices for later transcripts.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeakerNamesSheet(
    job: Job,
    load: suspend (Job) -> List<SpeakerInfo>,
    onSave: (names: List<String?>, remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val speakers by produceState<List<SpeakerInfo>?>(null, job.id, job.revision) { value = load(job) }
    val names = remember(speakers) { mutableStateListOf<String>().apply { speakers?.forEach { add(it.name.orEmpty()) } } }
    var remember by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    Icons.Rounded.RecordVoiceOver,
                    MaterialShapes.Clover4Leaf.toShape(),
                    colors.primaryContainer,
                    colors.onPrimaryContainer,
                    size = 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(Res.string.speakers_sheet_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                stringResource(Res.string.speakers_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            val list = speakers
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            list.forEachIndexed { index, speaker ->
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(Res.string.meeting_speaker, index + 1),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary,
                        )
                        if (speaker.sample.isNotEmpty()) {
                            Text(
                                "“${speaker.sample}”",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedTextField(
                            value = names.getOrElse(index) { "" },
                            onValueChange = { if (index < names.size) names[index] = it.take(MAX_NAME) },
                            label = { Text(stringResource(Res.string.speakers_name_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!speaker.hasVoice) {
                            Text(
                                stringResource(Res.string.speakers_voice_too_short),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .toggleable(value = remember, role = Role.Switch) {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        remember = it
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.speakers_remember), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.speakers_remember_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = remember, onCheckedChange = null)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSave(names.map { it.trim().ifEmpty { null } }, remember)
                },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PrimaryButtonPadding,
                modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_save_names), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Longest speaker name accepted. */
private const val MAX_NAME = 40
