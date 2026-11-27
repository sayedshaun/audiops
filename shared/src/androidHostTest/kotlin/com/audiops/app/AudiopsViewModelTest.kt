package com.audiops.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job as CoroutineJob

/** Engine double: records calls and lets each test choose what a job returns. */
private class FakeEngine(
    override val supportsEnhance: Boolean = true,
    override val supportsTranscribe: Boolean = true,
    override val supportsSpeak: Boolean = true,
    override val supportsSeparate: Boolean = true,
) : AudioEngine {
    val deleted: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val processed: MutableList<List<AudioOperation>> = Collections.synchronizedList(mutableListOf())
    val transcribeCalls: MutableList<Pair<String, TranscriptionModel>> = Collections.synchronizedList(mutableListOf())
    val speakerTranscripts: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val running = AtomicInteger()
    val maxConcurrent = AtomicInteger()

    /** Runs inside [process]; may block or throw. */
    @Volatile var onProcess: () -> Unit = {}

    @Volatile var transcript = "hello world"

    val enhanced = AtomicInteger()

    override fun enhance(source: String, listener: ProcessingListener): String {
        enhanced.incrementAndGet()
        return "/out/enhanced.wav"
    }

    @Volatile var subtitles: String? = null

    override fun subtitlesFor(transcriptPath: String): String? = subtitles

    @Volatile var speakersFile: String? = null

    override fun speakersFile(transcriptPath: String): String? = speakersFile

    val namedSpeakers: MutableList<Pair<List<String?>, Boolean>> = Collections.synchronizedList(mutableListOf())
    val voices: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun nameSpeakers(transcriptPath: String, names: List<String?>, remember: Boolean, speakerName: (Int) -> String) {
        namedSpeakers += names to remember
        if (remember) names.filterNotNull().forEach { voices += it }
        transcript = names.firstOrNull() ?: speakerName(1)
    }

    override fun rememberedVoices(): List<String> = voices.toList()

    override fun forgetVoice(name: String) {
        voices -= name
    }

    val separated = AtomicInteger()

    override fun separate(source: String, listener: ProcessingListener): List<String> {
        val run = separated.incrementAndGet()
        return SeparatedStem.entries.map { "/out/separated-$run-${it.fileSuffix}.wav" }
    }

    val spoken: MutableList<Triple<String, String, Float>> = Collections.synchronizedList(mutableListOf())

    override fun speak(text: String, voiceId: String, speed: Float, listener: ProcessingListener): String {
        spoken += Triple(text, voiceId, speed)
        return "/out/speech.wav"
    }

    override fun transcribe(
        source: String,
        language: String,
        model: TranscriptionModel,
        speakers: Boolean,
        speakerName: (Int) -> String,
        listener: ProcessingListener,
    ): String {
        transcribeCalls += language to model
        if (speakers) speakerTranscripts += speakerName(1)
        return "/out/transcript.txt"
    }

    override fun process(source: String, operations: List<AudioOperation>, listener: ProcessingListener): String {
        maxConcurrent.accumulateAndGet(running.incrementAndGet(), ::maxOf)
        try {
            listener.onSection(1, 2)
            onProcess()
            processed += operations
            return "/out/processed-${processed.size}.wav"
        } finally {
            running.decrementAndGet()
        }
    }

    override fun analyze(source: String, listener: ProcessingListener) = AudioReport(1_000, 44_100, -1f, -20f, -23f, 0, 0f, 0.1f)

    override fun waveform(source: String, buckets: Int) = WaveformData(FloatArray(buckets) { 0.5f }, 1_000)

    override fun readText(path: String) = transcript

    override fun copyFile(path: String, destination: String) = Unit

    val savedAudio: MutableList<Triple<String, SaveFormat, String>> = Collections.synchronizedList(mutableListOf())

    override val saveFormats: List<SaveFormat> = listOf(SaveFormat.WAV, SaveFormat.M4A)

    override fun saveAudio(path: String, format: SaveFormat, destination: String) {
        savedAudio += Triple(path, format, destination)
    }

    override fun writeText(text: String, destination: String) = Unit

    override fun deleteFile(path: String) {
        deleted += path
    }

    val models: MutableMap<AiModel, Long> = Collections.synchronizedMap(mutableMapOf(AiModel.OMNILINGUAL to 75L, AiModel.BANGLA to 30L))

    override fun modelStorage(): Map<AiModel, Long> = models.toMap()

    override fun deleteModel(model: AiModel) {
        models.remove(model)
    }

    override fun outputStorageBytes() = 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudiopsViewModelTest {
    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }
    private val dir = File.createTempFile("audiops-vm", "").apply {
        delete()
        mkdirs()
    }
    private var stores = 0
    private val created = mutableListOf<AudiopsViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(mainThread.asCoroutineDispatcher())
    }

    @After fun tearDown() {
        // Cancel every ViewModel and wait until its coroutines have finished: a coroutine that
        // resumes on Main while the next test swaps Main fails in TestMainDispatcher.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[CoroutineJob]!!.cancelAndJoin() } }
        Dispatchers.resetMain()
        mainThread.shutdownNow()
        dir.deleteRecursively()
    }

    private fun store() = createSettingsStore(File(dir, "store-${stores++}.preferences_pb").absolutePath)

    private fun viewModel(
        engine: AudioEngine,
        history: HistoryRepository = HistoryRepository(store()),
        meeting: LiveMeeting = LiveMeeting.Unavailable,
    ) = AudiopsViewModel(SavedStateHandle(), engine, SettingsRepository(store()), history, meeting).apply {
        created += this
        chooseInput("/music/in.wav", "in.wav")
    }

    /** Waits (in real time) until the state satisfies [predicate]. */
    private fun AudiopsViewModel.awaitState(predicate: (AudiopsState) -> Boolean): AudiopsState = runBlocking {
        withTimeout(10_000) { state.first(predicate) }
    }

    private fun AudiopsViewModel.awaitJobs(count: Int, status: JobStatus) =
        awaitState { s -> s.jobs.count { it.status == status } == count }.jobs.filter { it.status == status }

    /** Polls [condition] for up to 10 s; for effects that happen outside the state flow. */
    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Condition not met within 10 s" }
            Thread.sleep(20)
        }
    }

    private val gain = JobSpec.Process(listOf(AudioOperation.Gain(3f)))

    /** A live meeting that finishes when stopped, with fixed files. */
    private class FakeMeeting : LiveMeeting {
        override val available = true
        override val state = MutableStateFlow<MeetingState>(MeetingState.Idle)
        var started: Pair<TranscriptionModel, String>? = null
        var speakerName: ((Int) -> String)? = null
        var acknowledged = 0

        override fun start(model: TranscriptionModel, language: String, name: String, speakerName: (Int) -> String) {
            started = model to name
            this.speakerName = speakerName
            state.value = MeetingState.Live(0, 0f, emptyList(), null, 0)
        }

        override fun stop() {
            state.value = MeetingState.Finished(
                MeetingResult(
                    name = "Meeting 1",
                    transcriptPath = "/out/meeting.txt",
                    subtitlesPath = "/out/meeting.srt",
                    audioPath = "/out/meeting.wav",
                    speakersPath = "/out/meeting.speakers.json",
                    startedAt = 1_000,
                    durationMs = 90_000,
                    speakers = 2,
                    model = TranscriptionModel.PARAKEET,
                    preview = "Speaker 1 (0:00) Hello.",
                ),
            )
        }

        override fun discard() {
            state.value = MeetingState.Idle
        }

        override fun acknowledge() {
            acknowledged++
            state.value = MeetingState.Idle
        }
    }

    @Test fun finishedMeetingIsSavedToHistoryWithItsFiles() {
        val history = HistoryRepository(store())
        val meeting = FakeMeeting()
        val vm = viewModel(FakeEngine(), history, meeting)
        assertFalse(Tool.MEETING in vm.unsupportedTools)
        vm.startMeeting("Meeting 1") { "Speaker $it" }
        assertEquals(TranscriptionModel.PARAKEET to "Meeting 1", meeting.started)
        assertEquals("Speaker 2", meeting.speakerName?.invoke(2))
        vm.stopMeeting()
        val job = vm.awaitJobs(1, JobStatus.DONE).single()
        assertEquals(Tool.MEETING, job.tool)
        assertEquals(JobSpec.Meeting(TranscriptionModel.PARAKEET, 2, 90_000, SpeechLanguage.ENGLISH), job.spec)
        assertEquals(listOf("/out/meeting.txt", "/out/meeting.srt", "/out/meeting.wav", "/out/meeting.speakers.json"), job.files)
        assertEquals(job, vm.state.value.latestJob(Tool.MEETING))
        eventually { meeting.acknowledged == 1 && meeting.state.value == MeetingState.Idle }
        eventually { runBlocking { history.load() }.singleOrNull()?.id == job.id }
    }

    @Test fun meetingIsUnavailableWithoutAPlatformImplementation() {
        assertTrue(Tool.MEETING in viewModel(FakeEngine()).unsupportedTools)
    }

    @Test fun processJobFinishesAndIsSavedToHistory() {
        val history = HistoryRepository(store())
        val vm = viewModel(FakeEngine(), history)
        vm.start(Tool.VOLUME, gain)
        val done = vm.awaitJobs(1, JobStatus.DONE).single()
        assertEquals("/out/processed-1.wav", done.outputPath)
        assertNotNull(done.finishedAt)
        assertNull(done.progress)
        eventually { runBlocking { history.load() }.map { it.id } == listOf(done.id) }
    }

    @Test fun waveformLoadsForTheChosenInput() {
        val vm = viewModel(FakeEngine())
        val state = vm.awaitState { it.waveform != null }
        assertEquals(1_000L, state.waveform!!.durationMs)
        assertTrue(!state.waveformLoading)
    }

    @Test fun engineErrorsMarkTheJobFailed() {
        val engine = FakeEngine().apply { onProcess = { error("Corrupt file") } }
        val vm = viewModel(engine)
        vm.start(Tool.VOLUME, gain)
        val failed = vm.awaitJobs(1, JobStatus.FAILED).single()
        assertEquals(FailureKind.PROCESSING, failed.failure)
        assertEquals("Corrupt file", failed.failureDetail)
    }

    @Test fun blankTranscriptIsReportedAsNoSpeech() {
        val engine = FakeEngine().apply { transcript = "  \n" }
        val vm = viewModel(engine)
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.ENGLISH))
        val failed = vm.awaitJobs(1, JobStatus.FAILED).single()
        assertEquals(FailureKind.NO_SPEECH, failed.failure)
        assertEquals(listOf("/out/transcript.txt"), engine.deleted.toList())
    }

    @Test fun subtitlesPathIsKeptWhenTheEngineHasSubtitles() {
        val engine = FakeEngine().apply { subtitles = "/out/transcript.srt" }
        val vm = viewModel(engine)
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.ENGLISH))
        assertEquals("/out/transcript.srt", vm.awaitJobs(1, JobStatus.DONE).single().subtitlesPath)
    }

    @Test fun transcriptPreviewIsKept() {
        val vm = viewModel(FakeEngine())
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.ENGLISH))
        assertEquals("hello world", vm.awaitJobs(1, JobStatus.DONE).single().preview)
    }

    @Test fun transcriptionPassesItsLanguageAndModelToTheEngine() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.URDU, TranscriptionModel.WHISPER))
        vm.awaitJobs(1, JobStatus.DONE)
        assertEquals(listOf("ur" to TranscriptionModel.WHISPER), engine.transcribeCalls.toList())
    }

    @Test fun speakerTranscriptsAreNamedRewrittenAndTheirVoicesRemembered() {
        val engine = FakeEngine().apply { speakersFile = "/out/transcript.speakers.json" }
        val vm = viewModel(engine)
        vm.speakerName = { "Sprecher $it" }
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.GERMAN, TranscriptionModel.PARAKEET, speakers = true))
        val job = vm.awaitJobs(1, JobStatus.DONE).single()
        assertEquals(listOf("Sprecher 1"), engine.speakerTranscripts.toList())
        assertEquals("/out/transcript.speakers.json", job.speakersPath)
        assertTrue("/out/transcript.speakers.json" in job.files)

        vm.nameSpeakers(job.id, listOf("Ana", null), remember = true)
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.state.value.jobs.single().revision == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val named = vm.state.value.jobs.single()
        assertEquals(1, named.revision)
        assertEquals("Ana", named.preview)
        assertEquals(listOf(listOf("Ana", null) to true), engine.namedSpeakers.toList())
        while (vm.state.value.rememberedVoices.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf("Ana"), vm.state.value.rememberedVoices)

        vm.forgetVoice("Ana")
        while (vm.state.value.rememberedVoices.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(emptyList<String>(), vm.state.value.rememberedVoices)
    }

    @Test fun audioIsSavedInTheChosenFormat() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        assertEquals(listOf(SaveFormat.WAV, SaveFormat.M4A), vm.saveFormats)
        vm.start(Tool.SPEAK, JobSpec.Speak("Hi.", "pocket-alba"))
        val job = vm.awaitJobs(1, JobStatus.DONE).single()
        vm.saveAudio(job, SaveFormat.M4A, "content://dest/hi.m4a")
        vm.saveAudio(job, SaveFormat.OPUS, null)
        val deadline = System.currentTimeMillis() + 5_000
        while (engine.savedAudio.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf(Triple("/out/speech.wav", SaveFormat.M4A, "content://dest/hi.m4a")), engine.savedAudio.toList())
    }

    @Test fun sharedAudioBecomesTheCurrentRecordingOnTheToolsTab() {
        val vm = viewModel(FakeEngine())
        vm.selectTab(Tab.SETTINGS)
        vm.open(Tool.TRIM)
        vm.inputShared("file:///cache/shared/1-note.ogg", "note.ogg")
        val state = vm.state.value
        assertEquals(AudioInput("file:///cache/shared/1-note.ogg", "note.ogg"), state.input)
        assertEquals(Tab.TOOLS, state.tab)
        assertNull(state.tool)
    }

    @Test fun speechNeedsNoInputRecording() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.start(Tool.SPEAK, JobSpec.Speak("  Hello,\n  world.  ", "pocket-alba", 1.5f))
        val done = vm.awaitJobs(1, JobStatus.DONE).single()
        assertEquals("/out/speech.wav", done.outputPath)
        assertEquals("Hello, world.", done.inputName)
        assertEquals("", done.inputId)
        assertEquals(listOf(Triple("  Hello,\n  world.  ", "pocket-alba", 1.5f)), engine.spoken.toList())
        assertEquals(done.id, vm.state.value.latestJob(Tool.SPEAK)?.id)
    }

    @Test fun unsupportedToolsFailWithoutRunning() {
        val vm = viewModel(FakeEngine(supportsEnhance = false))
        assertEquals(setOf(Tool.ENHANCE, Tool.MEETING), vm.unsupportedTools)
        vm.start(Tool.ENHANCE, JobSpec.Enhance())
        assertEquals(FailureKind.UNSUPPORTED, vm.awaitJobs(1, JobStatus.FAILED).single().failure)
    }

    @Test fun enhanceModesUseTheirOwnModel() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.start(Tool.ENHANCE, JobSpec.Enhance(EnhanceMode.FAST))
        val fast = vm.awaitJobs(1, JobStatus.DONE).single()
        // Fast runs the AI noise-reduction operation at full strength; Studio runs Resemble Enhance.
        assertEquals(listOf(listOf<AudioOperation>(AudioOperation.Denoise(1f))), engine.processed.toList())
        assertEquals(0, engine.enhanced.get())
        assertEquals("/out/processed-1.wav", fast.outputPath)
        vm.start(Tool.ENHANCE, JobSpec.Enhance(EnhanceMode.STUDIO))
        vm.awaitJobs(2, JobStatus.DONE)
        assertEquals(1, engine.enhanced.get())
        assertEquals(1, engine.processed.size)
    }

    @Test fun separationKeepsEveryStemAndDeletingRemovesThemAll() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.start(Tool.SEPARATE, JobSpec.Separate)
        val job = vm.awaitJobs(1, JobStatus.DONE).single()
        val stems = checkNotNull(job.stemPaths)
        assertEquals(SeparatedStem.entries.size, stems.size)
        assertEquals(stems.first(), job.outputPath)
        assertEquals(stems.toSet(), job.files.toSet())
        vm.delete(job.id)
        eventually { engine.deleted.toSet().containsAll(stems) }
    }

    @Test fun separationIsUnsupportedWhereTheEngineCannotDoIt() {
        val vm = viewModel(FakeEngine(supportsSeparate = false))
        assertEquals(setOf(Tool.SEPARATE, Tool.MEETING), vm.unsupportedTools)
        vm.start(Tool.SEPARATE, JobSpec.Separate)
        assertEquals(FailureKind.UNSUPPORTED, vm.awaitJobs(1, JobStatus.FAILED).single().failure)
    }

    @Test fun jobsRunOneAtATimeInQueueOrder() {
        val engine = FakeEngine().apply { onProcess = { Thread.sleep(50) } }
        val vm = viewModel(engine)
        val specs = (1..3).map { JobSpec.Process(listOf(AudioOperation.Gain(it.toFloat()))) }
        specs.forEach { vm.start(Tool.VOLUME, it) }
        vm.awaitJobs(3, JobStatus.DONE)
        assertEquals(specs.map { it.operations }, engine.processed.toList())
        assertEquals(1, engine.maxConcurrent.get())
    }

    @Test fun cancellingStopsTheRunningJobAndSkipsQueuedOnes() {
        val started = CountDownLatch(1)
        val engine = FakeEngine().apply {
            onProcess = {
                started.countDown()
                Thread.sleep(30_000) // interrupted by cancel
            }
        }
        val vm = viewModel(engine)
        vm.start(Tool.VOLUME, gain)
        vm.start(Tool.FADE, JobSpec.Process(listOf(AudioOperation.Fade(10, 10))))
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val (running, queued) = vm.awaitState { s -> s.jobs.any { it.status == JobStatus.RUNNING } }.jobs
        assertEquals(JobStatus.QUEUED, queued.status)
        vm.cancel(queued.id)
        vm.cancel(running.id)
        val cancelled = vm.awaitJobs(2, JobStatus.CANCELLED)
        assertEquals(setOf(running.id, queued.id), cancelled.map { it.id }.toSet())
        assertTrue(engine.processed.isEmpty())
    }

    @Test fun cancelAllStopsEveryActiveJobWithOneMessage() {
        val started = CountDownLatch(1)
        val engine = FakeEngine().apply {
            onProcess = {
                started.countDown()
                Thread.sleep(30_000) // interrupted by cancel
            }
        }
        val vm = viewModel(engine)
        repeat(3) { vm.start(Tool.VOLUME, gain) }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        vm.awaitState { s -> s.jobs.any { it.status == JobStatus.RUNNING } }
        vm.cancelAll()
        assertEquals(3, vm.awaitJobs(3, JobStatus.CANCELLED).size)
        assertTrue(vm.state.value.activeJobs.isEmpty())
        val message = runBlocking { withTimeout(10_000) { vm.messages.first() } }
        assertEquals(UiMessage.Cancelled, message)
        assertNull(runBlocking { withTimeoutOrNull(300) { vm.messages.first() } })
        assertTrue(engine.processed.isEmpty())
    }

    @Test fun modelsAreListedAndDeletedOneByOne() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        val listed = runBlocking { withTimeout(10_000) { vm.modelStorage.first { it != null } } }
        assertEquals(mapOf(AiModel.OMNILINGUAL to 75L, AiModel.BANGLA to 30L), listed)
        vm.deleteModel(AiModel.OMNILINGUAL)
        val after = runBlocking { withTimeout(10_000) { vm.modelStorage.first { it == mapOf(AiModel.BANGLA to 30L) } } }
        assertEquals(mapOf(AiModel.BANGLA to 30L), after)
        assertEquals(UiMessage.ModelsDeleted, runBlocking { withTimeout(10_000) { vm.messages.first() } })
    }

    @Test fun deleteAndClearHistoryRemoveOutputs() {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        repeat(3) { vm.start(Tool.VOLUME, gain) }
        val done = vm.awaitJobs(3, JobStatus.DONE)
        vm.delete(done.first().id)
        vm.awaitState { it.jobs.size == 2 }
        vm.clearHistory()
        vm.awaitState { it.jobs.isEmpty() }
        eventually { engine.deleted.size == 3 }
        assertEquals(done.map { it.outputPath }.toSet(), engine.deleted.toSet())
    }

    @Test fun backClosesTheToolThenReportsNothingToClose() {
        val vm = viewModel(FakeEngine())
        vm.selectTab(Tab.ACTIVITY)
        vm.open(Tool.TRIM)
        assertEquals(Tool.TRIM, vm.state.value.tool)
        assertTrue(vm.back())
        assertNull(vm.state.value.tool)
        assertEquals(Tab.ACTIVITY, vm.state.value.tab)
        assertTrue(!vm.back())
    }

    @Test fun finishedJobsAnnounceThemselves() {
        val vm = viewModel(FakeEngine())
        vm.start(Tool.ANALYZE, JobSpec.Analyze)
        val message = runBlocking { withTimeout(10_000) { vm.messages.first() } }
        assertEquals(UiMessage.JobFinished(Tool.ANALYZE), message)
        assertEquals(-23f, vm.awaitJobs(1, JobStatus.DONE).single().report!!.loudnessLufs)
    }
}
