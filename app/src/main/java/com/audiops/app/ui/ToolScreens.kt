package com.audiops.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.AudiopsState
import com.audiops.app.R
import com.audiops.app.SpeechLanguage
import com.audiops.app.Work
import kotlinx.coroutines.launch

/** Events from [EnhanceScreen]. The screen holds no state of its own (state hoisting). */
data class EnhanceActions(
    val onBack: () -> Unit,
    val onChoose: (Uri, String) -> Unit,
    val onEnhance: () -> Unit,
    val onCancel: () -> Unit,
    val onSave: (Uri?) -> Unit,
    val onDiscard: () -> Unit,
)

/** Events from [TranscribeScreen]. */
data class TranscribeActions(
    val onBack: () -> Unit,
    val onChoose: (Uri, String) -> Unit,
    val onLanguage: (SpeechLanguage) -> Unit,
    val onTranscribe: () -> Unit,
    val onCancel: () -> Unit,
    val onSave: (Uri?) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnhanceScreen(state: AudiopsState, snackbar: SnackbarHostState, actions: EnhanceActions) {
    val pickRecording = rememberAudioPicker(actions.onChoose)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        actions.onSave(uri)
    }
    ResultHaptics(success = state.enhanced != null, error = state.error)

    ToolScaffold(
        title = stringResource(R.string.enhance_title),
        subtitle = stringResource(R.string.enhance_intro),
        busy = state.busy,
        onBack = actions.onBack,
        snackbar = snackbar,
    ) {
        StepTitle(1, stringResource(R.string.step_recording))
        Spacer(Modifier.height(12.dp))
        RecordingCard(state.input?.name, enabled = !state.busy, onChoose = pickRecording)
        Spacer(Modifier.height(28.dp))

        StepTitle(2, stringResource(R.string.step_enhance))
        Spacer(Modifier.height(12.dp))
        ActionPanel(
            hint = stringResource(R.string.enhance_hint),
            running = state.work as? Work.Running,
            actionLabel = stringResource(R.string.action_enhance),
            actionIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null) },
            actionEnabled = state.input != null,
            onAction = actions.onEnhance,
            onCancel = actions.onCancel,
        )

        ErrorSection(state.error)

        AnimatedVisibility(
            visible = state.enhanced != null && !state.busy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(24.dp))
                ResultCard(
                    title = stringResource(R.string.enhance_result_title),
                    body = stringResource(R.string.enhance_result_body),
                ) {
                    Button(
                        onClick = { saver.launch(derivedFileName(state.input?.name, "enhanced", "wav")) },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PrimaryButtonPadding,
                        modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_save_wav))
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = actions.onDiscard,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.action_discard)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranscribeScreen(state: AudiopsState, snackbar: SnackbarHostState, actions: TranscribeActions) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.message_copied)
    val pickRecording = rememberAudioPicker(actions.onChoose)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        actions.onSave(uri)
    }
    ResultHaptics(success = state.transcript != null, error = state.error)

    ToolScaffold(
        title = stringResource(R.string.transcribe_title),
        subtitle = stringResource(R.string.transcribe_intro),
        busy = state.busy,
        onBack = actions.onBack,
        snackbar = snackbar,
    ) {
        StepTitle(1, stringResource(R.string.step_recording))
        Spacer(Modifier.height(12.dp))
        RecordingCard(state.input?.name, enabled = !state.busy, onChoose = pickRecording)
        Spacer(Modifier.height(28.dp))

        StepTitle(2, stringResource(R.string.step_language))
        Spacer(Modifier.height(12.dp))
        LanguageButtonGroup(
            selected = state.language,
            enabled = !state.busy,
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                actions.onLanguage(it)
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.transcribe_language_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        StepTitle(3, stringResource(R.string.step_transcribe))
        Spacer(Modifier.height(12.dp))
        ActionPanel(
            hint = stringResource(R.string.transcribe_hint),
            running = state.work as? Work.Running,
            actionLabel = stringResource(R.string.action_transcribe),
            actionIcon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null) },
            actionEnabled = state.input != null,
            onAction = actions.onTranscribe,
            onCancel = actions.onCancel,
        )

        ErrorSection(state.error)

        val transcript = state.transcript
        AnimatedVisibility(
            visible = transcript != null && !state.busy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.transcript_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(transcript.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        }
                        Spacer(Modifier.height(16.dp))
                        // Connected button group, Material 3 Expressive style.
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            FilledTonalButton(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(ClipData.newPlainText(copiedMessage, transcript.orEmpty()))
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scope.launch { snackbar.showSnackbar(copiedMessage) }
                                },
                                shapes = ConnectedLeading,
                                modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_copy))
                            }
                            FilledTonalButton(
                                onClick = { saver.launch(derivedFileName(state.input?.name, "transcript", "txt")) },
                                shapes = ConnectedTrailing,
                                modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_save_text))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Connected pair for plain buttons: pill outer edge, tight inner corners; squarer when pressed.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ConnectedLeading = ButtonShapes(
    shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15),
    pressedShape = RoundedCornerShape(12.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ConnectedTrailing = ButtonShapes(
    shape = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50),
    pressedShape = RoundedCornerShape(12.dp),
)

/** Hint, then either the primary action or live progress with Cancel. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionPanel(
    hint: String,
    running: Work.Running?,
    actionLabel: String,
    actionIcon: @Composable () -> Unit,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            if (running != null) {
                WorkStatus(running)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PrimaryButtonPadding,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PrimaryButtonPadding,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
                ) {
                    actionIcon()
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Connected toggle buttons for the spoken language (replaces chips in M3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageButtonGroup(selected: SpeechLanguage, enabled: Boolean, onSelect: (SpeechLanguage) -> Unit) {
    val options = SpeechLanguage.entries
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, language ->
            ToggleButton(
                checked = language == selected,
                onCheckedChange = { onSelect(language) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(stringResource(language.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ErrorSection(error: String?) {
    AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column {
            Spacer(Modifier.height(16.dp))
            ErrorCard(error.orEmpty())
        }
    }
}

/** A confirm buzz when a result arrives, a reject buzz on failure. */
@Composable
private fun ResultHaptics(success: Boolean, error: String?) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(success) { if (success) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    LaunchedEffect(error) { if (error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject) }
}
