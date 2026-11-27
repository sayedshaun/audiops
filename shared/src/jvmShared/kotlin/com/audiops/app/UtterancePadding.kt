package com.audiops.app

/**
 * Widens each utterance a voice activity detector finds with the audio just before and after it.
 *
 * Silero VAD starts an utterance a few hundred milliseconds after speech begins (it needs some
 * speech to be sure). Parakeet copes with the clipped start, but the many-language model then
 * misreads the whole utterance (English came out in Arabic script in a test). [lead] samples before
 * and [tail] after fix that. Audio is fed in order with the utterances found in it; an utterance is
 * handed on once its tail has arrived (or at [flush]). Only the audio still needed is kept.
 */
class UtterancePadding(private val lead: Int = LEAD, private val tail: Int = TAIL) {
    private var history = FloatArray(0)
    private var historySize = 0
    private var historyStart = 0L
    private val pending = ArrayDeque<Utterance>()

    private val received: Long get() = historyStart + historySize

    /** Adds the next [samples] of audio and the utterances [found] so far; returns the padded utterances that are complete. */
    fun add(samples: FloatArray, found: List<Utterance>): List<Utterance> {
        append(samples)
        pending += found
        val ready = mutableListOf<Utterance>()
        while (pending.isNotEmpty() && end(pending.first()) + tail <= received) ready += padded(pending.removeFirst())
        trim()
        return ready
    }

    /** The audio has ended: returns every utterance left (with [found]), padded with what audio there is. */
    fun flush(found: List<Utterance> = emptyList()): List<Utterance> {
        pending += found
        val ready = pending.map(::padded)
        pending.clear()
        trim()
        return ready
    }

    private fun end(utterance: Utterance) = utterance.startSample + utterance.samples.size

    private fun padded(utterance: Utterance): Utterance {
        val from = maxOf(utterance.startSample - lead, historyStart)
        val to = minOf(end(utterance) + tail, received)
        // The detector's own samples, should the history not reach (it always does when fed in order).
        if (from > utterance.startSample || to < end(utterance)) return utterance
        return Utterance(from, history.copyOfRange((from - historyStart).toInt(), (to - historyStart).toInt()))
    }

    private fun append(samples: FloatArray) {
        if (historySize + samples.size > history.size) history = history.copyOf(maxOf(historySize + samples.size, history.size * 2))
        System.arraycopy(samples, 0, history, historySize, samples.size)
        historySize += samples.size
    }

    /** Drops audio no pending or future utterance can need. */
    private fun trim() {
        val oldestStart = pending.firstOrNull()?.startSample ?: (received - KEEP)
        val keepFrom = (minOf(oldestStart, received - KEEP) - lead).coerceAtLeast(historyStart)
        val drop = (keepFrom - historyStart).toInt()
        if (drop <= 0) return
        System.arraycopy(history, drop, history, 0, historySize - drop)
        historySize -= drop
        historyStart = keepFrom
    }

    companion object {
        /** 0.3 s of audio before each utterance. */
        const val LEAD = MEETING_RATE * 3 / 10

        /** 0.2 s after. */
        const val TAIL = MEETING_RATE / 5

        /**
         * Audio kept even with nothing pending: a detector reports an utterance only after it ended,
         * so its start can lie this far back (the 12 s utterance limit plus the pause that ends it).
         */
        private const val KEEP = MEETING_RATE * 15
    }
}
