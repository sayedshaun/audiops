package com.audiops.app.ui

import androidx.compose.runtime.Composable
import com.audiops.app.AiModel
import com.audiops.app.AudioOperation
import com.audiops.app.EnhanceMode
import com.audiops.app.EqPreset
import com.audiops.app.FailureKind
import com.audiops.app.InputProblem
import com.audiops.app.Job
import com.audiops.app.JobSpec
import com.audiops.app.JobStatus
import com.audiops.app.LoudnessTarget
import com.audiops.app.Preset
import com.audiops.app.SaveFormat
import com.audiops.app.SeparatedStem
import com.audiops.app.SpeechLanguage
import com.audiops.app.Stage
import com.audiops.app.Tool
import com.audiops.app.ToolCategory
import com.audiops.app.TranscriptionModel
import com.audiops.app.UiMessage
import com.audiops.app.currentTimeMillis
import com.audiops.app.ttsLanguages
import com.audiops.app.ttsVoice
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_analyze
import com.audiops.shared.resources.action_enhance
import com.audiops.shared.resources.action_process
import com.audiops.shared.resources.action_separate
import com.audiops.shared.resources.action_speak
import com.audiops.shared.resources.action_transcribe
import com.audiops.shared.resources.category_ai
import com.audiops.shared.resources.category_clean_up
import com.audiops.shared.resources.category_edit
import com.audiops.shared.resources.category_effects
import com.audiops.shared.resources.category_utilities
import com.audiops.shared.resources.enhance_mode_fast
import com.audiops.shared.resources.enhance_mode_fast_description
import com.audiops.shared.resources.enhance_mode_studio
import com.audiops.shared.resources.enhance_mode_studio_description
import com.audiops.shared.resources.eq_bass_boost
import com.audiops.shared.resources.eq_remove_rumble
import com.audiops.shared.resources.eq_telephone
import com.audiops.shared.resources.eq_treble_boost
import com.audiops.shared.resources.eq_voice_clarity
import com.audiops.shared.resources.eq_warm
import com.audiops.shared.resources.error_import
import com.audiops.shared.resources.error_interrupted
import com.audiops.shared.resources.error_no_speech
import com.audiops.shared.resources.error_out_of_memory
import com.audiops.shared.resources.error_processing_failed
import com.audiops.shared.resources.error_record
import com.audiops.shared.resources.error_save
import com.audiops.shared.resources.error_unknown
import com.audiops.shared.resources.error_unreadable
import com.audiops.shared.resources.error_unsupported_format
import com.audiops.shared.resources.error_unsupported_platform
import com.audiops.shared.resources.format_m4a
import com.audiops.shared.resources.format_m4a_hint
import com.audiops.shared.resources.format_opus
import com.audiops.shared.resources.format_opus_hint
import com.audiops.shared.resources.format_wav
import com.audiops.shared.resources.format_wav_hint
import com.audiops.shared.resources.language_afrikaans
import com.audiops.shared.resources.language_amharic
import com.audiops.shared.resources.language_arabic
import com.audiops.shared.resources.language_auto
import com.audiops.shared.resources.language_azerbaijani
import com.audiops.shared.resources.language_bangla
import com.audiops.shared.resources.language_bulgarian
import com.audiops.shared.resources.language_burmese
import com.audiops.shared.resources.language_catalan
import com.audiops.shared.resources.language_chinese
import com.audiops.shared.resources.language_croatian
import com.audiops.shared.resources.language_czech
import com.audiops.shared.resources.language_danish
import com.audiops.shared.resources.language_dutch
import com.audiops.shared.resources.language_english
import com.audiops.shared.resources.language_estonian
import com.audiops.shared.resources.language_filipino
import com.audiops.shared.resources.language_finnish
import com.audiops.shared.resources.language_french
import com.audiops.shared.resources.language_german
import com.audiops.shared.resources.language_greek
import com.audiops.shared.resources.language_gujarati
import com.audiops.shared.resources.language_hebrew
import com.audiops.shared.resources.language_hindi
import com.audiops.shared.resources.language_hungarian
import com.audiops.shared.resources.language_indonesian
import com.audiops.shared.resources.language_italian
import com.audiops.shared.resources.language_japanese
import com.audiops.shared.resources.language_kannada
import com.audiops.shared.resources.language_kazakh
import com.audiops.shared.resources.language_khmer
import com.audiops.shared.resources.language_korean
import com.audiops.shared.resources.language_latvian
import com.audiops.shared.resources.language_lithuanian
import com.audiops.shared.resources.language_malay
import com.audiops.shared.resources.language_malayalam
import com.audiops.shared.resources.language_maltese
import com.audiops.shared.resources.language_marathi
import com.audiops.shared.resources.language_nepali
import com.audiops.shared.resources.language_norwegian
import com.audiops.shared.resources.language_persian
import com.audiops.shared.resources.language_polish
import com.audiops.shared.resources.language_portuguese
import com.audiops.shared.resources.language_punjabi
import com.audiops.shared.resources.language_romanian
import com.audiops.shared.resources.language_russian
import com.audiops.shared.resources.language_serbian
import com.audiops.shared.resources.language_slovak
import com.audiops.shared.resources.language_slovenian
import com.audiops.shared.resources.language_spanish
import com.audiops.shared.resources.language_swahili
import com.audiops.shared.resources.language_swedish
import com.audiops.shared.resources.language_tamil
import com.audiops.shared.resources.language_telugu
import com.audiops.shared.resources.language_thai
import com.audiops.shared.resources.language_turkish
import com.audiops.shared.resources.language_ukrainian
import com.audiops.shared.resources.language_urdu
import com.audiops.shared.resources.language_uzbek
import com.audiops.shared.resources.language_vietnamese
import com.audiops.shared.resources.loudness_broadcast
import com.audiops.shared.resources.loudness_podcast
import com.audiops.shared.resources.loudness_streaming
import com.audiops.shared.resources.message_cancelled
import com.audiops.shared.resources.message_copied
import com.audiops.shared.resources.message_history_cleared
import com.audiops.shared.resources.message_input_ready
import com.audiops.shared.resources.message_job_failed
import com.audiops.shared.resources.message_job_finished
import com.audiops.shared.resources.message_models_deleted
import com.audiops.shared.resources.message_saved
import com.audiops.shared.resources.model_bangla
import com.audiops.shared.resources.model_bangla_purpose
import com.audiops.shared.resources.model_dpdfnet
import com.audiops.shared.resources.model_dpdfnet_purpose
import com.audiops.shared.resources.model_enhance
import com.audiops.shared.resources.model_enhance_purpose
import com.audiops.shared.resources.model_omnilingual
import com.audiops.shared.resources.model_omnilingual_purpose
import com.audiops.shared.resources.model_parakeet
import com.audiops.shared.resources.model_parakeet_purpose
import com.audiops.shared.resources.model_piper
import com.audiops.shared.resources.model_piper_purpose
import com.audiops.shared.resources.model_pocket_tts
import com.audiops.shared.resources.model_pocket_tts_purpose
import com.audiops.shared.resources.model_scnet
import com.audiops.shared.resources.model_scnet_purpose
import com.audiops.shared.resources.model_supertonic
import com.audiops.shared.resources.model_supertonic_purpose
import com.audiops.shared.resources.model_whisper
import com.audiops.shared.resources.model_whisper_purpose
import com.audiops.shared.resources.preset_lecture
import com.audiops.shared.resources.preset_lecture_description
import com.audiops.shared.resources.preset_noisy
import com.audiops.shared.resources.preset_noisy_description
import com.audiops.shared.resources.preset_podcast
import com.audiops.shared.resources.preset_podcast_description
import com.audiops.shared.resources.preset_voice_message
import com.audiops.shared.resources.preset_voice_message_description
import com.audiops.shared.resources.record_permission_needed
import com.audiops.shared.resources.stage_model_download_percent
import com.audiops.shared.resources.stage_percent
import com.audiops.shared.resources.stage_preparing
import com.audiops.shared.resources.stage_queued
import com.audiops.shared.resources.stage_section
import com.audiops.shared.resources.status_cancelled
import com.audiops.shared.resources.status_done
import com.audiops.shared.resources.status_failed
import com.audiops.shared.resources.status_queued
import com.audiops.shared.resources.status_running
import com.audiops.shared.resources.stem_bass
import com.audiops.shared.resources.stem_drums
import com.audiops.shared.resources.stem_instrumental
import com.audiops.shared.resources.stem_other
import com.audiops.shared.resources.stem_vocals
import com.audiops.shared.resources.summary_fade
import com.audiops.shared.resources.summary_gain
import com.audiops.shared.resources.summary_loudness
import com.audiops.shared.resources.summary_peak
import com.audiops.shared.resources.summary_rate
import com.audiops.shared.resources.summary_silence
import com.audiops.shared.resources.summary_speed
import com.audiops.shared.resources.summary_strength
import com.audiops.shared.resources.time_days
import com.audiops.shared.resources.time_hours
import com.audiops.shared.resources.time_just_now
import com.audiops.shared.resources.time_minutes
import com.audiops.shared.resources.tool_analyze_description
import com.audiops.shared.resources.tool_analyze_title
import com.audiops.shared.resources.tool_convert_description
import com.audiops.shared.resources.tool_convert_title
import com.audiops.shared.resources.tool_denoise_description
import com.audiops.shared.resources.tool_denoise_title
import com.audiops.shared.resources.tool_enhance_description
import com.audiops.shared.resources.tool_enhance_title
import com.audiops.shared.resources.tool_equalizer_description
import com.audiops.shared.resources.tool_equalizer_title
import com.audiops.shared.resources.tool_fade_description
import com.audiops.shared.resources.tool_fade_title
import com.audiops.shared.resources.tool_presets_description
import com.audiops.shared.resources.tool_presets_title
import com.audiops.shared.resources.tool_separate_description
import com.audiops.shared.resources.tool_separate_title
import com.audiops.shared.resources.tool_silence_description
import com.audiops.shared.resources.tool_silence_title
import com.audiops.shared.resources.tool_speak_description
import com.audiops.shared.resources.tool_speak_title
import com.audiops.shared.resources.tool_speed_description
import com.audiops.shared.resources.tool_speed_title
import com.audiops.shared.resources.tool_transcribe_description
import com.audiops.shared.resources.tool_transcribe_title
import com.audiops.shared.resources.tool_trim_description
import com.audiops.shared.resources.tool_trim_title
import com.audiops.shared.resources.tool_volume_description
import com.audiops.shared.resources.tool_volume_title
import com.audiops.shared.resources.transcription_model_auto
import com.audiops.shared.resources.transcription_model_auto_summary
import com.audiops.shared.resources.transcription_model_bangla_summary
import com.audiops.shared.resources.transcription_model_omnilingual_summary
import com.audiops.shared.resources.transcription_model_parakeet_summary
import com.audiops.shared.resources.transcription_model_whisper_summary
import com.audiops.shared.resources.tts_language_ar
import com.audiops.shared.resources.tts_language_bg
import com.audiops.shared.resources.tts_language_cs
import com.audiops.shared.resources.tts_language_da
import com.audiops.shared.resources.tts_language_de_de
import com.audiops.shared.resources.tts_language_el
import com.audiops.shared.resources.tts_language_en_gb
import com.audiops.shared.resources.tts_language_en_us
import com.audiops.shared.resources.tts_language_es_es
import com.audiops.shared.resources.tts_language_et
import com.audiops.shared.resources.tts_language_fa
import com.audiops.shared.resources.tts_language_fi
import com.audiops.shared.resources.tts_language_fr_fr
import com.audiops.shared.resources.tts_language_hi
import com.audiops.shared.resources.tts_language_hr
import com.audiops.shared.resources.tts_language_hu
import com.audiops.shared.resources.tts_language_id
import com.audiops.shared.resources.tts_language_it_it
import com.audiops.shared.resources.tts_language_ja
import com.audiops.shared.resources.tts_language_kk
import com.audiops.shared.resources.tts_language_ko
import com.audiops.shared.resources.tts_language_lt
import com.audiops.shared.resources.tts_language_lv
import com.audiops.shared.resources.tts_language_ne
import com.audiops.shared.resources.tts_language_nl_nl
import com.audiops.shared.resources.tts_language_no
import com.audiops.shared.resources.tts_language_other
import com.audiops.shared.resources.tts_language_pl
import com.audiops.shared.resources.tts_language_pt_br
import com.audiops.shared.resources.tts_language_pt_pt
import com.audiops.shared.resources.tts_language_ro
import com.audiops.shared.resources.tts_language_ru
import com.audiops.shared.resources.tts_language_sk
import com.audiops.shared.resources.tts_language_sl
import com.audiops.shared.resources.tts_language_sv
import com.audiops.shared.resources.tts_language_tr
import com.audiops.shared.resources.tts_language_uk
import com.audiops.shared.resources.tts_language_vi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

