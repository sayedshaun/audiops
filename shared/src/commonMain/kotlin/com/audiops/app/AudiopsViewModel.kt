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
    val language: SpeechLanguage = SpeechLanguage.ENGLISH,
    /** Active jobs first (in queue order), then history, newest first. */
    val jobs: List<Job> = emptyList(),
    /** Names of the voices remembered on this device, A to Z. */
    val rememberedVoices: List<String> = emptyList(),
) {
    val activeJobs: List<Job> get() = jobs.filter { it.status.active }

    /** Tools of the most recent jobs, newest first, each once; at most [limit]. */
    fun recentTools(limit: Int): List<Tool> = jobs
        .sortedByDescending { it.createdAt }
        .map { it.tool }
        .distinct()
        .take(limit)

    /** The most recent job of [tool] on the current input (any input for text to speech and meetings), if any. */
    fun latestJob(tool: Tool): Job? = jobs
        .filter { it.tool == tool && (tool == Tool.SPEAK || tool == Tool.MEETING || it.inputId == input?.id) }
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
    private val meeting: LiveMeeting = LiveMeeting.Unavailable,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AudiopsState(
            tab = savedState.get<String>(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.TOOLS,
            tool = savedState.get<String>(KEY_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } },
            input = savedState.get<String>(KEY_ID)?.let { AudioInput(it, savedState.get<String>(KEY_NAME).orEmpty()) },
            language = savedState.get<String>(KEY_LANGUAGE)
                ?.let { name -> SpeechLanguage.entries.firstOrNull { it.name == name } }
                ?.takeIf { it in transcriptionLanguages }
                ?: SpeechLanguage.ENGLISH,
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
        if (!engine.supportsSpeak) add(Tool.SPEAK)
        if (!engine.supportsSeparate) add(Tool.SEPARATE)
        if (!meeting.available) add(Tool.MEETING)
    }

    /** The live meeting, if one is starting, running or finishing. */
    val meetingState: StateFlow<MeetingState> = meeting.state

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
        viewModelScope.launch {
            meeting.state.collect { state ->
                if (state is MeetingState.Finished) {
                    recordMeeting(state.result)
                    meeting.acknowledge()
                }
            }
        }
        if (_state.value.input != null) loadWaveform()
        refreshStorage()
        refreshVoices()
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

    /** Audio shared into the app: it becomes the current recording and the Tools tab opens. */
    fun inputShared(id: String, name: String) {
        back()
        selectTab(Tab.TOOLS)
        chooseInput(id, name)
        _messages.trySend(UiMessage.InputReady(name))
    }

    fun recordFailed(detail: String?) {
        _messages.trySend(UiMessage.RecordFailed(detail))
    }

    fun inputShareFailed(detail: String?) {
        _messages.trySend(UiMessage.ImportFailed(detail))
    }

    /** Opens the tool for a finished job's input again (Activity → "Open in tool"). */
    fun reopen(job: Job) {
        val hasInput = job.spec !is JobSpec.Speak && job.spec !is JobSpec.Meeting
        if (hasInput && _state.value.input?.id != job.inputId) chooseInput(job.inputId, job.inputName)
        open(job.tool)
    }

    /**
     * Sets the spoken language for transcription and live meetings, and remembers it. When the chosen
     * model cannot transcribe it, the recommended model for the language is chosen instead.
     */
    fun setLanguage(language: SpeechLanguage) {
        savedState[KEY_LANGUAGE] = language.name
        _state.update { it.copy(language = language) }
        viewModelScope.launch {
            settingsRepository.setDefaultLanguage(language)
            val model = settings.value?.transcriptionModel
            if (model == null ||
                !model.supports(language)
            ) {
                settingsRepository.setTranscriptionModel(recommendedTranscriptionModel(language))
            }
        }
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
        // Text to speech has no input recording: the job is named after the start of its text.
        val input = if (spec is JobSpec.Speak) {
            AudioInput(id = "", name = spec.text.trim().replace(Regex("\\s+"), " ").take(SPEAK_NAME_LENGTH))
        } else {
            _state.value.input ?: return
        }
        val unsupported = (spec is JobSpec.Enhance && !engine.supportsEnhance) ||
            (spec is JobSpec.Transcribe && !engine.supportsTranscribe) ||
            (spec is JobSpec.Speak && !engine.supportsSpeak) ||
            (spec is JobSpec.Separate && !engine.supportsSeparate)
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
        viewModelScope.launch { runCatching { runOnWorker { job.files.forEach(engine::deleteFile) } } }
        persist()
        refreshStorage()
    }

    /** Removes every finished job and its output. */
    fun clearHistory() {
        val finished = _state.value.jobs.filter { !it.status.active }
        _state.update { s -> s.copy(jobs = s.jobs.filter { it.status.active }) }
        viewModelScope.launch {
            runCatching { runOnWorker { finished.flatMap { it.files }.forEach(engine::deleteFile) } }
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

    /** The formats audio can be saved in on this device. */
    val saveFormats: List<SaveFormat> get() = engine.saveFormats

    /** Saves the processed audio of [job] to [destination] as [format]. */
    fun saveAudio(job: Job, format: SaveFormat, destination: String?) {
        val path = job.outputPath ?: return
        destination ?: return
        viewModelScope.launch {
            val result = runCatching { runOnWorker { engine.saveAudio(path, format, destination) } }
            _messages.trySend(result.exceptionOrNull()?.let { UiMessage.SaveFailed(it.message) } ?: UiMessage.Saved)
        }
    }

    /** Saves the subtitles of a transcription job to [destination] as [format]. */
    fun saveSubtitles(job: Job, format: SubtitleFormat, destination: String?) {
        val path = job.subtitlesPath ?: return
        destination ?: return
        viewModelScope.launch {
            val result = runCatching {
                runOnWorker {
                    val srt = engine.readText(path)
                    engine.writeText(if (format == SubtitleFormat.VTT) Subtitles.toVtt(srt) else srt, destination)
                }
            }
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
                    is JobSpec.Meeting -> error("Live meetings are recorded, not queued")

                    is JobSpec.Enhance -> job.copy(
                        outputPath = when (spec.mode) {
                            // Fast: the AI noise-reduction model at full strength; Studio: Resemble Enhance.
                            EnhanceMode.FAST -> engine.process(job.inputId, listOf(AudioOperation.Denoise(1f)), listener)

                            EnhanceMode.STUDIO -> engine.enhance(job.inputId, listener)
                        },
                    )

                    is JobSpec.Transcribe -> {
                        val path = engine.transcribe(job.inputId, spec.language.code, spec.model, spec.speakers, speakerName, listener)
                        val text = engine.readText(path)
                        if (text.isBlank()) {
                            engine.deleteFile(path)
                            job.copy(status = JobStatus.FAILED, failure = FailureKind.NO_SPEECH)
                        } else {
                            job.copy(
                                outputPath = path,
                                preview = text.take(PREVIEW_LENGTH),
                                subtitlesPath = engine.subtitlesFor(path),
                                speakersPath = engine.speakersFile(path),
                            )
                        }
                    }

                    JobSpec.Separate -> {
                        val paths = engine.separate(job.inputId, listener)
                        job.copy(outputPath = paths.first(), stemPaths = paths)
                    }

                    is JobSpec.Speak -> job.copy(outputPath = engine.speak(spec.text, spec.voice, spec.speed, listener))

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

    // Live meetings

    /**
     * Starts a live meeting with the transcription model chosen in Settings. [name] names the
     * meeting in history; [speakerName] names speakers (1-based) in the saved transcript.
     */
    fun startMeeting(name: String, speakerName: (Int) -> String) {
        val language = _state.value.language
        val model = settings.value?.transcriptionModel?.takeIf { it != TranscriptionModel.AUTO && it.supports(language) }
            ?: recommendedTranscriptionModel(language)
        meetingLanguage = language
        meeting.start(model, language.code, name, speakerName)
    }

    /** The language of the meeting that is running, for its history entry. */
    private var meetingLanguage = SpeechLanguage.AUTO

    fun stopMeeting() = meeting.stop()

    fun discardMeeting() {
        meeting.discard()
        _messages.trySend(UiMessage.Cancelled)
    }

    /**
     * Names speakers ("Speaker 1", "Speaker 2"…) in saved transcripts. The UI sets it from its
     * localized text; transcripts already saved keep the words they were written with.
     */
    var speakerName: (Int) -> String = { "Speaker $it" }

    /** The speakers of a transcript with speakers, for naming them. */
    suspend fun speakers(job: Job): List<SpeakerInfo> {
        val path = job.outputPath ?: return emptyList()
        if (job.speakersPath == null) return emptyList()
        return runCatching { runOnWorker { engine.speakers(path) } }.getOrDefault(emptyList())
    }

    /**
     * Names the speakers of [jobId]'s transcript ([names] in speaker order; blank keeps "Speaker N")
     * and, with [remember], keeps the named voices so later transcripts are named by themselves.
     */
    fun nameSpeakers(jobId: String, names: List<String?>, remember: Boolean) {
        val job = _state.value.jobs.firstOrNull { it.id == jobId } ?: return
        val path = job.outputPath ?: return
        val name = speakerName
        viewModelScope.launch {
            val text = runCatching {
                runOnWorker {
                    engine.nameSpeakers(path, names, remember, name)
                    engine.readText(path)
                }
            }.getOrElse {
                _messages.trySend(UiMessage.SpeakersNotNamed)
                return@launch
            }
            update(jobId) { it.copy(preview = text.take(PREVIEW_LENGTH), revision = it.revision + 1) }
            persist()
            refreshVoices()
            _messages.trySend(UiMessage.SpeakersNamed)
        }
    }

    /** Forgets a remembered voice. */
    fun forgetVoice(name: String) {
        viewModelScope.launch {
            runCatching { runOnWorker { engine.forgetVoice(name) } }
            refreshVoices()
        }
    }

    private fun refreshVoices() {
        viewModelScope.launch {
            val names = runCatching { runOnWorker { engine.rememberedVoices() } }.getOrDefault(emptyList())
            _state.update { it.copy(rememberedVoices = names) }
        }
    }

    /** Dismisses a failed meeting. */
    fun acknowledgeMeeting() = meeting.acknowledge()

    private fun recordMeeting(result: MeetingResult) {
        val job = Job(
            id = "${result.startedAt}-${Random.nextInt(1_000_000)}",
            tool = Tool.MEETING,
            inputId = result.audioPath,
            inputName = result.name,
            spec = JobSpec.Meeting(result.model, result.speakers, result.durationMs, meetingLanguage),
            status = JobStatus.DONE,
            createdAt = result.startedAt,
            finishedAt = currentTimeMillis(),
            outputPath = result.transcriptPath,
            subtitlesPath = result.subtitlesPath,
            audioPath = result.audioPath,
            speakersPath = result.speakersPath,
            preview = result.preview.take(PREVIEW_LENGTH),
        )
        _state.update { s ->
            val active = s.jobs.filter { it.status.active }
            s.copy(jobs = active + job + s.jobs.filter { !it.status.active })
        }
        persist()
        refreshStorage()
        _messages.trySend(UiMessage.JobFinished(Tool.MEETING))
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
            finished.drop(HistoryRepository.MAX_JOBS).flatMap { it.files }.forEach { path ->
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

    fun setTranscriptionModel(model: TranscriptionModel) {
        viewModelScope.launch { settingsRepository.setTranscriptionModel(model) }
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
        const val SPEAK_NAME_LENGTH = 60
        const val WAVEFORM_BARS = 160
        const val PREVIEW_LENGTH = 160
    }
}
