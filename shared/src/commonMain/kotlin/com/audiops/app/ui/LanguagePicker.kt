package com.audiops.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.audiops.app.SpeechLanguage
import com.audiops.app.transcriptionLanguages
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.settings_default_language
import org.jetbrains.compose.resources.stringResource

/** The spoken language for transcription, as a [SelectorCard] that opens a searchable [SelectionSheet]. */
@Composable
fun LanguageField(selected: SpeechLanguage, onSelect: (SpeechLanguage) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.settings_default_language)
    SelectorCard(
        icon = Icons.Rounded.Translate,
        label = title,
        value = stringResource(selected.label),
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        SelectionSheet(
            title = title,
            icon = Icons.Rounded.Translate,
            options = transcriptionLanguages,
            selected = selected,
            label = { stringResource(it.label) },
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}
