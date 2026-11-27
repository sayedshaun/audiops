package com.audiops.app

/**
 * Sentence breaks for models that write no punctuation (the many-language and Bangla models).
 *
 * Each utterance a voice activity detector cuts at a pause becomes a sentence: it starts with a
 * capital letter (in scripts that have them) and ends with the language's full stop. An utterance
 * the detector cut only because it grew too long ([maxSpeechMs]) runs on into the next one. A long
 * pause ([PARAGRAPH_PAUSE_MS]) starts a new paragraph in the plain text.
 */
object SpeechSentences {
    /** A pause at least this long between two utterances starts a new paragraph. */
    const val PARAGRAPH_PAUSE_MS = 2_000L

    /** Utterances within this much of the detector's limit are taken to have been cut, not ended. */
    private const val CUT_MARGIN_MS = 300L

    private val sentenceEnds = setOf('.', '!', '?', '…', '।', '॥', '۔', '؟', '。', '！', '？', '።', '။', '។')
    private val clauseEnds = setOf(',', ';', ':', '،', '、', '，', '؛')

    /** Languages written without spaces between sentences. */
    private val unspaced = setOf("zh", "ja", "yue")

    /** The mark that ends a sentence in [language] (a language code), or "" where none is used. */
    fun fullStop(language: String): String = when (language) {
        "bn", "as", "hi", "mr", "ne", "sa", "pa" -> "।"
        "ur" -> "۔"
        "zh", "ja", "yue" -> "。"
        "am" -> "።"
        "my" -> "။"
        "km" -> "។"
        "th", "lo" -> ""
        else -> "."
    }

    /** [lines] with sentence marks and capitals added; times and speakers are unchanged. */
    fun punctuate(lines: List<MeetingLine>, language: String, maxSpeechMs: Long): List<MeetingLine> {
        var capitalize = true
        return lines.map { line ->
            val cut = line.endMs - line.startMs >= maxSpeechMs - CUT_MARGIN_MS
            val text = sentence(line.text, language, capitalize, close = !cut)
            capitalize = !cut || text.lastOrNull() in sentenceEnds
            line.copy(text = text)
        }
    }

    /** One utterance as a sentence: capitalized when [capitalize], ended with a full stop when [close]. */
    fun sentence(text: String, language: String, capitalize: Boolean, close: Boolean): String {
        var result = text.trim()
        if (result.isEmpty()) return result
        if (capitalize) result = capitalized(result, language)
        if (close && result.last() !in sentenceEnds) {
            if (result.last() in clauseEnds) result = result.dropLast(1).trimEnd()
            result += fullStop(language)
        }
        return result
    }

    /** The utterances as plain text: sentences in a paragraph, and a new paragraph after a long pause. */
    fun join(lines: List<MeetingLine>, language: String): String = buildString {
        val space = if (language in unspaced) "" else " "
        var previousEnd: Long? = null
        for (line in lines) {
            if (line.text.isEmpty()) continue
            val end = previousEnd
            if (end != null) append(if (line.startMs - end >= PARAGRAPH_PAUSE_MS) "\n\n" else space)
            append(line.text)
            previousEnd = line.endMs
        }
    }

    private fun capitalized(text: String, language: String): String {
        val first = text.first()
        if (!first.isLowerCase()) return text
        // Turkish and Azerbaijani have a dotted capital I.
        val upper = if (first == 'i' && (language == "tr" || language == "az")) "İ" else first.titlecase()
        return upper + text.substring(1)
    }
}
