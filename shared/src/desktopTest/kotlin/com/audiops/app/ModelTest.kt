package com.audiops.app

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun job(spec: JobSpec, id: String = "1", tool: Tool = Tool.VOLUME) = Job(
        id = id,
        tool = tool,
        inputId = "/music/in.wav",
        inputName = "in.wav",
        spec = spec,
        status = JobStatus.DONE,
        createdAt = 1_000,
    )

    @Test fun everyOperationSurvivesAJsonRoundTrip() {
        val operations = listOf(
            AudioOperation.Trim(100, 2_000),
            AudioOperation.Gain(-3.5f),
            AudioOperation.NormalizePeak(),
            AudioOperation.NormalizeLoudness(LoudnessTarget.PODCAST),
            AudioOperation.Fade(500, 1_500),
            AudioOperation.Equalizer(EqPreset.TELEPHONE),
            AudioOperation.SpeedPitch(1.25f, -2f),
            AudioOperation.Denoise(0.6f),
            AudioOperation.RemoveSilence(-40f, 800, 120),
            AudioOperation.Resample(16_000),
        )
        val specs = listOf(
            JobSpec.Enhance,
            JobSpec.Analyze,
            JobSpec.Transcribe(SpeechLanguage.BANGLA),
            JobSpec.Process(operations),
        )
        for (spec in specs) {
            val original = job(spec)
            assertEquals(original, json.decodeFromString<Job>(json.encodeToString(original)))
        }
    }

    @Test fun everyLanguageSurvivesAJsonRoundTripWithAUniqueWhisperCode() {
        SpeechLanguage.entries.forEach { language ->
            val spec: JobSpec = JobSpec.Transcribe(language)
            assertEquals(spec, json.decodeFromString<JobSpec>(json.encodeToString(spec)))
        }
        val codes = SpeechLanguage.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.filter { it.isNotEmpty() }.all { it.length == 2 && it.all(Char::isLowerCase) })
        assertEquals("", SpeechLanguage.AUTO.code)
        assertEquals("bn", SpeechLanguage.BANGLA.code)
    }

    @Test fun storedTypeNamesStayStable() {
        // History written by older versions must keep loading: these names are a file format.
        val encoded = json.encodeToString<JobSpec>(JobSpec.Process(listOf(AudioOperation.NormalizePeak(-1f))))
        assertTrue(encoded, encoded.contains("\"type\":\"process\""))
        assertTrue(encoded, encoded.contains("\"type\":\"normalize_peak\""))
    }

    @Test fun progressIsNotPersisted() {
        val running = job(JobSpec.Analyze).copy(status = JobStatus.RUNNING, progress = JobProgress(Stage.PROCESSING, 1, 2))
        assertNull(json.decodeFromString<Job>(json.encodeToString(running)).progress)
    }

    @Test fun unknownFieldsAreIgnored() {
        val withExtra = json.encodeToString(job(JobSpec.Analyze)).dropLast(1) + ",\"addedLater\":42}"
        assertEquals(job(JobSpec.Analyze), json.decodeFromString<Job>(withExtra))
    }

    @Test fun progressFractionCoversEveryItem() {
        assertNull(JobProgress(Stage.PREPARING).fraction)
        assertEquals(0f, JobProgress(Stage.PROCESSING, 1, 4).fraction!!, 1e-6f)
        assertEquals(0.375f, JobProgress(Stage.PROCESSING, 2, 4, 0.5f).fraction!!, 1e-6f)
        assertEquals(1f, JobProgress(Stage.PROCESSING, 5, 4, 1f).fraction!!, 1e-6f)
    }

    @Test fun onlyQueuedAndRunningJobsAreActive() {
        assertEquals(setOf(JobStatus.QUEUED, JobStatus.RUNNING), JobStatus.entries.filter { it.active }.toSet())
        assertFalse(JobStatus.CANCELLED.active)
    }

    @Test fun latestJobMatchesToolAndCurrentInput() {
        val older = job(JobSpec.Analyze, id = "a", tool = Tool.ANALYZE).copy(createdAt = 1)
        val newer = older.copy(id = "b", createdAt = 2)
        val otherInput = older.copy(id = "c", createdAt = 3, inputId = "/other.wav")
        val state = AudiopsState(input = AudioInput("/music/in.wav", "in.wav"), jobs = listOf(older, otherInput, newer))
        assertEquals("b", state.latestJob(Tool.ANALYZE)?.id)
        assertNull(state.latestJob(Tool.TRIM))
    }

    @Test fun waveformEqualityComparesContent() {
        assertEquals(WaveformData(floatArrayOf(0.1f, 0.5f), 10), WaveformData(floatArrayOf(0.1f, 0.5f), 10))
        assertFalse(WaveformData(floatArrayOf(0.1f), 10) == WaveformData(floatArrayOf(0.2f), 10))
    }
}
