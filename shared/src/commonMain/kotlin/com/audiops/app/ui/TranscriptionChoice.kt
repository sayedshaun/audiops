package com.audiops.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.audiops.app.SpeechLanguage
import com.audiops.app.TranscriptionModel
import com.audiops.app.recommendedTranscriptionModel
import com.audiops.app.supports
import com.audiops.app.transcriptionModelsFor
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.model_choice_bangla
import com.audiops.shared.resources.model_choice_bangla_summary
import com.audiops.shared.resources.model_choice_omnilingual
import com.audiops.shared.resources.model_choice_omnilingual_summary
import com.audiops.shared.resources.model_choice_parakeet
import com.audiops.shared.resources.model_choice_parakeet_summary
import com.audiops.shared.resources.model_choice_recommended
import com.audiops.shared.resources.model_choice_whisper
import com.audiops.shared.resources.model_choice_whisper_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The model a transcription in [language] uses: [chosen] when it supports the language, otherwise the recommended one. */
fun effectiveTranscriptionModel(language: SpeechLanguage, chosen: TranscriptionModel): TranscriptionModel =
    chosen.takeIf { it != TranscriptionModel.AUTO && it.supports(language) } ?: recommendedTranscriptionModel(language)

/** What a model choice is called on screen. Model names appear only under Settings → Downloaded models. */
private val TranscriptionModel.choiceTitle: StringResource
    get() = when (this) {
        TranscriptionModel.PARAKEET, TranscriptionModel.AUTO -> Res.string.model_choice_parakeet
        TranscriptionModel.BANGLA -> Res.string.model_choice_bangla
        TranscriptionModel.OMNILINGUAL -> Res.string.model_choice_omnilingual
        TranscriptionModel.WHISPER -> Res.string.model_choice_whisper
    }

private val TranscriptionModel.choiceSummary: StringResource
    get() = when (this) {
        TranscriptionModel.PARAKEET, TranscriptionModel.AUTO -> Res.string.model_choice_parakeet_summary
        TranscriptionModel.BANGLA -> Res.string.model_choice_bangla_summary
        TranscriptionModel.OMNILINGUAL -> Res.string.model_choice_omnilingual_summary
        TranscriptionModel.WHISPER -> Res.string.model_choice_whisper_summary
    }

/**
 * The models that can transcribe [language], as one connected group of radio rows; the recommended
 * one comes first with a badge.
 */
@Composable
fun TranscriptionModelChoice(language: SpeechLanguage, selected: TranscriptionModel, onSelect: (TranscriptionModel) -> Unit) {
    val models = transcriptionModelsFor(language)
    val current = effectiveTranscriptionModel(language, selected)
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        models.forEachIndexed { index, model ->
            val chosen = model == current
            Surface(
                color = if (chosen) colors.secondaryContainer else colors.surfaceContainerHigh,
                contentColor = if (chosen) colors.onSecondaryContainer else colors.onSurface,
                shape = groupShape(index, models.size, outer = 20.dp, inner = 4.dp),
                modifier = Modifier.fillMaxWidth().selectable(selected = chosen, role = Role.RadioButton) {
                    if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(model)
                },
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(model.choiceTitle),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (index == 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(color = colors.primary, contentColor = colors.onPrimary, shape = CircleShape) {
                                    Text(
                                        stringResource(Res.string.model_choice_recommended),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(model.choiceSummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (chosen) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