val SpeechLanguage.label: StringResource
    get() = when (this) {
        SpeechLanguage.AUTO -> Res.string.language_auto
        SpeechLanguage.BANGLA -> Res.string.language_bangla
        SpeechLanguage.ENGLISH -> Res.string.language_english
        SpeechLanguage.HINDI -> Res.string.language_hindi
        SpeechLanguage.ARABIC -> Res.string.language_arabic
        SpeechLanguage.CHINESE -> Res.string.language_chinese
        SpeechLanguage.FRENCH -> Res.string.language_french
        SpeechLanguage.GERMAN -> Res.string.language_german
        SpeechLanguage.INDONESIAN -> Res.string.language_indonesian
        SpeechLanguage.ITALIAN -> Res.string.language_italian
        SpeechLanguage.JAPANESE -> Res.string.language_japanese
        SpeechLanguage.KOREAN -> Res.string.language_korean
        SpeechLanguage.PERSIAN -> Res.string.language_persian
        SpeechLanguage.PORTUGUESE -> Res.string.language_portuguese
        SpeechLanguage.RUSSIAN -> Res.string.language_russian
        SpeechLanguage.SPANISH -> Res.string.language_spanish
        SpeechLanguage.TURKISH -> Res.string.language_turkish
        SpeechLanguage.URDU -> Res.string.language_urdu
        SpeechLanguage.VIETNAMESE -> Res.string.language_vietnamese
        SpeechLanguage.BULGARIAN -> Res.string.language_bulgarian
        SpeechLanguage.CZECH -> Res.string.language_czech
        SpeechLanguage.DANISH -> Res.string.language_danish
        SpeechLanguage.GREEK -> Res.string.language_greek
        SpeechLanguage.ESTONIAN -> Res.string.language_estonian
        SpeechLanguage.FINNISH -> Res.string.language_finnish
        SpeechLanguage.CROATIAN -> Res.string.language_croatian
        SpeechLanguage.HUNGARIAN -> Res.string.language_hungarian
        SpeechLanguage.LITHUANIAN -> Res.string.language_lithuanian
        SpeechLanguage.LATVIAN -> Res.string.language_latvian
        SpeechLanguage.MALTESE -> Res.string.language_maltese
        SpeechLanguage.DUTCH -> Res.string.language_dutch
        SpeechLanguage.POLISH -> Res.string.language_polish
        SpeechLanguage.ROMANIAN -> Res.string.language_romanian
        SpeechLanguage.SLOVAK -> Res.string.language_slovak
        SpeechLanguage.SLOVENIAN -> Res.string.language_slovenian
        SpeechLanguage.SWEDISH -> Res.string.language_swedish
        SpeechLanguage.UKRAINIAN -> Res.string.language_ukrainian
        SpeechLanguage.THAI -> Res.string.language_thai
        SpeechLanguage.SWAHILI -> Res.string.language_swahili
        SpeechLanguage.TAMIL -> Res.string.language_tamil
        SpeechLanguage.TELUGU -> Res.string.language_telugu
        SpeechLanguage.MARATHI -> Res.string.language_marathi
        SpeechLanguage.GUJARATI -> Res.string.language_gujarati
        SpeechLanguage.PUNJABI -> Res.string.language_punjabi
        SpeechLanguage.KANNADA -> Res.string.language_kannada
        SpeechLanguage.MALAYALAM -> Res.string.language_malayalam
        SpeechLanguage.NEPALI -> Res.string.language_nepali
        SpeechLanguage.HEBREW -> Res.string.language_hebrew
        SpeechLanguage.MALAY -> Res.string.language_malay
        SpeechLanguage.FILIPINO -> Res.string.language_filipino
        SpeechLanguage.BURMESE -> Res.string.language_burmese
        SpeechLanguage.KHMER -> Res.string.language_khmer
        SpeechLanguage.AMHARIC -> Res.string.language_amharic
        SpeechLanguage.AFRIKAANS -> Res.string.language_afrikaans
        SpeechLanguage.CATALAN -> Res.string.language_catalan
        SpeechLanguage.NORWEGIAN -> Res.string.language_norwegian
        SpeechLanguage.SERBIAN -> Res.string.language_serbian
        SpeechLanguage.AZERBAIJANI -> Res.string.language_azerbaijani
        SpeechLanguage.KAZAKH -> Res.string.language_kazakh
        SpeechLanguage.UZBEK -> Res.string.language_uzbek
    }

