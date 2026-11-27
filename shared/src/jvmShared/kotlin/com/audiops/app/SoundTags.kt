package com.audiops.app

import com.audiops.app.dsp.FilterChain
import com.audiops.app.dsp.ProcessorChain
import com.audiops.app.dsp.Resampler
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns the scores of an AudioSet sound tagger (class name to probability, 0..1) into a
 * [SoundProfile], and picks the parts of a recording the tagger listens to.
 *
 * Thresholds come from the Zipformer tagger on test clips: speech scores 0.9 and up, music and
 * singing 0.5 and up; added white noise, hum or a tone shows up as 0.15 to 0.5 across the noise classes.
 */
object SoundTags {
    /** Seconds per window the tagger hears. */
    const val WINDOW_SECONDS = 10

    /** Windows taken from a long recording (start, middle and end). */
    const val MAX_WINDOWS = 3

    private const val SPEECH = 0.4f
    private const val MUSIC = 0.4f
    private const val SINGING = 0.3f
    private const val MUSIC_OVER_SPEECH = 0.6f
    private const val NOISE = 0.15f
    private const val TAG = 0.1f
    private const val MAX_TAGS = 4

    private val speech = setOf(
        "Speech",
        "Male speech, man speaking",
        "Female speech, woman speaking",
        "Child speech, kid speaking",
        "Conversation",
        "Narration, monologue",
    )
    private val singing = setOf("Singing", "Choir", "Vocal music", "A capella", "Rapping", "Song", "Male singing", "Female singing")
    private val noise = setOf(
        "Noise", "White noise", "Pink noise", "Static", "Hum", "Mains hum", "Buzz", "Hiss", "Sine wave", "Environmental noise",
        "Wind", "Wind noise (microphone)", "Rustling leaves", "Rain", "Raindrop", "Rain on surface", "Traffic noise, roadway noise",
        "Vehicle", "Engine", "Idling", "Air conditioning", "Mechanical fan", "Crowd", "Hubbub, speech noise, speech babble",
        "Chatter", "Rumble", "Waterfall", "Stream", "Echo", "Reverberation", "Clatter", "Inside, public space", "Outside, urban or manmade",
    )

    /** Classes too general to be worth showing. */
    private val hidden = setOf("Inside, small room", "Inside, large room or hall", "Silence", "Sound effect", "Outside, rural or natural")

    fun profile(scores: Map<String, Float>): SoundProfile {
        fun max(names: Set<String>) = names.maxOf { scores[it] ?: 0f }
        val music = scores["Music"] ?: 0f
        val talking = max(speech) >= SPEECH
        val noisy = noise.sumOf { (scores[it] ?: 0f).toDouble() } >= NOISE
        val kind = when {
            max(singing) >= SINGING -> SoundKind.MUSIC

            // Speech over quiet background music (a podcast intro) is still speech.
            talking && music < MUSIC_OVER_SPEECH -> if (noisy) SoundKind.NOISY_SPEECH else SoundKind.SPEECH

            music >= MUSIC -> SoundKind.MUSIC

            talking -> if (noisy) SoundKind.NOISY_SPEECH else SoundKind.SPEECH

            else -> SoundKind.OTHER
        }
        val tags = scores.entries
            .filter { it.value >= TAG && it.key !in hidden }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(MAX_TAGS)
        return SoundProfile(kind, tags)
    }

    /** Averages the scores of several windows (a class missing from a window counts as 0). */
    fun average(windows: List<Map<String, Float>>): Map<String, Float> {
        if (windows.isEmpty()) return emptyMap()
        val sums = mutableMapOf<String, Float>()
        windows.forEach { window -> window.forEach { (name, score) -> sums[name] = (sums[name] ?: 0f) + score } }
        return sums.mapValues { it.value / windows.size }
    }

    /**
     * Up to [MAX_WINDOWS] windows of [WINDOW_SECONDS] from decoded [audio] (44.1 kHz mono PCM16), spread
     * over the recording, at 16 kHz. A short recording gives one window with all of it.
     */
    fun windows(audio: File): List<FloatArray> {
        val total = audio.length() / 2
        if (total == 0L) return emptyList()
        val window = PIPELINE_RATE.toLong() * WINDOW_SECONDS
        val starts = when {
            total <= window -> listOf(0L)
            total <= window * MAX_WINDOWS -> listOf(0L, total - window)
            else -> List(MAX_WINDOWS) { (total - window) * it / (MAX_WINDOWS - 1) }
        }
        RandomAccessFile(audio, "r").use { file ->
            return starts.map { start ->
                throwIfCancelled()
                val count = minOf(window, total - start).toInt()
                val bytes = ByteArray(count * 2)
                file.seek(start * 2)
                file.readFully(bytes)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val samples = FloatArray(count) { buffer.short / 32768f }
                val down = ProcessorChain(
                    listOf(FilterChain.antiAlias(PIPELINE_RATE, MEETING_RATE), Resampler(PIPELINE_RATE.toDouble() / MEETING_RATE)),
                )
                down.process(samples) + down.flush()
            }
        }
    }
}
