package com.audiops.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Voices the user has named, kept only on the device in [file], so later meetings and transcripts
 * can name those speakers by themselves. Each voice is the mean direction of the speaker's voice
 * embeddings; naming the same person again refines it.
 *
 * Every instance on the same file shares one lock, so the engine and a live meeting can both use it.
 */
class VoiceBook(private val file: File, private val threshold: Float = DEFAULT_THRESHOLD) {
    @Serializable
    private data class Entry(val name: String, val voice: List<Float>, val count: Int)

    /** Names of the remembered voices, A to Z. */
    fun names(): List<String> = synchronized(LOCK) { load().map { it.name }.sortedBy { it.lowercase() } }

    /** Remembers [voice] (any length; it is normalized) as [name], or refines the voice already kept for it. */
    fun remember(name: String, voice: FloatArray) {
        val unit = SpeakerClusters.normalized(voice) ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        synchronized(LOCK) {
            val entries = load().toMutableList()
            val index = entries.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
            val old = entries.getOrNull(index)
            entries += if (old != null && old.voice.size == unit.size) {
                entries.removeAt(index)
                val merged = FloatArray(unit.size) { old.voice[it] * old.count + unit[it] }
                Entry(clean, SpeakerClusters.normalized(merged)!!.toList(), (old.count + 1).coerceAtMost(MAX_COUNT))
            } else {
                if (old != null) entries.removeAt(index)
                Entry(clean, unit.toList(), 1)
            }
            save(entries)
        }
    }

    fun forget(name: String) = synchronized(LOCK) {
        save(load().filterNot { it.name == name })
    }

    /**
     * A remembered name for each of [voices] (one per speaker), or null. A name is given to at most one
     * speaker: the closest pairs are matched first, and only when they are at least [threshold] alike.
     */
    fun match(voices: List<FloatArray?>): List<String?> {
        val entries = synchronized(LOCK) { load() }
        val result = arrayOfNulls<String>(voices.size)
        if (entries.isEmpty()) return result.toList()
        val pairs = mutableListOf<Triple<Int, Int, Float>>()
        voices.forEachIndexed { speaker, voice ->
            val unit = voice?.let(SpeakerClusters::normalized) ?: return@forEachIndexed
            entries.forEachIndexed { e, entry ->
                if (entry.voice.size != unit.size) return@forEachIndexed
                var dot = 0f
                for (i in unit.indices) dot += unit[i] * entry.voice[i]
                if (dot >= threshold) pairs += Triple(speaker, e, dot)
            }
        }
        val usedEntries = mutableSetOf<Int>()
        for ((speaker, e, _) in pairs.sortedByDescending { it.third }) {
            if (result[speaker] != null || e in usedEntries) continue
            result[speaker] = entries[e].name
            usedEntries += e
        }
        return result.toList()
    }

    private fun load(): List<Entry> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString(entriesSerializer, file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
    }

    private fun save(entries: List<Entry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.encodeToString(entriesSerializer, entries), Charsets.UTF_8)
        check(temporary.renameTo(file) || run { file.delete() && temporary.renameTo(file) }) { "Could not save the voices." }
    }

    companion object {
        /**
         * Speaker voices are averages over many utterances, so the same person scores well above the
         * 0.58 single utterances reach (see [SpeakerClusters]); 0.6 keeps wrong names rare.
         */
        const val DEFAULT_THRESHOLD = 0.6f

        /** Later namings still move the voice by at least 1/[MAX_COUNT]. */
        private const val MAX_COUNT = 20

        private val LOCK = Any()
        private val json = Json { ignoreUnknownKeys = true }
        private val entriesSerializer = ListSerializer(Entry.serializer())
    }
}