val AiModel.title: StringResource
    get() = when (this) {
        AiModel.ENHANCE -> Res.string.model_enhance
        AiModel.NOISE -> Res.string.model_dpdfnet
        AiModel.PARAKEET -> Res.string.model_parakeet
        AiModel.OMNILINGUAL -> Res.string.model_omnilingual
        AiModel.WHISPER -> Res.string.model_whisper
        AiModel.BANGLA -> Res.string.model_bangla
        AiModel.POCKET_TTS -> Res.string.model_pocket_tts
        AiModel.PIPER -> Res.string.model_piper
        AiModel.SUPERTONIC -> Res.string.model_supertonic
        AiModel.SCNET -> Res.string.model_scnet
    }

/** What the model is used for. */
val AiModel.purpose: StringResource
    get() = when (this) {
        AiModel.ENHANCE -> Res.string.model_enhance_purpose
        AiModel.NOISE -> Res.string.model_dpdfnet_purpose
        AiModel.PARAKEET -> Res.string.model_parakeet_purpose
        AiModel.OMNILINGUAL -> Res.string.model_omnilingual_purpose
        AiModel.WHISPER -> Res.string.model_whisper_purpose
        AiModel.BANGLA -> Res.string.model_bangla_purpose
        AiModel.POCKET_TTS -> Res.string.model_pocket_tts_purpose
        AiModel.PIPER -> Res.string.model_piper_purpose
        AiModel.SUPERTONIC -> Res.string.model_supertonic_purpose
        AiModel.SCNET -> Res.string.model_scnet_purpose
    }

