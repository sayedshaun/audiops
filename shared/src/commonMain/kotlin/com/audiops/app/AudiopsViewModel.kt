package com.audiops.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.coroutines.Job as CoroutineJob

enum class Tab { TOOLS, ACTIVITY, SETTINGS }

/** Why the waveform preview could not be shown. */
sealed interface InputProblem {
    data object Unsupported : InputProblem
    data class Unreadable(val detail: String?) : InputProblem
}

data class AudiopsState(
    val tab: Tab = Tab.TOOLS,
    /** Open tool screen, shown above the tabs; null on the tab level. */
    val tool: Tool? = null,
    val input: AudioInput? = null,
    val waveform: WaveformData? = null,
    val waveformLoading: Boolean = false,
    val inputProblem: InputProblem? = null,
    val language: SpeechLanguage = SpeechLanguage.AUTO,
    /** Active jobs first (in queue order), then history, newest first. */
    val jobs: List<Job> = emptyList(),
) {
    val activeJobs: List<Job> get() = jobs.filter { it.status.active }

    /** The most recent job of [tool] on the current input, if any. */
    fun latestJob(tool: Tool): Job? = jobs
        .filter { it.tool == tool && it.inputId == input?.id }
        .maxByOrNull { it.createdAt }
}

/**
 * Owns app state and runs jobs one at a time through the platform [engine]. Jobs can be
 * queued from any tool; the Activity tab shows the queue and history. Selections survive
 * process death through [SavedStateHandle]; finished jobs are persisted by [history].
 */
