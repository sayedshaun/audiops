package com.audiops.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
private class FakeEngine(override val supportsEnhance: Boolean = true, override val supportsTranscribe: Boolean = true) : AudioEngine {
    val deleted: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val processed: MutableList<List<AudioOperation>> = Collections.synchronizedList(mutableListOf())
    private val running = AtomicInteger()
    val maxConcurrent = AtomicInteger()

    /** Runs inside [process]; may block or throw. */
    @Volatile var onProcess: () -> Unit = {}

    @Volatile var transcript = "hello world"

    override fun enhance(source: String, listener: ProcessingListener) = "/out/enhanced.wav"

    override fun transcribe(source: String, language: String, listener: ProcessingListener) = "/out/transcript.txt"

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

    override fun writeText(text: String, destination: String) = Unit

    override fun deleteFile(path: String) {
        deleted += path
    }

    override fun modelStorageBytes() = 0L

    override fun deleteModels() = Unit

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

    private fun viewModel(engine: AudioEngine, history: HistoryRepository = HistoryRepository(store())) =
        AudiopsViewModel(SavedStateHandle(), engine, SettingsRepository(store()), history).apply {
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

    @Test fun transcriptPreviewIsKept() {
        val vm = viewModel(FakeEngine())
        vm.start(Tool.TRANSCRIBE, JobSpec.Transcribe(SpeechLanguage.ENGLISH))
        assertEquals("hello world", vm.awaitJobs(1, JobStatus.DONE).single().preview)
    }

    @Test fun unsupportedToolsFailWithoutRunning() {
        val vm = viewModel(FakeEngine(supportsEnhance = false))
        assertEquals(setOf(Tool.ENHANCE), vm.unsupportedTools)
        vm.start(Tool.ENHANCE, JobSpec.Enhance)
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