val TranscriptionModel.label: StringResource
    get() = when (this) {
        TranscriptionModel.AUTO -> Res.string.transcription_model_auto
        TranscriptionModel.PARAKEET -> Res.string.model_parakeet
        TranscriptionModel.OMNILINGUAL -> Res.string.model_omnilingual
        TranscriptionModel.WHISPER -> Res.string.model_whisper
        TranscriptionModel.BANGLA -> Res.string.model_bangla
    }

val TranscriptionModel.summary: StringResource
    get() = when (this) {
        TranscriptionModel.AUTO -> Res.string.transcription_model_auto_summary
        TranscriptionModel.PARAKEET -> Res.string.transcription_model_parakeet_summary
        TranscriptionModel.OMNILINGUAL -> Res.string.transcription_model_omnilingual_summary
        TranscriptionModel.WHISPER -> Res.string.transcription_model_whisper_summary
        TranscriptionModel.BANGLA -> Res.string.transcription_model_bangla_summary
    }

/** Whether the transcription screen asks for a language with this model. */
val TranscriptionModel.usesLanguage: Boolean
    get() = this == TranscriptionModel.WHISPER

/** Display name of a text-to-speech language (a BCP 47 tag from [ttsLanguages]). */
fun ttsLanguageLabel(tag: String): StringResource = when (tag) {
    "en-US" -> Res.string.tts_language_en_us
    "fr-FR" -> Res.string.tts_language_fr_fr
    "de-DE" -> Res.string.tts_language_de_de
    "es-ES" -> Res.string.tts_language_es_es
    "it-IT" -> Res.string.tts_language_it_it
    "pt-BR" -> Res.string.tts_language_pt_br
    "nl-NL" -> Res.string.tts_language_nl_nl
    "en-GB" -> Res.string.tts_language_en_gb
    "pt-PT" -> Res.string.tts_language_pt_pt
    "cs" -> Res.string.tts_language_cs
    "da" -> Res.string.tts_language_da
    "el" -> Res.string.tts_language_el
    "fa" -> Res.string.tts_language_fa
    "fi" -> Res.string.tts_language_fi
    "hu" -> Res.string.tts_language_hu
    "kk" -> Res.string.tts_language_kk
    "lv" -> Res.string.tts_language_lv
    "ne" -> Res.string.tts_language_ne
    "no" -> Res.string.tts_language_no
    "pl" -> Res.string.tts_language_pl
    "ro" -> Res.string.tts_language_ro
    "ru" -> Res.string.tts_language_ru
    "sk" -> Res.string.tts_language_sk
    "sl" -> Res.string.tts_language_sl
    "sv" -> Res.string.tts_language_sv
    "tr" -> Res.string.tts_language_tr
    "uk" -> Res.string.tts_language_uk
    "vi" -> Res.string.tts_language_vi
    "ko" -> Res.string.tts_language_ko
    "ja" -> Res.string.tts_language_ja
    "ar" -> Res.string.tts_language_ar
    "bg" -> Res.string.tts_language_bg
    "et" -> Res.string.tts_language_et
    "hr" -> Res.string.tts_language_hr
    "hi" -> Res.string.tts_language_hi
    "id" -> Res.string.tts_language_id
    "lt" -> Res.string.tts_language_lt
    else -> Res.string.tts_language_other
}

