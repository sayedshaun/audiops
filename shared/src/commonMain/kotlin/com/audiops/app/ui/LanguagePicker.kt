package com.audiops.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.audiops.app.SpeechLanguage
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.language_field
import org.jetbrains.compose.resources.stringResource

/** The app's main languages, listed first and separated from the others in [LanguageDialog]. */
private val mainLanguages = setOf(SpeechLanguage.AUTO, SpeechLanguage.BANGLA, SpeechLanguage.ENGLISH, SpeechLanguage.HINDI)

/** A field showing the transcription language; opens [LanguageDialog]. */
@Composable
fun LanguageField(selected: SpeechLanguage, onSelect: (SpeechLanguage) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.DropdownList) { open = true },
        headlineContent = { Text(stringResource(selected.label)) },
        leadingContent = { Icon(Icons.Rounded.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
    if (open) {
        LanguageDialog(
            title = stringResource(Res.string.language_field),
            selected = selected,
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** Single-choice list of every [SpeechLanguage]: main languages first, then the rest. Scrolls on small screens. */
@Composable
fun LanguageDialog(title: String, selected: SpeechLanguage, onSelect: (SpeechLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                SpeechLanguage.entries.forEachIndexed { index, language ->
                    if (index > 0 && language !in mainLanguages && SpeechLanguage.entries[index - 1] in mainLanguages) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = language == selected, role = Role.RadioButton) { onSelect(language) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == selected, onClick = null)
                        Text(
                            stringResource(language.label),
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
