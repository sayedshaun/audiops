package com.audiops.app

/** One subtitle: [text] shown from [startMs] to [endMs] on the recording's timeline. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** A transcript: the plain [text] and, when timing is known, its subtitle [cues]. */
data class Transcript(val text: String, val cues: List<Cue> = emptyList()) {
    val isBlank: Boolean get() = text.isBlank()
}

enum class SubtitleFormat { SRT, VTT }

/** Writes cues as SubRip (SRT) or WebVTT. */
object Subtitles {
    fun srt(cues: List<Cue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(timestamp(cue.startMs, ',')).append(" --> ").append(timestamp(cue.endMs, ',')).append('\n')
            append(cue.text).append("\n\n")
        }
    }

    /** Converts SRT text to WebVTT: a header line, and dots instead of commas in the times. */
    fun toVtt(srt: String): String {
        val body = srt.replace("\r\n", "\n").lines().joinToString("\n") { line ->
            if (" --> " in line) line.replace(',', '.') else line
        }
        return "WEBVTT\n\n" + body.trimStart('\n')
    }

    private fun timestamp(ms: Long, separator: Char): String {
        val total = ms.coerceAtLeast(0)
        val hours = total / 3_600_000
        val minutes = total / 60_000 % 60
        val seconds = total / 1_000 % 60
        val millis = total % 1_000
        return "${two(hours)}:${two(minutes)}:${two(seconds)}$separator${millis.toString().padStart(3, '0')}"
    }

    private fun two(value: Long) = value.toString().padStart(2, '0')
}

/**
 * Turns the text and token times a recognizer reports for one section of a recording into
 * subtitle cues. Models that report no token times (Whisper) get times spread over the section
 * in proportion to each cue's length, which is approximate but stays in order.
 */
object Cues {
    private const val MAX_CHARS = 80
    private const val LINE_CHARS = 42
    private const val MIN_CUE_MS = 400L
    private const val LAST_TOKEN_MS = 400L
    private val sentenceEnds = setOf('.', '!', '?', '।', '؟', '。', '！', '？')

    fun forSection(offsetMs: Long, durationMs: Long, text: String, tokens: List<String>, timestamps: List<Float>): List<Cue> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val chunks = chunk(clean)
        val total = chunks.sumOf { countNonSpace(it) }.coerceAtLeast(1)
        val tokenTimes = if (tokens.isNotEmpty() && tokens.size == timestamps.size) tokenBoundaries(tokens) else null
        val timed = tokenTimes != null && tokenTimes.last() == countNonSpace(clean)
        val cues = ArrayList<Cue>(chunks.size)
        var before = 0
        for (chunk in chunks) {
            val size = countNonSpace(chunk)
            val (start, end) = if (timed) {
                tokenSpan(tokenTimes!!, timestamps, before, before + size, durationMs)
            } else {
                durationMs * before / total to durationMs * (before + size) / total
            }
            before += size
            val previousEnd = cues.lastOrNull()?.endMs ?: offsetMs
            val from = (offsetMs + start).coerceIn(previousEnd, offsetMs + durationMs)
            val to = (offsetMs + end).coerceIn(from + MIN_CUE_MS, maxOf(offsetMs + durationMs, from + MIN_CUE_MS))
            cues += Cue(from, to, wrap(chunk))
        }
        return cues
    }

    /**
     * [cues] in order with no overlap: each starts no earlier than the one before ends (utterances
     * widened with the audio around them can overlap a little).
     */
    fun sequential(cues: List<Cue>): List<Cue> {
        var previousEnd = 0L
        return cues.map { cue ->
            val start = maxOf(cue.startMs, previousEnd)
            cue.copy(startMs = start, endMs = maxOf(cue.endMs, start + MIN_CUE_MS)).also { previousEnd = it.endMs }
        }
    }

    /** Splits at sentence ends, and at spaces when a sentence is longer than [MAX_CHARS]. */
    internal fun chunk(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            current.append(ch)
            if (ch in sentenceEnds) {
                sentences += current.toString().trim()
                current.clear()
            }
        }
        if (current.isNotBlank()) sentences += current.toString().trim()
        return sentences.filter { it.isNotEmpty() }.flatMap { sentence ->
            val parts = mutableListOf<String>()
            var rest = sentence
            while (rest.length > MAX_CHARS) {
                val cut = rest.lastIndexOf(' ', MAX_CHARS).takeIf { it > 0 } ?: MAX_CHARS
                parts += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) parts += rest
            parts
        }
    }

    /** Breaks [text] into at most two lines of about [LINE_CHARS] characters, at a space. */
    internal fun wrap(text: String): String {
        if (text.length <= LINE_CHARS) return text
        val middle = text.length / 2
        val cut = listOf(text.lastIndexOf(' ', middle), text.indexOf(' ', middle)).filter { it > 0 }
            .minByOrNull { kotlin.math.abs(it - middle) } ?: return text
        return text.substring(0, cut).trim() + "\n" + text.substring(cut).trim()
    }

    private fun countNonSpace(text: String) = text.count { !it.isWhitespace() }

    /** Cumulative non-space character count at the end of each token. */
    private fun tokenBoundaries(tokens: List<String>): List<Int> {
        var count = 0
        return tokens.map { token ->
            count += countNonSpace(token.replace('▁', ' '))
            count
        }
    }

    /** Start and end (ms within the section) of the characters [from, to), by token times. */
    private fun tokenSpan(ends: List<Int>, times: List<Float>, from: Int, to: Int, durationMs: Long): Pair<Long, Long> {
        fun indexOf(position: Int) = ends.indexOfFirst { it > position }.takeIf { it >= 0 } ?: ends.lastIndex
        val first = indexOf(from)
        val last = indexOf((to - 1).coerceAtLeast(from))
        val start = (times[first] * 1000).toLong()
        val end = if (last + 1 < times.size) (times[last + 1] * 1000).toLong() else (times[last] * 1000).toLong() + LAST_TOKEN_MS
        return start to minOf(end, durationMs).coerceAtLeast(start)
    }
}