val SaveFormat.hint: StringResource
    get() = when (this) {
        SaveFormat.WAV -> Res.string.format_wav_hint
        SaveFormat.M4A -> Res.string.format_m4a_hint
        SaveFormat.OPUS -> Res.string.format_opus_hint
    }

val SaveFormat.label: StringResource
    get() = when (this) {
        SaveFormat.WAV -> Res.string.format_wav
        SaveFormat.M4A -> Res.string.format_m4a
        SaveFormat.OPUS -> Res.string.format_opus
    }

val EnhanceMode.title: StringResource
    get() = when (this) {
        EnhanceMode.FAST -> Res.string.enhance_mode_fast
        EnhanceMode.STUDIO -> Res.string.enhance_mode_studio
    }

val EnhanceMode.description: StringResource
    get() = when (this) {
        EnhanceMode.FAST -> Res.string.enhance_mode_fast_description
        EnhanceMode.STUDIO -> Res.string.enhance_mode_studio_description
    }

val SeparatedStem.label: StringResource
    get() = when (this) {
        SeparatedStem.VOCALS -> Res.string.stem_vocals
        SeparatedStem.INSTRUMENTAL -> Res.string.stem_instrumental
        SeparatedStem.DRUMS -> Res.string.stem_drums
        SeparatedStem.BASS -> Res.string.stem_bass
        SeparatedStem.OTHER -> Res.string.stem_other
    }

