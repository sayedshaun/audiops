package com.audiops.app

/** Text-to-speech engines. PocketTTS runs through MediaPipe; Piper and Supertonic through sherpa-onnx (Piper with espeak-ng). */
enum class TtsEngine { POCKET, PIPER, SUPERTONIC }

/**
 * One downloadable voice. [language] is a BCP 47 tag; [files] maps each file in the Hugging Face
 * [repo] at [revision] to its SHA-256. [nonCommercial] voices may not be used commercially and are
 * labelled in the app. [downloadMb] is the size of the voice's own files.
 */
data class TtsVoice(
    val id: String,
    val language: String,
    val name: String,
    val engine: TtsEngine,
    val license: String,
    val downloadMb: Int,
    val repo: String,
    val revision: String,
    val files: Map<String, String>,
    val nonCommercial: Boolean = false,
) {
    /** Language without the region, for example `en` for `en-GB`. */
    val baseLanguage: String get() = language.substringBefore('-')
}

private const val POCKET_REPO = "litert-community/pocket-tts"
private const val POCKET_REVISION = "03f949ec418ccd4d153008f7ef9c58c754cc0616"

/** PocketTTS INT8 bundles, one per language: file to (size in MB, SHA-256). */
private val POCKET_FILES = mapOf(
    "pocket_tts_de_int8.litertlm" to (126 to "2754f69cc739f26a60489e7033975e31ada679ac191e3d07d5696148dacabb50"),
    "pocket_tts_en_int8.litertlm" to (163 to "465e0083cd61188772aed76160b07af0dcf59057497d7659f43f3f2b13a174a1"),
    "pocket_tts_es_int8.litertlm" to (126 to "5100595c1baf614539738dfef90105511fe59c47308ef27ad886c790e247a267"),
    "pocket_tts_fr_int8.litertlm" to (127 to "6885614825d59fa5b48eb32d5f79ee11b4ea2b0a603d2508354ba88005b200eb"),
    "pocket_tts_it_int8.litertlm" to (125 to "28fe0a618ca7fda55e8f138db955d0f74470ed10be8f4910d081bbbbc789974c"),
    "pocket_tts_nl_int8.litertlm" to (125 to "e38af0d6b726c550548b0126c5563245d9ac563c6669b66af924a2b545b51387"),
    "pocket_tts_pt_int8.litertlm" to (126 to "4d71e3097906d8373e5dcf86297ee366d9d8c00022fbd9ea59d6bc8531173e1f"),
)

private fun pocket(id: String, language: String, name: String, file: String): TtsVoice {
    val (megabytes, digest) = POCKET_FILES.getValue(file)
    return TtsVoice(
        "pocket-$id", language, name, TtsEngine.POCKET, "CC BY 4.0", megabytes, POCKET_REPO, POCKET_REVISION,
        mapOf(
            file to digest,
        ),
    )
}

private const val SUPERTONIC_REPO = "SayedShaun/supertonic-3-sherpa-onnx-int8"
private const val SUPERTONIC_REVISION = "25a63a9f1ba817eff0a1960616dc2e6834b6516b"

/** Supertonic 3 int8 files (sherpa-onnx layout, one set for every language and voice): file to SHA-256. */
private val SUPERTONIC_FILES = mapOf(
    "duration_predictor.int8.onnx" to "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db",
    "text_encoder.int8.onnx" to "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff",
    "vector_estimator.int8.onnx" to "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd",
    "vocoder.int8.onnx" to "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152",
    "tts.json" to "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09",
    "unicode_indexer.bin" to "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30",
    "voice.bin" to "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8",
    // A stub the model never reads: Hugging Face counts a download when it is fetched, so the model page shows usage.
    "config.json" to "74d23e4e44bd8c6223eaca9cfd59ef38cd896b58923859bc0faa092b36405d4f",
)

/** Supertonic's own language codes, each with the language tag used in this catalogue. */
private val SUPERTONIC_LANGUAGES = listOf(
    "en" to "en-US", "ko" to "ko", "ja" to "ja", "ar" to "ar", "bg" to "bg", "cs" to "cs", "da" to "da", "de" to "de-DE",
    "el" to "el", "es" to "es-ES", "et" to "et", "fi" to "fi", "fr" to "fr-FR", "hi" to "hi", "hr" to "hr", "hu" to "hu",
    "id" to "id", "it" to "it-IT", "lt" to "lt", "lv" to "lv", "nl" to "nl-NL", "pl" to "pl", "pt" to "pt-BR", "ro" to "ro",
    "ru" to "ru", "sk" to "sk", "sl" to "sl", "sv" to "sv", "tr" to "tr", "uk" to "uk", "vi" to "vi",
)

