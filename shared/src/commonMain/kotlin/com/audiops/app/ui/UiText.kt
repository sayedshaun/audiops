package com.audiops.app.ui

import androidx.compose.runtime.Composable
import com.audiops.app.SpeechLanguage
import com.audiops.app.Stage
import com.audiops.app.UiError
import com.audiops.app.UiMessage
import com.audiops.app.Work
import com.audiops.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Display label for a spoken language. */
val SpeechLanguage.label: StringResource
    get() = when (this) {
        SpeechLanguage.AUTO -> Res.string.language_auto
        SpeechLanguage.BANGLA -> Res.string.language_bangla
        SpeechLanguage.ENGLISH -> Res.string.language_english
        SpeechLanguage.HINDI -> Res.string.language_hindi
    }

/** Status line for running work, such as "Enhancing section 3 of 8…". */
@Composable
fun Work.Running.statusText(): String = when (stage) {
    Stage.PREPARING -> stringResource(Res.string.stage_preparing)
    Stage.DOWNLOADING_MODEL -> stringResource(Res.string.stage_model_download, index, total)
    Stage.ENHANCING -> stringResource(Res.string.stage_enhance_section, index, total)
    Stage.TRANSCRIBING -> stringResource(Res.string.stage_transcribe_section, index, total)
}

@Composable
fun UiError.message(): String = when (this) {
    is UiError.Processing -> stringResource(Res.string.error_processing_failed, detail ?: stringResource(Res.string.error_unknown))
    UiError.OutOfMemory -> stringResource(Res.string.error_out_of_memory)
    UiError.NoSpeech -> stringResource(Res.string.error_no_speech)
    UiError.Unsupported -> stringResource(Res.string.error_unsupported_platform)
}

suspend fun UiMessage.text(): String = when (this) {
    UiMessage.Enhanced -> getString(Res.string.message_enhanced)
    UiMessage.Transcribed -> getString(Res.string.message_transcribed)
    UiMessage.Saved -> getString(Res.string.message_saved)
    is UiMessage.SaveFailed -> getString(Res.string.error_save, detail ?: getString(Res.string.error_unknown))
    UiMessage.Cancelled -> getString(Res.string.message_cancelled)
    UiMessage.ModelsDeleted -> getString(Res.string.message_models_deleted)
}