val Preset.title: StringResource
    get() = when (this) {
        Preset.PODCAST -> Res.string.preset_podcast
        Preset.VOICE_MESSAGE -> Res.string.preset_voice_message
        Preset.LECTURE -> Res.string.preset_lecture
        Preset.NOISY -> Res.string.preset_noisy
    }

val Preset.description: StringResource
    get() = when (this) {
        Preset.PODCAST -> Res.string.preset_podcast_description
        Preset.VOICE_MESSAGE -> Res.string.preset_voice_message_description
        Preset.LECTURE -> Res.string.preset_lecture_description
        Preset.NOISY -> Res.string.preset_noisy_description
    }

val Tool.title: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.tool_enhance_title
        Tool.TRANSCRIBE -> Res.string.tool_transcribe_title
        Tool.SPEAK -> Res.string.tool_speak_title
        Tool.SEPARATE -> Res.string.tool_separate_title
        Tool.PRESETS -> Res.string.tool_presets_title
        Tool.DENOISE -> Res.string.tool_denoise_title
        Tool.SILENCE -> Res.string.tool_silence_title
        Tool.TRIM -> Res.string.tool_trim_title
        Tool.FADE -> Res.string.tool_fade_title
        Tool.VOLUME -> Res.string.tool_volume_title
        Tool.SPEED -> Res.string.tool_speed_title
        Tool.EQUALIZER -> Res.string.tool_equalizer_title
        Tool.CONVERT -> Res.string.tool_convert_title
        Tool.ANALYZE -> Res.string.tool_analyze_title
    }

val Tool.description: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.tool_enhance_description
        Tool.TRANSCRIBE -> Res.string.tool_transcribe_description
        Tool.SPEAK -> Res.string.tool_speak_description
        Tool.SEPARATE -> Res.string.tool_separate_description
        Tool.PRESETS -> Res.string.tool_presets_description
        Tool.DENOISE -> Res.string.tool_denoise_description
        Tool.SILENCE -> Res.string.tool_silence_description
        Tool.TRIM -> Res.string.tool_trim_description
        Tool.FADE -> Res.string.tool_fade_description
        Tool.VOLUME -> Res.string.tool_volume_description
        Tool.SPEED -> Res.string.tool_speed_description
        Tool.EQUALIZER -> Res.string.tool_equalizer_description
        Tool.CONVERT -> Res.string.tool_convert_description
        Tool.ANALYZE -> Res.string.tool_analyze_description
    }

