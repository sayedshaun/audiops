package com.audiops.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiops.app.AiModel
import com.audiops.app.SpeechLanguage
import com.audiops.app.ThemeMode
import com.audiops.app.UserSettings
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.action_clear
import com.audiops.shared.resources.action_close
import com.audiops.shared.resources.action_delete
import com.audiops.shared.resources.back
import com.audiops.shared.resources.clear_history_body
import com.audiops.shared.resources.clear_history_title
import com.audiops.shared.resources.license_androidx
import com.audiops.shared.resources.license_androidx_summary
import com.audiops.shared.resources.license_bangla
import com.audiops.shared.resources.license_bangla_summary
import com.audiops.shared.resources.license_dsp
import com.audiops.shared.resources.license_dsp_summary
import com.audiops.shared.resources.license_litert
import com.audiops.shared.resources.license_litert_summary
import com.audiops.shared.resources.license_notices
import com.audiops.shared.resources.license_notices_summary
import com.audiops.shared.resources.license_omnilingual
import com.audiops.shared.resources.license_omnilingual_summary
import com.audiops.shared.resources.license_resemble
import com.audiops.shared.resources.license_resemble_summary
import com.audiops.shared.resources.license_sherpa
import com.audiops.shared.resources.license_sherpa_summary
import com.audiops.shared.resources.license_whisper
import com.audiops.shared.resources.license_whisper_summary
import com.audiops.shared.resources.open_link
import com.audiops.shared.resources.settings_about
import com.audiops.shared.resources.settings_about_summary
import com.audiops.shared.resources.settings_app_license
import com.audiops.shared.resources.settings_app_license_summary
import com.audiops.shared.resources.settings_default_language
import com.audiops.shared.resources.settings_dynamic_color
import com.audiops.shared.resources.settings_dynamic_color_desktop
import com.audiops.shared.resources.settings_dynamic_color_summary
import com.audiops.shared.resources.settings_dynamic_color_unsupported
import com.audiops.shared.resources.settings_keep_screen_on
import com.audiops.shared.resources.settings_keep_screen_on_summary
import com.audiops.shared.resources.settings_licenses
import com.audiops.shared.resources.settings_licenses_summary
import com.audiops.shared.resources.settings_model_summary
import com.audiops.shared.resources.settings_models
import com.audiops.shared.resources.settings_models_busy
import com.audiops.shared.resources.settings_models_delete_body
import com.audiops.shared.resources.settings_models_delete_title
import com.audiops.shared.resources.settings_models_measuring
import com.audiops.shared.resources.settings_models_none
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
import com.audiops.shared.resources.settings_source_code
import com.audiops.shared.resources.settings_source_code_summary
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
    val onDeleteModel: (AiModel) -> Unit,
    val onClearHistory: () -> Unit,
)

/** [androidOnly]: AI components that are bundled or downloaded only on Android. */
private data class LicenseEntry(val title: StringResource, val summary: StringResource, val url: String, val androidOnly: Boolean = false)

private val licenses = listOf(
    LicenseEntry(
        Res.string.license_resemble,
        Res.string.license_resemble_summary,
        "https://huggingface.co/SayedShaun/resemble-enhance-litert",
        androidOnly = true,
    ),
    LicenseEntry(
        Res.string.license_omnilingual,
        Res.string.license_omnilingual_summary,
        "https://github.com/facebookresearch/omnilingual-asr",
        androidOnly = true,
    ),
    LicenseEntry(
        Res.string.license_whisper,
        Res.string.license_whisper_summary,
        "https://github.com/openai/whisper",
        androidOnly = true,
    ),
    LicenseEntry(Res.string.license_sherpa, Res.string.license_sherpa_summary, "https://github.com/k2-fsa/sherpa-onnx", androidOnly = true),
    LicenseEntry(
        Res.string.license_bangla,
        Res.string.license_bangla_summary,
        "https://huggingface.co/SayedShaun/bangla-zipformer-onnx",
        androidOnly = true,
    ),
    LicenseEntry(Res.string.license_litert, Res.string.license_litert_summary, "https://ai.google.dev/edge/litert", androidOnly = true),
    LicenseEntry(Res.string.license_dsp, Res.string.license_dsp_summary, "https://github.com/wendykierp/JTransforms"),
    LicenseEntry(Res.string.license_androidx, Res.string.license_androidx_summary, "https://developer.android.com/jetpack/androidx"),
)

/** Bundled full licence texts (generated from licenses/; see ThirdPartyNoticesTest). */
private const val NOTICES_FILE = "files/third_party_notices.txt"

private const val SOURCE_URL = "https://github.com/sayedshaun/audiops"
private const val LICENSE_URL = "https://github.com/sayedshaun/audiops/blob/main/LICENSE"

