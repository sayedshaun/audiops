package com.audiops.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.audiops.app.Job
import com.audiops.app.SubtitleFormat
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_copy
import com.audiops.shared.resources.action_name_speakers
import com.audiops.shared.resources.action_save_srt
import com.audiops.shared.resources.action_save_text
import com.audiops.shared.resources.action_save_vtt
import com.audiops.shared.resources.action_subtitles
import org.jetbrains.compose.resources.stringResource

/**
 * A transcript's actions as an Expressive floating toolbar: Copy, Save text and Subtitles (SRT or
 * VTT), with the main action in the attached button: Name speakers when the transcript has
 * speakers, Copy otherwise. Tool screens float it over the screen so it stays in reach on a long
 * transcript; the Activity tab shows it under the text.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranscriptToolbar(job: Job, actions: JobCardActions, modifier: Modifier = Modifier, floating: Boolean = true) {
    val path = job.outputPath ?: return
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val text by produceState<String?>(initialValue = job.preview, path, job.revision) { value = actions.readText(path) ?: job.preview }
    var naming by rememberSaveable(job.id) { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf(false) }
    val saveText = rememberFileSaver("text/plain") { actions.onSave(job, it) }
    val saveSrt = rememberFileSaver("application/x-subrip") { actions.onSaveSubtitles(job, SubtitleFormat.SRT, it) }
    val saveVtt = rememberFileSaver("text/vtt") { actions.onSaveSubtitles(job, SubtitleFormat.VTT, it) }
    val copy = {
        clipboard.setText(AnnotatedString(text.orEmpty()))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        actions.onCopied()
    }
    val hasSpeakers = job.speakersPath != null

    val nameSpeakers = { naming = true }
    // The other actions: Copy (when the main button names speakers), Save text, Subtitles.
    val items: @Composable () -> Unit = {
        if (hasSpeakers || !floating) {
            IconButton(onClick = copy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(Res.string.action_copy))
            }
        }
        IconButton(onClick = { saveText(derivedFileName(job.inputName, "transcript", "txt")) }) {
            Icon(Icons.Rounded.Save, contentDescription = stringResource(Res.string.action_save_text))
        }
        if (job.subtitlesPath != null) {
            Box {
                IconButton(onClick = { subtitles = true }) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(Res.string.action_subtitles))
                }
                DropdownMenu(expanded = subtitles, onDismissRequest = { subtitles = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_save_srt)) },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                        onClick = {
                            subtitles = false
                            saveSrt(derivedFileName(job.inputName, "subtitles", "srt"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_save_vtt)) },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                        onClick = {
                            subtitles = false
                            saveVtt(derivedFileName(job.inputName, "subtitles", "vtt"))
                        },
                    )
                }
            }
        }
    }

    if (floating) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = if (hasSpeakers) nameSpeakers else copy) {
                    if (hasSpeakers) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = stringResource(Res.string.action_name_speakers))
                    } else {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(Res.string.action_copy))
                    }
                }
            },
            modifier = modifier,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) { items() }
    } else {
        // Inline (in a card or sheet): one pill, with Name speakers as its labelled main action.
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = modifier,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            expandedShadowElevation = 0.dp,
            trailingContent = {
                if (hasSpeakers) {
                    FilledTonalButton(onClick = nameSpeakers, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.action_name_speakers))
                    }
                }
            },
        ) { items() }
    }

    if (naming) {
        SpeakerNamesSheet(
            job = job,
            load = actions.speakers,
            onSave = { names, remember ->
                naming = false
                actions.onNameSpeakers(job.id, names, remember)
            },
            onDismiss = { naming = false },
        )
    }
}
