package com.audiops.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.audiops.app.Job
import com.audiops.app.JobSpec
import com.audiops.app.Tool
import com.audiops.app.TtsVoice
import com.audiops.app.defaultTtsVoice
import com.audiops.app.ttsLanguages
import com.audiops.app.ttsVoice
import com.audiops.app.ttsVoices
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.speak_hint
import com.audiops.shared.resources.speak_language
import com.audiops.shared.resources.speak_non_commercial
import com.audiops.shared.resources.speak_speed
import com.audiops.shared.resources.speak_text_count
import com.audiops.shared.resources.speak_text_label
import com.audiops.shared.resources.speak_voice
import com.audiops.shared.resources.speak_voice_summary
import com.audiops.shared.resources.step_text
import com.audiops.shared.resources.step_voice
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Longest text accepted in one job (about 10 minutes of speech). */
private const val MAX_TEXT_LENGTH = 10_000

/**
 * Text to speech: type or paste text, pick a language and voice and a speed, and get a WAV file.
 * There is no input recording; the latest speech job is shown below the button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeakScreen(latestJob: Job?, snackbar: SnackbarHostState, actions: ToolActions) {
    val tool = Tool.SPEAK
    var text by rememberSaveable { mutableStateOf("") }
    var voiceId by rememberSaveable { mutableStateOf(defaultTtsVoice.id) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    val voice = ttsVoice(voiceId) ?: defaultTtsVoice
    ResultHaptics(latestJob)
    val jobCardRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(latestJob?.id, latestJob?.status) {
        if (latestJob != null) {
            delay(120) // let the card enter the layout first
            jobCardRequester.bringIntoView()
        }
    }

    ToolScaffold(
        title = stringResource(tool.title),
        subtitle = stringResource(tool.description),
        busy = false,
        onBack = actions.onBack,
        snackbar = snackbar,
    ) {
        StepTitle(1, stringResource(Res.string.step_text))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_TEXT_LENGTH) },
            label = { Text(stringResource(Res.string.speak_text_label)) },
            supportingText = { Text(stringResource(Res.string.speak_text_count, text.length, MAX_TEXT_LENGTH)) },
            minLines = 5,
            maxLines = 12,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        StepTitle(2, stringResource(Res.string.step_voice))
        Spacer(Modifier.height(12.dp))
        OptionsCard {
            var languageDialog by rememberSaveable { mutableStateOf(false) }
            var voiceDialog by rememberSaveable { mutableStateOf(false) }
            val voicesForLanguage = ttsVoices.filter { it.language == voice.language }
            PickerField(
                icon = Icons.Rounded.Translate,
                label = stringResource(Res.string.speak_language),
                value = stringResource(ttsLanguageLabel(voice.language)),
                onClick = { languageDialog = true },
            )
            if (voicesForLanguage.size > 1) {
                PickerField(
                    icon = Icons.Rounded.Person,
                    label = stringResource(Res.string.speak_voice),
                    value = voice.name,
                    onClick = { voiceDialog = true },
                )
            }
            Hint(voiceSummary(voice))
            LabeledSlider(
                label = stringResource(Res.string.speak_speed, formatSpeed(speed)),
                value = speed,
                range = 0.5f..2f,
                step = 0.05f,
                onChange = { speed = it },
            )
            if (languageDialog) {
                ChoiceDialog(
                    title = stringResource(Res.string.speak_language),
                    icon = Icons.Rounded.Translate,
                    options = ttsLanguages,
                    selected = voice.language,
                    label = { stringResource(ttsLanguageLabel(it)) },
                    detail = { language ->
                        ttsVoices.first { it.language == language }.takeIf { it.nonCommercial }?.let {
                            stringResource(Res.string.speak_non_commercial)
                        }
                    },
                    onSelect = { language ->
                        voiceId = ttsVoices.first { it.language == language }.id
                        languageDialog = false
                    },
                    onDismiss = { languageDialog = false },
                )
            }
            if (voiceDialog) {
                ChoiceDialog(
                    title = stringResource(Res.string.speak_voice),
                    icon = Icons.Rounded.Person,
                    options = voicesForLanguage.map { it.id },
                    selected = voice.id,
                    label = { id -> ttsVoice(id)?.name.orEmpty() },
                    detail = { null },
                    onSelect = {
                        voiceId = it
                        voiceDialog = false
                    },
                    onDismiss = { voiceDialog = false },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { actions.onStart(JobSpec.Speak(text.trim(), voice.id, speed)) },
            enabled = text.isNotBlank(),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PrimaryButtonPadding,
            modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(tool.icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(tool.action), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.speak_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(visible = latestJob != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            if (latestJob != null) {
                Column(Modifier.bringIntoViewRequester(jobCardRequester)) {
                    Spacer(Modifier.height(24.dp))
                    JobCard(
                        job = latestJob,
                        actions = JobCardActions(
                            actions.onCancel,
                            actions.onSave,
                            actions.onSaveSubtitles,
                            actions.onCopied,
                            actions.readText,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun voiceSummary(voice: TtsVoice): String {
    val summary = stringResource(Res.string.speak_voice_summary, voice.name, voice.license, voice.downloadMb)
    return if (voice.nonCommercial) "$summary · ${stringResource(Res.string.speak_non_commercial)}" else summary
}

private fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

/** A tappable field showing the current choice; opens a dialog. */
@Composable
private fun PickerField(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.DropdownList, onClick = onClick),
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** Single-choice list in a dialog, scrolling when long. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    detail: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            detail(option)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