/** Settings has a main page and two sub-pages, opened from the About section. */
private enum class SettingsPage { MAIN, ABOUT, LICENSES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    appVersion: String,
    modelStorage: Map<AiModel, Long>?,
    outputStorage: Long?,
    busy: Boolean,
    showTranscription: Boolean,
    actions: SettingsActions,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    // Hoisted so returning from a sub-page keeps the main page's scroll position.
    val mainListState = rememberLazyListState()
    PlatformBackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    when (page) {
        SettingsPage.MAIN -> SettingsMain(
            settings = settings,
            appVersion = appVersion,
            modelStorage = modelStorage,
            outputStorage = outputStorage,
            busy = busy,
            showTranscription = showTranscription,
            actions = actions,
            listState = mainListState,
            onOpen = { page = it },
        )

        SettingsPage.ABOUT -> AboutPage(appVersion, onBack = { page = SettingsPage.MAIN })

        SettingsPage.LICENSES -> LicensesPage(showTranscription, onBack = { page = SettingsPage.MAIN })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsMain(
    settings: UserSettings,
    appVersion: String,
    modelStorage: Map<AiModel, Long>?,
    outputStorage: Long?,
    busy: Boolean,
    showTranscription: Boolean,
    actions: SettingsActions,
    listState: LazyListState,
    onOpen: (SettingsPage) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by rememberSaveable { mutableStateOf<AiModel?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

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
            state = listState,
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
                // One row per downloaded model, each with its own Delete button.
                val modelRows: List<@Composable (Shape) -> Unit> = when {
                    modelStorage.isNullOrEmpty() -> listOf { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Storage,
                            title = stringResource(Res.string.settings_models),
                            summary = stringResource(
                                if (modelStorage == null) Res.string.settings_models_measuring else Res.string.settings_models_none,
                            ),
                            shape = shape,
                        )
                    }

                    else -> modelStorage.map { (model, bytes) ->
                        @Composable { shape: Shape ->
                            SettingsRow(
                                icon = model.icon,
                                title = stringResource(model.title),
                                summary = if (busy) {
                                    stringResource(Res.string.settings_models_busy)
                                } else {
                                    stringResource(Res.string.settings_model_summary, stringResource(model.purpose), formatFileSize(bytes))
                                },
                                shape = shape,
                                trailing = {
                                    TextButton(onClick = { deleteTarget = model }, enabled = !busy) {
                                        Text(stringResource(Res.string.action_delete))
                                    }
                                },
                            )
                        }
                    }
                }
                val outputs: @Composable (Shape) -> Unit = { shape ->
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
                }
                SettingsGroup(*(modelRows + outputs).toTypedArray())
            }

            item { SectionHeader(stringResource(Res.string.settings_section_about)) }
            item {
                SettingsGroup(
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(Res.string.settings_about),
                            summary = stringResource(Res.string.settings_about_summary, appVersion),
                            shape = shape,
                            onClick = { onOpen(SettingsPage.ABOUT) },
                            trailing = { Chevron() },
                        )
                    },
                    { shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Gavel,
                            title = stringResource(Res.string.settings_licenses),
                            summary = stringResource(Res.string.settings_licenses_summary),
                            shape = shape,
                            onClick = { onOpen(SettingsPage.LICENSES) },
                            trailing = { Chevron() },
                        )
                    },
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            title = stringResource(Res.string.settings_default_language),
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

    deleteTarget?.let { model ->
        val size = modelStorage?.get(model)?.let { formatFileSize(it) }.orEmpty()
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_models_delete_title, stringResource(model.title))) },
            text = { Text(stringResource(Res.string.settings_models_delete_body, size)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    actions.onDeleteModel(model)
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** About Audiops: version, privacy, source code and the app's own licence. */
@Composable
private fun AboutPage(appVersion: String, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsSubPage(stringResource(Res.string.settings_about), onBack) {
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
                { shape ->
                    SettingsRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(Res.string.settings_source_code),
                        summary = stringResource(Res.string.settings_source_code_summary),
                        shape = shape,
                        onClick = { uriHandler.openUri(SOURCE_URL) },
                        trailing = { OpenLinkIcon() },
                    )
                },
                { shape ->
                    SettingsRow(
                        icon = Icons.Rounded.Gavel,
                        title = stringResource(Res.string.settings_app_license),
                        summary = stringResource(Res.string.settings_app_license_summary),
                        shape = shape,
                        onClick = { uriHandler.openUri(LICENSE_URL) },
                        trailing = { OpenLinkIcon() },
                    )
                },
            )
        }
    }
}

/** Every third-party licence: the bundled full texts, then one row per component. */
@Composable
private fun LicensesPage(showTranscription: Boolean, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var showNotices by rememberSaveable { mutableStateOf(false) }
    SettingsSubPage(stringResource(Res.string.settings_licenses), onBack) {
        item {
            val notices: @Composable (Shape) -> Unit = { shape ->
                SettingsRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(Res.string.license_notices),
                    summary = stringResource(Res.string.license_notices_summary),
                    shape = shape,
                    onClick = { showNotices = true },
                )
            }
            SettingsGroup(
                notices,
                *licenses.filter { showTranscription || !it.androidOnly }.map { entry ->
                    @Composable { shape: Shape ->
                        SettingsRow(
                            icon = Icons.Rounded.Gavel,
                            title = stringResource(entry.title),
                            summary = stringResource(entry.summary),
                            shape = shape,
                            onClick = { uriHandler.openUri(entry.url) },
                            trailing = { OpenLinkIcon() },
                        )
                    }
                }.toTypedArray(),
            )
        }
    }
    if (showNotices) {
        NoticesDialog(onDismiss = { showNotices = false })
    }
}

/** A Settings sub-page: large top app bar with Back, and a list of groups. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSubPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun OpenLinkIcon() {
    Icon(
        Icons.AutoMirrored.Rounded.OpenInNew,
        contentDescription = stringResource(Res.string.open_link),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun NoticesDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        text = runCatching { Res.readBytes(NOTICES_FILE).decodeToString() }.getOrDefault("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
        title = { Text(stringResource(Res.string.license_notices)) },
        text = {
            Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}
