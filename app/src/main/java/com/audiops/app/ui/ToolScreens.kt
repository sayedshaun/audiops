package com.audiops.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audiops.app.AudioOpsState
import com.audiops.app.AudioOpsViewModel
import com.audiops.app.R
import com.audiops.app.SpeechLanguage
import com.audiops.app.Work
import kotlinx.coroutines.launch

@Composable
fun EnhanceScreen(state: AudioOpsState, viewModel: AudioOpsViewModel, snackbar: SnackbarHostState) {
    val colors = MaterialTheme.colorScheme
    val running = state.work as? Work.Running
    val pickRecording = rememberAudioPicker { uri, name -> viewModel.chooseInput(uri, name) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        viewModel.saveEnhanced(uri)
    }

    ToolScaffold(
        title = stringResource(R.string.enhance_title),
        busy = state.busy,
        onBack = viewModel::back,
        snackbar = snackbar,
    ) {
        Text(
            stringResource(R.string.enhance_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        StepTitle(1, stringResource(R.string.step_recording))
        Spacer(Modifier.height(12.dp))
        RecordingCard(state.input?.name, enabled = !state.busy, onChoose = pickRecording)
        Spacer(Modifier.height(24.dp))

        StepTitle(2, stringResource(R.string.step_enhance))
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.enhance_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                if (running != null) {
                    WorkStatus(running)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = viewModel::cancel,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    Button(
                        onClick = viewModel::startEnhance,
                        enabled = state.input != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_enhance), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(message)
        }

        if (!state.busy) {
            state.enhanced?.let {
                Spacer(Modifier.height(24.dp))
                ResultCard(
                    title = stringResource(R.string.enhance_result_title),
                    body = stringResource(R.string.enhance_result_body),
                ) {
                    Button(
                        onClick = { saver.launch(derivedFileName(state.input?.name, "enhanced", "wav")) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_save_wav), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = viewModel::discardEnhanced, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_discard))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscribeScreen(state: AudioOpsState, viewModel: AudioOpsViewModel, snackbar: SnackbarHostState) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running = state.work as? Work.Running
    val copiedMessage = stringResource(R.string.message_copied)
    val pickRecording = rememberAudioPicker { uri, name -> viewModel.chooseInput(uri, name) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        viewModel.saveTranscript(uri)
    }

    ToolScaffold(
        title = stringResource(R.string.transcribe_title),
        busy = state.busy,
        onBack = viewModel::back,
        snackbar = snackbar,
    ) {
        Text(
            stringResource(R.string.transcribe_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        StepTitle(1, stringResource(R.string.step_recording))
        Spacer(Modifier.height(12.dp))
        RecordingCard(state.input?.name, enabled = !state.busy, onChoose = pickRecording)
        Spacer(Modifier.height(24.dp))

        StepTitle(2, stringResource(R.string.step_language))
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeechLanguage.entries.forEach { language ->
                FilterChip(
                    selected = state.language == language,
                    onClick = { viewModel.setLanguage(language) },
                    enabled = !state.busy,
                    label = { Text(stringResource(language.label)) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.transcribe_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        StepTitle(3, stringResource(R.string.step_transcribe))
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.transcribe_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                if (running != null) {
                    WorkStatus(running)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = viewModel::cancel,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    Button(
                        onClick = viewModel::startTranscribe,
                        enabled = state.input != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.action_transcribe), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(message)
        }

        state.transcript?.takeIf { !state.busy }?.let { text ->
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.transcript_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText(copiedMessage, text))
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_copy))
                        }
                        OutlinedButton(
                            onClick = { saver.launch(derivedFileName(state.input?.name, "transcript", "txt")) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_save_text))
                        }
                    }
                }
            }
        }
    }
}
