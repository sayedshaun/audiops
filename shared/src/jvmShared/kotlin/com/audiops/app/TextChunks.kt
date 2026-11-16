package com.audiops.app

/**
 * Splits text for speech synthesis into pieces of at most [maxChars] characters, breaking after
 * sentence ends (also `।`, `؟`, `。` and line breaks) and, for very long sentences, at spaces.
 * Synthesizing piece by piece keeps memory flat, shows progress and lets a job be cancelled.
 */
object TextChunks {
    private val sentenceEnd = Regex("""(?<=[.!?;:।؟。！？])\s+|\n+""")

    fun split(text: String, maxChars: Int = 400): List<String> {
        require(maxChars > 0)
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) pieces += current.toString().trim()
            current.clear()
        }
        for (sentence in text.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sentence.length > maxChars) {
                flush()
                pieces += wrap(sentence, maxChars)
                continue
            }
            if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()
        return pieces
    }

    /** Breaks one long sentence at spaces (or anywhere, for text without spaces). */
    private fun wrap(sentence: String, maxChars: Int): List<String> {
        val parts = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            val cut = rest.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