/** Primary button label on the tool screen. */
val Tool.action: StringResource
    get() = when (this) {
        Tool.ENHANCE -> Res.string.action_enhance
        Tool.TRANSCRIBE -> Res.string.action_transcribe
        Tool.SPEAK -> Res.string.action_speak
        Tool.SEPARATE -> Res.string.action_separate
        Tool.ANALYZE -> Res.string.action_analyze
        else -> Res.string.action_process
    }

val ToolCategory.title: StringResource
    get() = when (this) {
        ToolCategory.AI -> Res.string.category_ai
        ToolCategory.CLEAN_UP -> Res.string.category_clean_up
        ToolCategory.EDIT -> Res.string.category_edit
        ToolCategory.EFFECTS -> Res.string.category_effects
        ToolCategory.UTILITIES -> Res.string.category_utilities
    }

val EqPreset.title: StringResource
    get() = when (this) {
        EqPreset.VOICE_CLARITY -> Res.string.eq_voice_clarity
        EqPreset.BASS_BOOST -> Res.string.eq_bass_boost
        EqPreset.TREBLE_BOOST -> Res.string.eq_treble_boost
        EqPreset.WARM -> Res.string.eq_warm
        EqPreset.REMOVE_RUMBLE -> Res.string.eq_remove_rumble
        EqPreset.TELEPHONE -> Res.string.eq_telephone
    }

val LoudnessTarget.title: StringResource
    get() = when (this) {
        LoudnessTarget.STREAMING -> Res.string.loudness_streaming
        LoudnessTarget.PODCAST -> Res.string.loudness_podcast
        LoudnessTarget.BROADCAST -> Res.string.loudness_broadcast
    }

val JobStatus.title: StringResource
    get() = when (this) {
        JobStatus.QUEUED -> Res.string.status_queued
        JobStatus.RUNNING -> Res.string.status_running
        JobStatus.DONE -> Res.string.status_done
        JobStatus.FAILED -> Res.string.status_failed
        JobStatus.CANCELLED -> Res.string.status_cancelled
    }

/** Status line for a running or queued job, such as "Processing… 42%". */
@Composable
fun Job.progressText(): String {
    val (resource, args) = progressResource()
    return stringResource(resource, *args)
}

/** [progressText] outside composition, for example in a notification. */
suspend fun Job.progressLabel(): String {
    val (resource, args) = progressResource()
    return getString(resource, *args)
}

private fun Job.progressResource(): Pair<StringResource, Array<Any>> {
    val p = progress ?: return status.title to emptyArray()
    return when (p.stage) {
        Stage.QUEUED -> Res.string.stage_queued to emptyArray()

        Stage.PREPARING -> Res.string.stage_preparing to emptyArray()

        Stage.DOWNLOADING_MODEL ->
            Res.string.stage_model_download_percent to arrayOf(p.index, p.total, (p.subFraction * 100).toInt())

        Stage.PROCESSING -> if (p.total == 100) {
            Res.string.stage_percent to arrayOf(p.index)
        } else {
            Res.string.stage_section to arrayOf(p.index, p.total)
        }
    }
}

@Composable
fun Job.failureText(): String = when (failure) {
    FailureKind.OUT_OF_MEMORY -> stringResource(Res.string.error_out_of_memory)

    FailureKind.NO_SPEECH -> stringResource(Res.string.error_no_speech)

    FailureKind.UNSUPPORTED -> failureDetail ?: stringResource(Res.string.error_unsupported_platform)

    FailureKind.INTERRUPTED -> stringResource(Res.string.error_interrupted)

    FailureKind.PROCESSING, null -> stringResource(
        Res.string.error_processing_failed,
        failureDetail ?: stringResource(Res.string.error_unknown),
    )
}

@Composable
fun InputProblem.text(): String = when (this) {
    InputProblem.Unsupported -> stringResource(Res.string.error_unsupported_format)
    is InputProblem.Unreadable -> stringResource(Res.string.error_unreadable, detail ?: stringResource(Res.string.error_unknown))
}

