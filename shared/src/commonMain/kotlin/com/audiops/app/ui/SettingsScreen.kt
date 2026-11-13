package com.audiops.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.SpeechLanguage
import com.audiops.app.ThemeMode
import com.audiops.app.UserSettings
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.action_clear
import com.audiops.shared.resources.action_delete
import com.audiops.shared.resources.clear_history_body
import com.audiops.shared.resources.clear_history_title
import com.audiops.shared.resources.license_androidx
import com.audiops.shared.resources.license_androidx_summary
import com.audiops.shared.resources.license_bangla
import com.audiops.shared.resources.license_bangla_summary
import com.audiops.shared.resources.license_resemble
import com.audiops.shared.resources.license_resemble_summary
import com.audiops.shared.resources.license_sherpa
import com.audiops.shared.resources.license_sherpa_summary
import com.audiops.shared.resources.open_link
import com.audiops.shared.resources.settings_default_language
import com.audiops.shared.resources.settings_dynamic_color
import com.audiops.shared.resources.settings_dynamic_color_desktop
import com.audiops.shared.resources.settings_dynamic_color_summary
import com.audiops.shared.resources.settings_dynamic_color_unsupported
import com.audiops.shared.resources.settings_keep_screen_on
import com.audiops.shared.resources.settings_keep_screen_on_summary
import com.audiops.shared.resources.settings_licenses
import com.audiops.shared.resources.settings_models
import com.audiops.shared.resources.settings_models_busy
import com.audiops.shared.resources.settings_models_delete_body
import com.audiops.shared.resources.settings_models_delete_title
import com.audiops.shared.resources.settings_models_measuring
import com.audiops.shared.resources.settings_models_none
import com.audiops.shared.resources.settings_models_used
import com.audiops.shared.resources.settings_outputs
import com.audiops.shared.resources.settings_outputs_used
import com.audiops.shared.resources.settings_privacy
import com.audiops.shared.resources.settings_privacy_summary
import com.audiops.shared.resources.settings_pure_black
import com.audiops.shared.resources.settings_pure_black_summary
import com.audiops.shared.resources.settings_section_about
import com.audiops.shared.resources.settings_section_appearance
import com.audiops.shared.resources.settings_section_processing
import com.audiops.shared.resources.settings_section_storage
import com.audiops.shared.resources.settings_section_transcription
import com.audiops.shared.resources.settings_theme
import com.audiops.shared.resources.settings_title
import com.audiops.shared.resources.settings_version
import com.audiops.shared.resources.theme_dark
import com.audiops.shared.resources.theme_light
import com.audiops.shared.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Events from [SettingsScreen]; the screen holds only transient dialog state. */
data class SettingsActions(
    val onThemeMode: (ThemeMode) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
    val onPureBlack: (Boolean) -> Unit,
    val onKeepScreenOn: (Boolean) -> Unit,
    val onDefaultLanguage: (SpeechLanguage) -> Unit,
    val onDeleteModels: () -> Unit,
    val onClearHistory: () -> Unit,
)

private data class LicenseEntry(val title: StringResource, val summary: StringResource, val url: String)

