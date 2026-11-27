package com.audiops.app

/** Writes a meeting transcript as readable text and as subtitles. */
object MeetingDocument {
    /**
     * Paragraphs headed by the speaker and the time, one per turn: consecutive lines of the same
     * speaker are joined.
     */
    fun text(lines: List<MeetingLine>, speakerName: (Int) -> String): String = buildString {
        var current = -1
        for (line in lines) {
            if (line.speaker != current) {
                if (isNotEmpty()) append("\n\n")
                append(speakerName(line.speaker + 1)).append(" (").append(clock(line.startMs)).append(")\n")
                append(line.text)
                current = line.speaker
            } else {
                append(' ').append(line.text)
            }
        }
    }

    /** One subtitle per utterance, prefixed with its speaker. */
    fun cues(lines: List<MeetingLine>, speakerName: (Int) -> String): List<Cue> =
        Cues.sequential(lines.map { Cue(it.startMs, it.endMs, "${speakerName(it.speaker + 1)}: ${it.text}") })

    /** m:ss, or h:mm:ss from an hour on. */
    fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val h = seconds / 3600
        val m = seconds / 60 % 60
        val s = seconds % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }
}