class AudiopsViewModel(
    private val savedState: SavedStateHandle,
    private val engine: AudioEngine,
    private val settingsRepository: SettingsRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AudiopsState(
            tab = savedState.get<String>(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.TOOLS,
            tool = savedState.get<String>(KEY_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } },
            input = savedState.get<String>(KEY_ID)?.let { AudioInput(it, savedState.get<String>(KEY_NAME).orEmpty()) },
            language = savedState.get<String>(KEY_LANGUAGE)
                ?.let { name -> SpeechLanguage.entries.firstOrNull { it.name == name } }
                ?: SpeechLanguage.AUTO,
        ),
    )
    val state: StateFlow<AudiopsState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /** One-shot messages for the snackbar. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Settings; null until loaded (the Android splash screen waits for it). */
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .map<UserSettings, UserSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Tools this platform's engine cannot run (shown disabled with a badge). */
    val unsupportedTools: Set<Tool> = buildSet {
        if (!engine.supportsEnhance) add(Tool.ENHANCE)
        if (!engine.supportsTranscribe) add(Tool.TRANSCRIBE)
    }

    /** Bytes per downloaded model; null while measuring. */
    private val _modelStorage = MutableStateFlow<Map<AiModel, Long>?>(null)
    val modelStorage: StateFlow<Map<AiModel, Long>?> = _modelStorage.asStateFlow()

    private val _outputStorage = MutableStateFlow<Long?>(null)
    val outputStorage: StateFlow<Long?> = _outputStorage.asStateFlow()

    private val queue = Channel<String>(Channel.UNLIMITED)
    private var runningJob: CoroutineJob? = null
    private var runningId: String? = null
    private var waveformJob: CoroutineJob? = null

    init {
        viewModelScope.launch {
            val stored = runCatching { history.load() }.getOrDefault(emptyList())
            _state.update { s -> s.copy(jobs = s.jobs + stored.filter { old -> s.jobs.none { it.id == old.id } }) }
        }
        if (savedState.get<String>(KEY_LANGUAGE) == null) {
            viewModelScope.launch {
                val loaded = settings.filterNotNull().first()
                if (savedState.get<String>(KEY_LANGUAGE) == null) {
                    _state.update { it.copy(language = loaded.defaultLanguage) }
                }
            }
        }
        viewModelScope.launch { for (id in queue) runQueued(id) }
        if (_state.value.input != null) loadWaveform()
        refreshStorage()
    }

    // Navigation

    fun selectTab(tab: Tab) {
        savedState[KEY_TAB] = tab.name
        _state.update { it.copy(tab = tab) }
        if (tab == Tab.SETTINGS) refreshStorage()
    }

    fun open(tool: Tool) {
        savedState[KEY_TOOL] = tool.name
        _state.update { it.copy(tool = tool) }
    }

    /** Closes the open tool. Returns false when there was nothing to close. */
    fun back(): Boolean {
        if (_state.value.tool == null) return false
        savedState[KEY_TOOL] = null
        _state.update { it.copy(tool = null) }
        return true
    }

    // Input

    fun chooseInput(id: String, name: String) {
        savedState[KEY_ID] = id
        savedState[KEY_NAME] = name
        _state.update { it.copy(input = AudioInput(id, name)) }
        loadWaveform()
    }

    /** Opens the tool for a finished job's input again (Activity → "Open in tool"). */
    fun reopen(job: Job) {
        if (_state.value.input?.id != job.inputId) chooseInput(job.inputId, job.inputName)
        open(job.tool)
    }

    fun setLanguage(language: SpeechLanguage) {
        savedState[KEY_LANGUAGE] = language.name
        _state.update { it.copy(language = language) }
    }

    private fun loadWaveform() {
        val input = _state.value.input ?: return
        waveformJob?.cancel()
        _state.update { it.copy(waveform = null, waveformLoading = true, inputProblem = null) }
        waveformJob = viewModelScope.launch {
            try {
                val waveform = runOnWorker { engine.waveform(input.id, WAVEFORM_BARS) }
                _state.update { if (it.input?.id == input.id) it.copy(waveform = waveform, waveformLoading = false) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!e.isRecoverable()) throw e
                val problem = if (e is UnsupportedOnPlatformException) InputProblem.Unsupported else InputProblem.Unreadable(e.message)
                _state.update { if (it.input?.id == input.id) it.copy(inputProblem = problem, waveformLoading = false) else it }
            }
        }
    }

    // Jobs

    /** Queues [spec] for [tool] on the current input. */
    fun start(tool: Tool, spec: JobSpec) {
        val input = _state.value.input ?: return
        val unsupported = (spec is JobSpec.Enhance && !engine.supportsEnhance) ||
            (spec is JobSpec.Transcribe && !engine.supportsTranscribe)
        val job = Job(
            id = "${currentTimeMillis()}-${Random.nextInt(1_000_000)}",
            tool = tool,
            inputId = input.id,
            inputName = input.name,
            spec = spec,
            status = if (unsupported) JobStatus.FAILED else JobStatus.QUEUED,
            createdAt = currentTimeMillis(),
            failure = if (unsupported) FailureKind.UNSUPPORTED else null,
            progress = if (unsupported) null else JobProgress(Stage.QUEUED),
        )
        _state.update { s ->
            val active = s.jobs.filter { it.status.active }
            val rest = s.jobs.filter { !it.status.active }
            s.copy(jobs = active + job + rest)
        }
        if (unsupported) persist() else queue.trySend(job.id)
    }

    fun cancel(jobId: String) {
        val job = _state.value.jobs.firstOrNull { it.id == jobId } ?: return
        when (job.status) {
            JobStatus.QUEUED -> {
                finish(jobId) { it.copy(status = JobStatus.CANCELLED, finishedAt = currentTimeMillis(), progress = null) }
                _messages.trySend(UiMessage.Cancelled)
            }

            JobStatus.RUNNING -> if (runningId == jobId) runningJob?.cancel()

            else -> Unit
        }
    }

    /** Cancels every queued and running job (the notification's Cancel action), with one message. */
    fun cancelAll() {
        val active = _state.value.activeJobs
        if (active.isEmpty()) return
        active.filter { it.status == JobStatus.QUEUED }.forEach { job ->
            finish(job.id) { it.copy(status = JobStatus.CANCELLED, finishedAt = currentTimeMillis(), progress = null) }
        }
        val running = runningJob
        // The running job reports its own cancellation (see execute).
        if (running != null) running.cancel() else _messages.trySend(UiMessage.Cancelled)
    }

    /** Removes a finished job and its output file. */
    fun delete(jobId: String) {
        val job = _state.value.jobs.firstOrNull { it.id == jobId } ?: return
        if (job.status.active) return
        _state.update { s -> s.copy(jobs = s.jobs.filter { it.id != jobId }) }
        job.outputPath?.let { path -> viewModelScope.launch { runCatching { runOnWorker { engine.deleteFile(path) } } } }
        persist()
        refreshStorage()
    }

    /** Removes every finished job and its output. */
    fun clearHistory() {
        val finished = _state.value.jobs.filter { !it.status.active }
        _state.update { s -> s.copy(jobs = s.jobs.filter { it.status.active }) }
        viewModelScope.launch {
            runCatching { runOnWorker { finished.mapNotNull { it.outputPath }.forEach(engine::deleteFile) } }
            refreshStorage()
        }
        persist()
        _messages.trySend(UiMessage.HistoryCleared)
    }

    fun saveOutput(job: Job, destination: String?) {
        val path = job.outputPath ?: return
        destination ?: return
        viewModelScope.launch {
            val result = runCatching { runOnWorker { engine.copyFile(path, destination) } }
            _messages.trySend(result.exceptionOrNull()?.let { UiMessage.SaveFailed(it.message) } ?: UiMessage.Saved)
        }
    }

    /** Reads a transcript or other text output. */
    suspend fun readText(path: String): String? = runCatching { runOnWorker { engine.readText(path) } }.getOrNull()

    fun notifyCopied() {
        _messages.trySend(UiMessage.Copied)
    }

    private suspend fun runQueued(id: String) {
        val job = _state.value.jobs.firstOrNull { it.id == id } ?: return
        if (job.status != JobStatus.QUEUED) return
        update(id) { it.copy(status = JobStatus.RUNNING, progress = JobProgress(Stage.PREPARING)) }
        coroutineScope {
            val worker = launch { execute(job) }
            runningJob = worker
            runningId = id
            worker.join()
        }
        runningJob = null
        runningId = null
    }

    private suspend fun execute(job: Job) {
        val listener = object : ProcessingListener {
            override fun onModelDownload(index: Int, total: Int) =
                update(job.id) { it.copy(progress = JobProgress(Stage.DOWNLOADING_MODEL, index, total)) }

            override fun onModelDownloadProgress(index: Int, total: Int, fileFraction: Float) =
                update(job.id) { it.copy(progress = JobProgress(Stage.DOWNLOADING_MODEL, index, total, fileFraction)) }

            override fun onSection(index: Int, total: Int) =
                update(job.id) { it.copy(progress = JobProgress(Stage.PROCESSING, index, total)) }
        }
        try {
            val done = runOnWorker {
                when (val spec = job.spec) {
                    JobSpec.Enhance -> job.copy(outputPath = engine.enhance(job.inputId, listener))

                    is JobSpec.Transcribe -> {
                        val path = engine.transcribe(job.inputId, spec.language.code, listener)
                        val text = engine.readText(path)
                        if (text.isBlank()) {
                            engine.deleteFile(path)
                            job.copy(status = JobStatus.FAILED, failure = FailureKind.NO_SPEECH)
                        } else {
                            job.copy(outputPath = path, preview = text.take(PREVIEW_LENGTH))
                        }
                    }

                    is JobSpec.Process -> job.copy(outputPath = engine.process(job.inputId, spec.operations, listener))

                    JobSpec.Analyze -> job.copy(report = engine.analyze(job.inputId, listener))
                }
            }
            val status = if (done.status == JobStatus.FAILED) JobStatus.FAILED else JobStatus.DONE
            finish(job.id) { done.copy(status = status, finishedAt = currentTimeMillis(), progress = null) }
            _messages.trySend(if (status == JobStatus.DONE) UiMessage.JobFinished(job.tool) else UiMessage.JobFailed(job.tool))
        } catch (e: CancellationException) {
            finish(job.id) { it.copy(status = JobStatus.CANCELLED, finishedAt = currentTimeMillis(), progress = null) }
            _messages.trySend(UiMessage.Cancelled)
            throw e
        } catch (e: Throwable) {
            if (!e.isRecoverable()) throw e
            val kind = when {
                e is UnsupportedOnPlatformException -> FailureKind.UNSUPPORTED
                e.isOutOfMemory() -> FailureKind.OUT_OF_MEMORY
                else -> FailureKind.PROCESSING
            }
            finish(job.id) {
                it.copy(
                    status = JobStatus.FAILED,
                    finishedAt = currentTimeMillis(),
                    progress = null,
                    failure = kind,
                    failureDetail = e.message,
                )
            }
            _messages.trySend(UiMessage.JobFailed(job.tool))
        }
    }

    private fun update(id: String, transform: (Job) -> Job) {
        _state.update { s -> s.copy(jobs = s.jobs.map { if (it.id == id) transform(it) else it }) }
    }

    /** Applies [transform] and moves the job from the active list into history. */
    private fun finish(id: String, transform: (Job) -> Job) {
        _state.update { s ->
            val updated = s.jobs.firstOrNull { it.id == id }?.let(transform) ?: return@update s
            val others = s.jobs.filter { it.id != id }
            s.copy(jobs = others.filter { it.status.active } + updated + others.filter { !it.status.active })
        }
        persist()
        refreshStorage()
    }

    /** Saves history on [viewModelScope], so it still runs when the job coroutine was cancelled. */
    private fun persist() {
        val snapshot = _state.value.jobs
        viewModelScope.launch {
            val finished = snapshot.filter { !it.status.active }
            finished.drop(HistoryRepository.MAX_JOBS).mapNotNull { it.outputPath }.forEach { path ->
                runCatching { runOnWorker { engine.deleteFile(path) } }
            }
            runCatching { history.save(finished) }
        }
    }

    // Settings and storage

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPureBlack(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setDefaultLanguage(language: SpeechLanguage) {
        viewModelScope.launch { settingsRepository.setDefaultLanguage(language) }
        setLanguage(language)
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _modelStorage.value = runCatching { runOnWorker(engine::modelStorage) }.getOrDefault(emptyMap())
            _outputStorage.value = runCatching { runOnWorker(engine::outputStorageBytes) }.getOrDefault(0L)
        }
    }

    /** Deletes one downloaded model. Not allowed while a job may be reading it. */
    fun deleteModel(model: AiModel) {
        if (_state.value.activeJobs.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { runOnWorker { engine.deleteModel(model) } }
            refreshStorage()
            _messages.trySend(UiMessage.ModelsDeleted)
        }
    }

    override fun onCleared() {
        runningJob?.cancel()
        waveformJob?.cancel()
        queue.close()
        _messages.close()
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_TOOL = "tool"
        const val KEY_ID = "input_id"
        const val KEY_NAME = "input_name"
        const val KEY_LANGUAGE = "language"
        const val WAVEFORM_BARS = 160
        const val PREVIEW_LENGTH = 160
    }
}