private val licenses = listOf(
    LicenseEntry(
        Res.string.license_resemble,
        Res.string.license_resemble_summary,
        "https://huggingface.co/SayedShaun/resemble-enhance-litert",
    ),
    LicenseEntry(Res.string.license_sherpa, Res.string.license_sherpa_summary, "https://github.com/k2-fsa/sherpa-onnx"),
    LicenseEntry(Res.string.license_bangla, Res.string.license_bangla_summary, "https://huggingface.co/SayedShaun/bangla-zipformer-onnx"),
    LicenseEntry(Res.string.license_androidx, Res.string.license_androidx_summary, "https://developer.android.com/jetpack/androidx"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    appVersion: String,
    modelStorage: Long?,
    outputStorage: Long?,
    busy: Boolean,
    showTranscription: Boolean,
    actions: SettingsActions,
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val storageText = modelStorage?.let { formatFileSize(it) }
    val hasModels = (modelStorage ?: 0L) > 0L

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { SectionHeader(stringResource(Res.string.settings_section_appearance)) }
            item {
                SettingsGroup(
                    { shape ->
                        Column(
                            Modifier
                                .clip(shape)
                                .fillMaxWidth(),
                        ) {
                            SettingsRow(
                                icon = Icons.Rounded.Palette,
                                title = stringResource(Res.string.settings_theme),
                                shape = RectangleShape,
                            )
                            ThemeModeSelector(
                                selected = settings.themeMode,
                                onSelect = actions.onThemeMode,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    },
                    { shape ->
                        SwitchRow(
                            icon = Icons.Rounded.Wallpaper,
                            title = stringResource(Res.string.settings_dynamic_color),
                            summary = stringResource(
                                when {
                                    supportsDynamicColor -> Res.string.settings_dynamic_color_summary
                                    isDesktop -> Res.string.settings_dynamic_color_desktop
                                    else -> Res.string.settings_dynamic_color_unsupported
                                },
                            ),
                            checked = settings.dynamicColor && supportsDynamicColor,
                            enabled = supportsDynamicColor,
                            shape = shape,
                            onCheckedChange = actions.onDynamicColor,
                        )
                    },
                    { shape ->
                        SwitchRow(
                            icon = Icons.Rounded.Contrast,
                            title = stringResource(Res.string.settings_pure_black),
                            summary = stringResource(Res.string.settings_pure_black_summary),
                            checked = settings.pureBlack,
                            shape = shape,
                            onCheckedChange = actions.onPureBlack,
                        )
                    },
                )
            }

            // Phone-only sections: transcription defaults and keeping the screen awake.
            if (showTranscription) {
                item { SectionHeader(stringResource(Res.string.settings_section_transcription)) }
                item {
                    SettingsGroup({ shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Translate,
                            title = stringResource(Res.string.settings_default_language),
                            summary = stringResource(settings.defaultLanguage.label),
                            shape = shape,
                            onClick = { showLanguageDialog = true },
                        )
                    })
                }
            }
            if (!isDesktop) {
                item { SectionHeader(stringResource(Res.string.settings_section_processing)) }
                item {
                    SettingsGroup({ shape ->
                        SwitchRow(
                            icon = Icons.Rounded.Smartphone,
                            title = stringResource(Res.string.settings_keep_screen_on),
                            summary = stringResource(Res.string.settings_keep_screen_on_summary),
                            checked = settings.keepScreenOn,
                            shape = shape,
                            onCheckedChange = actions.onKeepScreenOn,
                        )
                    })
                }
            }

            item { SectionHeader(stringResource(Res.string.settings_section_storage)) }
            item {
                val outputText = outputStorage?.let { formatFileSize(it) }
                SettingsGroup(
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Storage,
                            title = stringResource(Res.string.settings_models),
                            summary = when {
                                busy -> stringResource(Res.string.settings_models_busy)
                                storageText == null -> stringResource(Res.string.settings_models_measuring)
                                !hasModels -> stringResource(Res.string.settings_models_none)
                                else -> stringResource(Res.string.settings_models_used, storageText)
                            },
                            shape = shape,
                            trailing = {
                                TextButton(onClick = { showDeleteDialog = true }, enabled = hasModels && !busy) {
                                    Text(stringResource(Res.string.action_delete))
                                }
                            },
                        )
                    },
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.History,
                            title = stringResource(Res.string.settings_outputs),
                            summary = if (outputText == null) {
                                stringResource(Res.string.settings_models_measuring)
                            } else {
                                stringResource(Res.string.settings_outputs_used, outputText)
                            },
                            shape = shape,
                            trailing = {
                                TextButton(onClick = { showClearDialog = true }, enabled = (outputStorage ?: 0L) > 0L) {
                                    Text(stringResource(Res.string.action_clear))
                                }
                            },
                        )
                    },
                )
            }

            item { SectionHeader(stringResource(Res.string.settings_section_about)) }
            item {
                SettingsGroup(
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(Res.string.settings_version),
                            summary = appVersion,
                            shape = shape,
                        )
                    },
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.PrivacyTip,
                            title = stringResource(Res.string.settings_privacy),
                            summary = stringResource(Res.string.settings_privacy_summary),
                            shape = shape,
                        )
                    },
                )
            }

            item { SectionHeader(stringResource(Res.string.settings_licenses)) }
            item {
                SettingsGroup(
                    *licenses.map { entry ->
                        @Composable { shape: Shape ->
                            SettingsRow(
                                icon = Icons.Rounded.Gavel,
                                title = stringResource(entry.title),
                                summary = stringResource(entry.summary),
                                shape = shape,
                                onClick = { uriHandler.openUri(entry.url) },
                                trailing = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = stringResource(Res.string.open_link),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }.toTypedArray(),
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = settings.defaultLanguage,
            onSelect = {
                actions.onDefaultLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            title = { Text(stringResource(Res.string.clear_history_title)) },
            text = { Text(stringResource(Res.string.clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    actions.onClearHistory()
                }) { Text(stringResource(Res.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_models_delete_title)) },
            text = { Text(stringResource(Res.string.settings_models_delete_body, storageText.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    actions.onDeleteModels()
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** A connected group of rows (Android 16+ settings style): 2 dp gaps, rounded outer corners. */
@Composable
private fun SettingsGroup(vararg rows: @Composable (Shape) -> Unit) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, row -> row(groupShape(index, rows.size)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        // The whole row toggles, as in system Settings; the Switch itself is not focusable.
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(it)
            },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** Theme choice as a connected toggle-button group (Material 3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val options = listOf(
        Triple(ThemeMode.SYSTEM, Res.string.theme_system, Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.LIGHT, Res.string.theme_light, Icons.Rounded.LightMode),
        Triple(ThemeMode.DARK, Res.string.theme_dark, Icons.Rounded.DarkMode),
    )
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (mode, label, icon) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(mode)
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                // Unchecked buttons need their own fill: the group already sits on surfaceContainer.
                colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LanguageDialog(selected: SpeechLanguage, onSelect: (SpeechLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
        title = { Text(stringResource(Res.string.settings_default_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                SpeechLanguage.entries.forEach { language ->
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