/** Short description of what a job did, such as "Peak −1 dB" or "1.25× · +2 st". */
@Composable
fun JobSpec.summary(): String? = when (this) {
    JobSpec.Analyze, JobSpec.Separate -> null

    is JobSpec.Enhance -> stringResource(mode.title)

    // The model that ran, plus the language only for Whisper (the one model that is given a language).
    is JobSpec.Transcribe -> listOfNotNull(
        stringResource(model.label),
        if (model.usesLanguage && language != SpeechLanguage.AUTO) stringResource(language.label) else null,
    ).joinToString(" · ")

    is JobSpec.Speak -> listOfNotNull(ttsVoice(voice)?.name, ttsVoice(voice)?.let { stringResource(ttsLanguageLabel(it.language)) })
        .joinToString(" · ")
        .ifEmpty { null }

    is JobSpec.Process -> operations.mapNotNull { it.summary() }.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun AudioOperation.summary(): String? = when (this) {
    is AudioOperation.Trim -> "${formatDuration(startMs)} – ${formatDuration(endMs)}"
    is AudioOperation.Gain -> stringResource(Res.string.summary_gain, signed(db))
    is AudioOperation.NormalizePeak -> stringResource(Res.string.summary_peak, signed(peakDb))
    is AudioOperation.NormalizeLoudness -> stringResource(Res.string.summary_loudness, target.lufs.roundToInt())
    is AudioOperation.Fade -> stringResource(Res.string.summary_fade, seconds(inMs), seconds(outMs))
    is AudioOperation.Equalizer -> stringResource(preset.title)
    is AudioOperation.SpeedPitch -> stringResource(Res.string.summary_speed, decimal(speed, 2), signed(semitones))
    is AudioOperation.Denoise -> stringResource(Res.string.summary_strength, (strength * 100).roundToInt())
    is AudioOperation.RemoveSilence -> stringResource(Res.string.summary_silence, signed(thresholdDb))
    is AudioOperation.Resample -> stringResource(Res.string.summary_rate, decimal(sampleRate / 1000f, 2))
}

suspend fun UiMessage.text(): String = when (this) {
    is UiMessage.JobFinished -> getString(Res.string.message_job_finished, getString(tool.title))
    is UiMessage.JobFailed -> getString(Res.string.message_job_failed, getString(tool.title))
    UiMessage.Saved -> getString(Res.string.message_saved)
    is UiMessage.SaveFailed -> getString(Res.string.error_save, detail ?: getString(Res.string.error_unknown))
    UiMessage.Cancelled -> getString(Res.string.message_cancelled)
    UiMessage.ModelsDeleted -> getString(Res.string.message_models_deleted)
    UiMessage.HistoryCleared -> getString(Res.string.message_history_cleared)
    UiMessage.Copied -> getString(Res.string.message_copied)
    is UiMessage.InputReady -> getString(Res.string.message_input_ready, name)
    is UiMessage.RecordFailed -> getString(Res.string.error_record, detail ?: getString(Res.string.record_permission_needed))
    is UiMessage.ImportFailed -> getString(Res.string.error_import, detail ?: getString(Res.string.error_unknown))
}

/** "Just now", "5 min ago", "3 h ago", "2 d ago". */
@Composable
fun relativeTime(epochMs: Long): String {
    val minutes = (currentTimeMillis() - epochMs).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> stringResource(Res.string.time_just_now)
        minutes < 60 -> stringResource(Res.string.time_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(Res.string.time_hours, (minutes / 60).toInt())
        else -> stringResource(Res.string.time_days, (minutes / (60 * 24)).toInt())
    }
}

/** m:ss.d for editing, or h:mm:ss for long durations. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}.$tenths"
}

private fun two(v: Long) = if (v < 10) "0$v" else "$v"

/** "+2.5", "−3", "0" with a true minus sign. */
fun signed(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10f
    val text = if (rounded == rounded.toInt().toFloat()) abs(rounded.toInt()).toString() else decimal(abs(rounded), 1)
    return when {
        rounded > 0 -> "+$text"
        rounded < 0 -> "−$text"
        else -> "0"
    }
}

/** Fixed decimals without platform formatting APIs. */
fun decimal(value: Float, digits: Int): String {
    var factor = 1
    repeat(digits) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToInt()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(digits, '0').trimEnd('0')
    val sign = if (value < 0 && scaled != 0) "\u2212" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

private fun seconds(ms: Long): String = decimal(ms / 1000f, 1)