/** The preset voice styles offered per language: id suffix to display name (a female and a male voice). */
private val SUPERTONIC_STYLES = listOf("f1" to "Female", "m1" to "Male")

/** Supertonic's ten preset styles in the order of its voice table (speaker ids 0 to 9). */
val SupertonicStyles: List<String> = listOf("f1", "f2", "f3", "f4", "f5", "m1", "m2", "m3", "m4", "m5")

/** Speaker id of a Supertonic voice (the suffix of its id, such as `f1`). */
fun supertonicSpeaker(voice: TtsVoice): Int =
    SupertonicStyles.indexOf(voice.id.substringAfterLast('-')).also { check(it >= 0) { voice.id } }

private val supertonicVoices: List<TtsVoice> = SUPERTONIC_LANGUAGES.flatMap { (code, language) ->
    SUPERTONIC_STYLES.map { (style, name) ->
        TtsVoice(
            "supertonic-$code-$style", language, name, TtsEngine.SUPERTONIC, "OpenRAIL-M", 145, SUPERTONIC_REPO, SUPERTONIC_REVISION,
            SUPERTONIC_FILES,
        )
    }
}

private val otherVoices: List<TtsVoice> = listOf(
    pocket("alba", "en-US", "Alba", "pocket_tts_en_int8.litertlm"),
    pocket("marius", "en-US", "Marius", "pocket_tts_en_int8.litertlm"),
    pocket("javert", "en-US", "Javert", "pocket_tts_en_int8.litertlm"),
    pocket("jean", "en-US", "Jean", "pocket_tts_en_int8.litertlm"),
    pocket("fantine", "en-US", "Fantine", "pocket_tts_en_int8.litertlm"),
    pocket("cosette", "en-US", "Cosette", "pocket_tts_en_int8.litertlm"),
    pocket("eponine", "en-US", "Eponine", "pocket_tts_en_int8.litertlm"),
    pocket("azelma", "en-US", "Azelma", "pocket_tts_en_int8.litertlm"),
    pocket("estelle", "fr-FR", "Estelle", "pocket_tts_fr_int8.litertlm"),
    pocket("juergen", "de-DE", "Jürgen", "pocket_tts_de_int8.litertlm"),
    pocket("lola", "es-ES", "Lola", "pocket_tts_es_int8.litertlm"),
    pocket("giovanni", "it-IT", "Giovanni", "pocket_tts_it_int8.litertlm"),
    pocket("rafael", "pt-BR", "Rafael", "pocket_tts_pt_int8.litertlm"),
    pocket("daan", "nl-NL", "Daan", "pocket_tts_nl_int8.litertlm"),
    TtsVoice(
        "piper-en-gb-cori", "en-GB", "Cori", TtsEngine.PIPER, "Public domain", 64,
        "csukuangfj/vits-piper-en_GB-cori-medium", "e304c95c578725ba9cab0cff451c4e5d9aaf889e",
        mapOf(
            "en_GB-cori-medium.onnx" to "8b0d3cdd77f2878e0aa2048103eabb4d01b334783f99629f042bcf703aeba487",
            "tokens.txt" to "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d",
        ),
    ),
    TtsVoice(
        "piper-pt-pt-tugao", "pt-PT", "Tugão", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-pt_PT-tugao-medium", "612042d3d5a504d6c65080b1b2b2f7981b831954",
        mapOf(
            "pt_PT-tugao-medium.onnx" to "0d922da6f6fd87f981bb05fa8f698a1af6fc5c9366c212cdeb36a0f04c3c056d",
            "tokens.txt" to "620e1aecf1a68fea3ba5850d137b0138fa2037c9b372dad13b95a2a215d0849a",
        ),
    ),
    TtsVoice(
        "piper-cs-jirka", "cs", "Jirka", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-cs_CZ-jirka-medium", "14061e2f0c1b212be50bc32445e77b490c041ef4",
        mapOf(
            "cs_CZ-jirka-medium.onnx" to "d41a2b0a0d488c5796b8eed3403155ee0556b7642268111f655334df97e918b8",
            "tokens.txt" to "620e1aecf1a68fea3ba5850d137b0138fa2037c9b372dad13b95a2a215d0849a",
        ),
    ),
    TtsVoice(
        "piper-da-talesyntese", "da", "Talesyntese", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-da_DK-talesyntese-medium", "f2c1614417beec702b8cb3d28b10ae0bf7a1af1c",
        mapOf(
            "da_DK-talesyntese-medium.onnx" to "516ef8c4b8104610598bef078114437092d482c36f00e72b0b9b358d8db0916e",
            "tokens.txt" to "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
        ),
    ),
    TtsVoice(
        "piper-el-rapunzelina", "el", "Rapunzelina", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-el_GR-rapunzelina-low", "b16a9198ecb66e5361f3d0c01a181afa0ea28fff",
        mapOf(
            "el_GR-rapunzelina-low.onnx" to "5aaa1b1ea2c94e90eed2f0f7f12989df22fa2e1c3b2d781b15e93c210943c20a",
            "tokens.txt" to "42d1a69ed2b91a51928a711aa228ed9f3dc021c6d359a3e9c4f37eb1d20f80bd",
        ),
    ),
    TtsVoice(
        "piper-fa-amir", "fa", "Amir", TtsEngine.PIPER, "CC0", 64,
        "csukuangfj/vits-piper-fa_IR-amir-medium", "e852208889c7cb924b8b43eace4e8bff847b13a1",
        mapOf(
            "fa_IR-amir-medium.onnx" to "5a832803df099857d92cf7ec1e340c7ef6910433f089d9aca9ff1ebf9b7fa6c5",
            "tokens.txt" to "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d",
        ),
    ),
    TtsVoice(
        "piper-fi-harri", "fi", "Harri", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-fi_FI-harri-medium", "6c1264e0997582ccdd6c54de1e22698d460c40ea",
        mapOf(
            "fi_FI-harri-medium.onnx" to "0f57368660befcb6a1c2d25547cff9fcd1fdc2edb3241676f0117c09ecb8e9b6",
            "tokens.txt" to "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
        ),
    ),
    TtsVoice(
        "piper-hu-anna", "hu", "Anna", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-hu_HU-anna-medium", "dec1dc1120c96916b49c71e3ac199e83beee341a",
        mapOf(
            "hu_HU-anna-medium.onnx" to "33ec97c272e650e528fb164939bc782517f50c0f0fd3c3ef9235d5b98300c67c",
            "tokens.txt" to "620e1aecf1a68fea3ba5850d137b0138fa2037c9b372dad13b95a2a215d0849a",
        ),
    ),
    TtsVoice(
        "piper-kk-issai", "kk", "ISSAI", TtsEngine.PIPER, "CC BY 4.0", 128,
        "csukuangfj/vits-piper-kk_KZ-issai-high", "22e6357072c742143b1fa7704391386a3756e8a4",
        mapOf(
            "kk_KZ-issai-high.onnx" to "2a2e21c96f21dae5e160c55f0ab969e9fb71f355b33d81ffd2d6c3927b1c9aef",
            "tokens.txt" to "42d1a69ed2b91a51928a711aa228ed9f3dc021c6d359a3e9c4f37eb1d20f80bd",
        ),
    ),
    TtsVoice(
        "piper-lv-aivars", "lv", "Aivars", TtsEngine.PIPER, "CC0", 64,
        "csukuangfj/vits-piper-lv_LV-aivars-medium", "5dabfb0558f9b4046598c9d50599e6004a9544c6",
        mapOf(
            "lv_LV-aivars-medium.onnx" to "a85a008f63feb962cd6631415ad2aa98bb1861cdf5971fc13b8f45d5a3ff366d",
            "tokens.txt" to "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d",
        ),
    ),
    TtsVoice(
        "piper-ne-chitwan", "ne", "Chitwan", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-ne_NP-chitwan-medium", "2b6bad58d4f7df8f794f3514cca582c08e7223ff",
        mapOf(
            "ne_NP-chitwan-medium.onnx" to "4c0220bc13a665add2bef76bd6d40fedcc2273731c933c4cdf3bf3c1440ae1e9",
            "tokens.txt" to "e8a50ae0c75612d18cfcf3f90800dca79f961e3ed97f9574f3535194469187fd",
        ),
    ),
    TtsVoice(
        "piper-no-talesyntese", "no", "Talesyntese", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-no_NO-talesyntese-medium", "c9003dc23feabf3579bf390238955ac2e88c179e",
        mapOf(
            "no_NO-talesyntese-medium.onnx" to "8027a2c33f6dc30e137a0f7f15022c07ef2d0e56300a5dfc29e38408970a0ddf",
            "tokens.txt" to "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
        ),
    ),
    TtsVoice(
        "piper-pl-darkman", "pl", "Darkman", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-pl_PL-darkman-medium", "2a1dfbd2449c2a19bf794f3977bb5088a83c3cce",
        mapOf(
            "pl_PL-darkman-medium.onnx" to "fe8df9888ce02d985472355b3cad5fa2267c8bffa7c09438029bb16d07060adc",
            "tokens.txt" to "2619c1a9de1bcf928162f40c583caf39368cfd6b2340c7bcad51dc634411ec36",
        ),
    ),
    TtsVoice(
        "piper-ro-mihai", "ro", "Mihai", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-ro_RO-mihai-medium", "b7955c4f2151846c863e197689ec2bcb74b1de8d",
        mapOf(
            "ro_RO-mihai-medium.onnx" to "bbeedb8a14a28a089c5bc47e689dc6053c6190abbd00d591033de0f4ed6eae8b",
            "tokens.txt" to "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
        ),
    ),
    TtsVoice(
        "piper-ru-denis", "ru", "Denis", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-ru_RU-denis-medium", "1e2f78fc13100abc60bf56264dc2ee254b4c5f8e",
        mapOf(
            "ru_RU-denis-medium.onnx" to "f0129d8cbd0fef7df16a101d0cd302b25a8fd8bbda7b11af885b1e9f3e974dcf",
            "tokens.txt" to "2619c1a9de1bcf928162f40c583caf39368cfd6b2340c7bcad51dc634411ec36",
        ),
    ),
    TtsVoice(
        "piper-sk-lili", "sk", "Lili", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-sk_SK-lili-medium", "e24926b2960bdd9f471d40384adb33558b14e3b5",
        mapOf(
            "sk_SK-lili-medium.onnx" to "4184b6990b4cd832ed7889abbeaff217d96cb1eb168c29231168b0d8c4848993",
            "tokens.txt" to "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d",
        ),
    ),
    TtsVoice(
        "piper-sl-artur", "sl", "Artur", TtsEngine.PIPER, "CC BY 4.0", 63,
        "csukuangfj/vits-piper-sl_SI-artur-medium", "6d8fe8757edf4d3481543621d645ab87fd09538b",
        mapOf(
            "sl_SI-artur-medium.onnx" to "7e0c703d8cd2577e669e1dcb95d1cf47b06a8abfdd08c50890508d69f6528c70",
            "tokens.txt" to "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d",
        ),
    ),
    TtsVoice(
        "piper-sv-nst", "sv", "NST", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-sv_SE-nst-medium", "04df2eea5794c14b235bb25f35f69bf4f7d5592d",
        mapOf(
            "sv_SE-nst-medium.onnx" to "61dd6f6a45801fb51117a74cff17957428d7156d747b163fa1fbb9c1df26278d",
            "tokens.txt" to "42d1a69ed2b91a51928a711aa228ed9f3dc021c6d359a3e9c4f37eb1d20f80bd",
        ),
    ),
    TtsVoice(
        "piper-tr-fahrettin", "tr", "Fahrettin", TtsEngine.PIPER, "CC0", 63,
        "csukuangfj/vits-piper-tr_TR-fahrettin-medium", "ec73b891c2f4f9b14b1f14dab62b90ea0eb983ad",
        mapOf(
            "tr_TR-fahrettin-medium.onnx" to "5809ca3765979ec7bb419ef67a86e0348bcf605cc70a9818417c62d473059c0c",
            "tokens.txt" to "620e1aecf1a68fea3ba5850d137b0138fa2037c9b372dad13b95a2a215d0849a",
        ),
    ),
    TtsVoice(
        "piper-vi-vais1000", "vi", "Vais", TtsEngine.PIPER, "CC BY 4.0", 63,
        "csukuangfj/vits-piper-vi_VN-vais1000-medium", "3d92898cf942406c23330da466a831d3d923c2a2",
        mapOf(
            "vi_VN-vais1000-medium.onnx" to "7264e130a2f2e62c2dd586e3790ba054b669f9f72a2bdb2c0d60207136611576",
            "tokens.txt" to "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
        ),
    ),
)

/**
 * Every voice offered, grouped by language in display order. In each language Supertonic 3
 * (OpenRAIL-M, 31 languages) comes first and is the default; PocketTTS (Kyutai, CC BY 4.0) and Piper
 * voices (CC0, public domain or CC BY 4.0) follow where they exist. Voices with unclear licences are left out.
 */
val ttsVoices: List<TtsVoice> = run {
    val languages = (otherVoices.map { it.language } + SUPERTONIC_LANGUAGES.map { it.second }).distinct()
    languages.flatMap { language -> supertonicVoices.filter { it.language == language } + otherVoices.filter { it.language == language } }
}

/** Languages that have at least one voice, in catalogue order. */
val ttsLanguages: List<String> = ttsVoices.map { it.language }.distinct()

fun ttsVoice(id: String): TtsVoice? = ttsVoices.firstOrNull { it.id == id }

/** The voice used when nothing is chosen yet. */
val defaultTtsVoice: TtsVoice = ttsVoices.first()
