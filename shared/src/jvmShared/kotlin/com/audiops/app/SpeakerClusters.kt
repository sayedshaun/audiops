package com.audiops.app

import kotlin.math.sqrt

/**
 * Tells speakers apart from voice embeddings (one per utterance).
 *
 * While a meeting runs, [assign] compares each new embedding with the running centre of every
 * speaker heard so far and either joins the closest one or starts a new speaker. Early on the
 * centres rest on very little speech, so when the meeting ends [recluster] groups all embeddings
 * again at once (average-linkage clustering), which corrects early mistakes.
 *
 * Similarities are cosine similarities in -1..1; higher means more alike.
 */
class SpeakerClusters(private val threshold: Float = DEFAULT_THRESHOLD) {
    private val sums = mutableListOf<FloatArray>()
    private var last = -1

    /** Speakers found so far. */
    val count: Int get() = sums.size

    /**
     * The speaker (0-based) of an utterance with [embedding]. Utterances too short to embed (null)
     * go to the previous speaker: in conversation, short replies are less likely than a speaker
     * continuing.
     */
    fun assign(embedding: FloatArray?): Int {
        if (embedding == null) return last.coerceAtLeast(0)
        val unit = normalized(embedding) ?: return last.coerceAtLeast(0)
        var best = -1
        var bestSimilarity = -2f
        sums.forEachIndexed { index, sum ->
            val similarity = cosine(unit, sum)
            if (similarity > bestSimilarity) {
                best = index
                bestSimilarity = similarity
            }
        }
        last = if (best >= 0 && bestSimilarity >= threshold) {
            val sum = sums[best]
            for (i in sum.indices) sum[i] += unit[i]
            best
        } else {
            sums += unit.copyOf()
            sums.lastIndex
        }
        return last
    }

    companion object {
        /**
         * Measured with the CAM++ model on short clips (1.4 to 5 s): the same voice scored 0.58 and up,
         * different voices at most 0.45. 0.5 (also sherpa-onnx's default for diarization) sits between.
         */
        const val DEFAULT_THRESHOLD = 0.5f

        /** Final pass: average similarity between two groups of utterances. */
        const val RECLUSTER_THRESHOLD = 0.5f

        /** Above this many embeddings the final pass is skipped and the live labels are kept (time and memory). */
        const val MAX_RECLUSTER = 3000

        /**
         * Groups [embeddings] (one per utterance, in order) into speakers by average-linkage
         * clustering and returns one 0-based speaker per utterance, numbered in order of first
         * appearance. Utterances without an embedding take the speaker of the one before them
         * (or after, at the start).
         */
        fun recluster(embeddings: List<FloatArray?>, threshold: Float = RECLUSTER_THRESHOLD): IntArray {
            val present = embeddings.indices.filter { embeddings[it]?.let(::normalized) != null }
            val labels = IntArray(embeddings.size) { -1 }
            if (present.isEmpty()) return IntArray(embeddings.size)
            val units = present.map { normalized(embeddings[it]!!)!! }
            val groups = averageLinkage(units, threshold)
            present.forEachIndexed { k, index -> labels[index] = groups[k] }
            fillGaps(labels)
            return renumber(labels)
        }

        /** Average-linkage agglomerative clustering with cached nearest neighbours; returns a group per item. */
        private fun averageLinkage(units: List<FloatArray>, threshold: Float): IntArray {
            val n = units.size
            val similarity = Array(n) { i -> FloatArray(n) { j -> if (i == j) Float.NEGATIVE_INFINITY else dot(units[i], units[j]) } }
            val size = IntArray(n) { 1 }
            val active = BooleanArray(n) { true }
            val parent = IntArray(n) { it }
            val nearest = IntArray(n) { i -> argmax(similarity[i], active, i) }
            while (true) {
                var a = -1
                var bestValue = Float.NEGATIVE_INFINITY
                for (i in 0 until n) {
                    val j = nearest[i]
                    if (active[i] && j >= 0 && similarity[i][j] > bestValue) {
                        a = i
                        bestValue = similarity[i][j]
                    }
                }
                if (a < 0 || bestValue < threshold) break
                val b = nearest[a]
                // Merge b into a (Lance-Williams update for average linkage).
                for (k in 0 until n) {
                    if (!active[k] || k == a || k == b) continue
                    val merged = (size[a] * similarity[a][k] + size[b] * similarity[b][k]) / (size[a] + size[b])
                    similarity[a][k] = merged
                    similarity[k][a] = merged
                }
                size[a] += size[b]
                active[b] = false
                parent[b] = a
                for (k in 0 until n) {
                    if (!active[k]) continue
                    if (k == a || nearest[k] == a || nearest[k] == b) {
                        nearest[k] = argmax(similarity[k], active, k)
                    } else if (similarity[k][a] > similarity[k][nearest[k]]) {
                        nearest[k] = a
                    }
                }
            }
            fun root(i: Int): Int {
                var r = i
                while (parent[r] != r) r = parent[r]
                return r
            }
            return IntArray(n) { root(it) }
        }

        private fun argmax(row: FloatArray, active: BooleanArray, self: Int): Int {
            var best = -1
            var value = Float.NEGATIVE_INFINITY
            for (j in row.indices) {
                if (j != self && active[j] && row[j] > value) {
                    best = j
                    value = row[j]
                }
            }
            return best
        }

        private fun fillGaps(labels: IntArray) {
            var previous = -1
            for (i in labels.indices) {
                if (labels[i] >= 0) {
                    previous = labels[i]
                } else if (previous >= 0) {
                    labels[i] = previous
                }
            }
            val first = labels.firstOrNull { it >= 0 } ?: 0
            for (i in labels.indices) if (labels[i] < 0) labels[i] = first
        }

        /** Renumbers labels 0, 1, 2… in order of first appearance. */
        fun renumber(labels: IntArray): IntArray {
            val order = mutableMapOf<Int, Int>()
            return IntArray(labels.size) { i -> order.getOrPut(labels[i]) { order.size } }
        }

        internal fun normalized(vector: FloatArray): FloatArray? {
            val norm = sqrt(dot(vector, vector))
            if (norm == 0f || norm.isNaN()) return null
            return FloatArray(vector.size) { vector[it] / norm }
        }

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }

        private fun cosine(unit: FloatArray, sum: FloatArray): Float {
            val norm = sqrt(dot(sum, sum))
            return if (norm == 0f) -1f else dot(unit, sum) / norm
        }
    }
}
