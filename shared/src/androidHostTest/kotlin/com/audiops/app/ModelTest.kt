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
            JobSpec.Enhance(),
            JobSpec.Enhance(EnhanceMode.FAST),
            JobSpec.Separate,
            JobSpec.Analyze,
            JobSpec.Transcribe(SpeechLanguage.BANGLA),
            JobSpec.Transcribe(SpeechLanguage.AUTO, TranscriptionModel.PARAKEET),
            JobSpec.Speak("Hello there.", "pocket-alba", 1.25f),
            JobSpec.Process(operations),
        )
        for (spec in specs) {
            val original = job(spec)
            assertEquals(original, json.decodeFromString<Job>(json.encodeToString(original)))
        }
    }

    @Test fun theLanguageListIsSortedAndHasNoAuto() {
        assertTrue(SpeechLanguage.AUTO !in transcriptionLanguages)
        assertEquals(transcriptionLanguages.sortedBy { it.name }, transcriptionLanguages)
        assertTrue(
            listOf(SpeechLanguage.BANGLA, SpeechLanguage.URDU, SpeechLanguage.THAI, SpeechLanguage.POLISH).all {
                it in
                    transcriptionLanguages
            },
        )
        assertEquals("bn", SpeechLanguage.BANGLA.code)
    }

    @Test fun autoIsNotASelectableModel() {
        assertTrue(TranscriptionModel.AUTO !in selectableTranscriptionModels)
        assertEquals(4, selectableTranscriptionModels.size)
    }

    @Test fun everyLanguageSurvivesAJsonRoundTripWithAUniqueCode() {
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

    @Test fun everyTranscriptionModelSurvivesAJsonRoundTrip() {
        TranscriptionModel.entries.forEach { model ->
            val spec: JobSpec = JobSpec.Transcribe(SpeechLanguage.AUTO, model)
            assertEquals(spec, json.decodeFromString<JobSpec>(json.encodeToString(spec)))
        }
    }

    @Test fun transcriptionsSavedBeforeTheModelChoiceLoadAsAuto() {
        val old = json.decodeFromString<JobSpec>("""{"type":"transcribe","language":"BANGLA"}""")
        assertEquals(JobSpec.Transcribe(SpeechLanguage.BANGLA, TranscriptionModel.AUTO), old)
    }

    @Test fun enhanceSavedBeforeModesLoadsAsStudio() {
        // Older history has no mode; it was always the Resemble model.
        assertEquals(JobSpec.Enhance(EnhanceMode.STUDIO), json.decodeFromString<JobSpec>("""{"type":"enhance"}"""))
        val fast = JobSpec.Enhance(EnhanceMode.FAST)
        assertEquals(fast, json.decodeFromString<JobSpec>(json.encodeToString<JobSpec>(fast)))
    }

    @Test fun separationJobsKeepTheirStemsAndOldJobsHaveNone() {
        val stems = SeparatedStem.entries.map { "/out/${it.fileSuffix}.wav" }
        val separated = job(JobSpec.Separate).copy(outputPath = stems.first(), stemPaths = stems)
        assertEquals(separated, json.decodeFromString<Job>(json.encodeToString(separated)))
        assertEquals(stems, separated.files)
        // History written before stems existed has no field and still loads.
        val old = json.decodeFromString<Job>(json.encodeToString(job(JobSpec.Analyze)).replace(",\"stemPaths\":null", ""))
        assertNull(old.stemPaths)
        assertEquals(emptyList<String>(), old.files)
        val transcript = job(JobSpec.Analyze).copy(outputPath = "/out/a.txt", subtitlesPath = "/out/a.srt")
        assertEquals(listOf("/out/a.txt", "/out/a.srt"), transcript.files)
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

    @Test fun recentToolsAreNewestFirstWithoutRepeats() {
        val analyze = job(JobSpec.Analyze, id = "a", tool = Tool.ANALYZE).copy(createdAt = 1)
        val trim = analyze.copy(id = "b", tool = Tool.TRIM, createdAt = 2)
        val analyzeAgain = analyze.copy(id = "c", createdAt = 3)
        val fade = analyze.copy(id = "d", tool = Tool.FADE, createdAt = 4, status = JobStatus.RUNNING)
        val state = AudiopsState(jobs = listOf(fade, trim, analyze, analyzeAgain))
        assertEquals(listOf(Tool.FADE, Tool.ANALYZE, Tool.TRIM), state.recentTools(limit = 3))
        assertEquals(listOf(Tool.FADE, Tool.ANALYZE), state.recentTools(limit = 2))
        assertEquals(emptyList<Tool>(), AudiopsState().recentTools(limit = 2))
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
