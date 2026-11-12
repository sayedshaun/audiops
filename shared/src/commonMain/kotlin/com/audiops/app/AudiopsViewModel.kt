package com.audiops.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

enum class Tool { ENHANCE, TRANSCRIBE }

/** Spoken languages. Labels are resolved by the UI. */
enum class SpeechLanguage(val code: String) { AUTO(""), BANGLA("bn"), ENGLISH("en"), HINDI("hi") }

/** A recording chosen by the user. [id] is a platform document id (URI or file path). */
data class AudioInput(val id: String, val name: String)

enum class Stage { PREPARING, DOWNLOADING_MODEL, ENHANCING, TRANSCRIBING }

sealed interface Work {
    data object Idle : Work

    /** [index] of [total] is known for downloads and sections; both are 0 while preparing. */
    data class Running(val stage: Stage, val index: Int = 0, val total: Int = 0) : Work {
        /** Fraction done, or null while the amount of work is unknown. */
        val progress: Float? get() = if (total > 0) ((index - 1).toFloat() / total).coerceIn(0f, 1f) else null
    }
}

/** Errors shown inline. The UI turns them into localized text. */
sealed interface UiError {
    data class Processing(val detail: String?) : UiError
    data object OutOfMemory : UiError
    data object NoSpeech : UiError
    data object Unsupported : UiError
}

/** One-shot snackbar messages. */
sealed interface UiMessage {
    data object Enhanced : UiMessage
    data object Transcribed : UiMessage
    data object Saved : UiMessage
    data class SaveFailed(val detail: String?) : UiMessage
    data object Cancelled : UiMessage
    data object ModelsDeleted : UiMessage
}

data class AudiopsState(
    val tool: Tool? = null,
    val input: AudioInput? = null,
    val language: SpeechLanguage = SpeechLanguage.AUTO,
    val work: Work = Work.Idle,
    /** Path of the temporary enhanced WAV, owned by this ViewModel. */
    val enhanced: String? = null,
    val transcript: String? = null,
    val error: UiError? = null,
    val settingsOpen: Boolean = false,
) {
    val busy: Boolean get() = work is Work.Running
}

/**
 * Owns app state and runs audio work through the platform [engine]. Results survive
 * configuration changes; the selected tool, recording, language and settings route
 * survive process death through [SavedStateHandle]. Shared by every platform.
 */
class AudiopsViewModel(
    private val savedState: SavedStateHandle,
    private val engine: AudioEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AudiopsState(
            tool = savedState.get<String>(KEY_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } },
            input = savedState.get<String>(KEY_ID)?.let { AudioInput(it, savedState.get<String>(KEY_NAME).orEmpty()) },
            language = savedState.get<String>(KEY_LANGUAGE)
                ?.let { name -> SpeechLanguage.entries.firstOrNull { it.name == name } }
                ?: SpeechLanguage.AUTO,
            settingsOpen = savedState.get<Boolean>(KEY_SETTINGS) ?: false,
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

    private val _modelStorage = MutableStateFlow<Long?>(null)

    /** Bytes used by downloaded models, or null while being measured. */
    val modelStorage: StateFlow<Long?> = _modelStorage.asStateFlow()

    private var job: Job? = null

    init {
        // Apply the saved default language unless this session already chose one.
        if (savedState.get<String>(KEY_LANGUAGE) == null) {
            viewModelScope.launch {
                val loaded = settings.filterNotNull().first()
                if (savedState.get<String>(KEY_LANGUAGE) == null && !_state.value.busy) {
                    _state.update { it.copy(language = loaded.defaultLanguage) }
                }
            }
        }
    }

    // Navigation

    fun open(tool: Tool) {
        if (_state.value.busy) return
        savedState[KEY_TOOL] = tool.name
        _state.update { it.copy(tool = tool, error = null) }
    }

    fun openSettings() {
        savedState[KEY_SETTINGS] = true
        _state.update { it.copy(settingsOpen = true) }
        refreshModelStorage()
    }

    fun back() {
        if (_state.value.settingsOpen) {
            savedState[KEY_SETTINGS] = false
            _state.update { it.copy(settingsOpen = false) }
            return
        }
        if (_state.value.busy) return
        savedState[KEY_TOOL] = null
        _state.update { it.copy(tool = null, error = null) }
    }

    // Input

    fun chooseInput(id: String, name: String) {
        if (_state.value.busy) return
        discardEnhanced()
        savedState[KEY_ID] = id
        savedState[KEY_NAME] = name
        _state.update { it.copy(input = AudioInput(id, name), transcript = null, error = null) }
    }

    fun setLanguage(language: SpeechLanguage) {
        if (_state.value.busy) return
        savedState[KEY_LANGUAGE] = language.name
        _state.update { it.copy(language = language, transcript = null, error = null) }
    }

    // Work

    fun startEnhance() {
        val input = _state.value.input ?: return
        if (_state.value.busy) return
        if (!engine.supportsEnhance) return fail(UiError.Unsupported)
        discardEnhanced()
        launchWork(Stage.ENHANCING, block = { listener -> engine.enhance(input.id, listener) }) { path ->
            _state.update { it.copy(enhanced = path) }
            _messages.trySend(UiMessage.Enhanced)
        }
    }

    fun startTranscribe() {
        val input = _state.value.input ?: return
        if (_state.value.busy) return
        if (!engine.supportsTranscribe) return fail(UiError.Unsupported)
        val language = _state.value.language
        _state.update { it.copy(transcript = null, error = null) }
        launchWork(Stage.TRANSCRIBING, block = { listener -> engine.transcribe(input.id, language.code, listener) }) { text ->
            if (text.isBlank()) {
                _state.update { it.copy(error = UiError.NoSpeech) }
            } else {
                _state.update { it.copy(transcript = text) }
                _messages.trySend(UiMessage.Transcribed)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun discardEnhanced() {
        _state.value.enhanced?.let(engine::deleteTemporary)
        _state.update { it.copy(enhanced = null) }
    }

    /** Writes the enhanced WAV to [destination], a document chosen by the user. */
    fun saveEnhanced(destination: String?) {
        val path = _state.value.enhanced ?: return
        destination ?: return
        save { engine.copyFile(path, destination) }
    }

    /** Writes the transcript as UTF-8 text to [destination]. */
    fun saveTranscript(destination: String?) {
        val text = _state.value.transcript ?: return
        destination ?: return
        save { engine.writeText(text, destination) }
    }

    private fun save(write: () -> Unit) {
        viewModelScope.launch {
            val result = runCatching { runOnWorker(write) }
            _messages.trySend(
                result.exceptionOrNull()?.let { UiMessage.SaveFailed(it.message) } ?: UiMessage.Saved,
            )
        }
    }

    // Settings

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

    /** Saves the default and applies it to the transcription screen when idle. */
    fun setDefaultLanguage(language: SpeechLanguage) {
        viewModelScope.launch { settingsRepository.setDefaultLanguage(language) }
        setLanguage(language)
    }

    fun refreshModelStorage() {
        viewModelScope.launch {
            _modelStorage.value = runCatching { runOnWorker(engine::modelStorageBytes) }.getOrNull() ?: 0L
        }
    }

    /** Deletes downloaded models. Not allowed while a job may be reading them. */
    fun deleteModels() {
        if (_state.value.busy) return
        viewModelScope.launch {
            runCatching { runOnWorker(engine::deleteModels) }
            _modelStorage.value = runCatching { runOnWorker(engine::modelStorageBytes) }.getOrNull() ?: 0L
            _messages.trySend(UiMessage.ModelsDeleted)
        }
    }

    // Internals

    private fun <T> launchWork(stage: Stage, block: (ProcessingListener) -> T, onResult: (T) -> Unit) {
        job = viewModelScope.launch {
            _state.update { it.copy(work = Work.Running(Stage.PREPARING), error = null) }
            val listener = object : ProcessingListener {
                override fun onModelDownload(index: Int, total: Int) = running(Stage.DOWNLOADING_MODEL, index, total)
                override fun onSection(index: Int, total: Int) = running(stage, index, total)
            }
            try {
                val result = runOnWorker { block(listener) }
                _state.update { it.copy(work = Work.Idle) }
                onResult(result)
            } catch (e: CancellationException) {
                _state.update { it.copy(work = Work.Idle) }
                _messages.trySend(UiMessage.Cancelled)
                throw e
            } catch (e: UnsupportedOnPlatformException) {
                fail(UiError.Unsupported)
            } catch (e: Throwable) {
                if (!e.isRecoverable()) throw e
                fail(if (e.isOutOfMemory()) UiError.OutOfMemory else UiError.Processing(e.message))
            }
        }
    }

    private fun running(stage: Stage, index: Int, total: Int) {
        _state.update { it.copy(work = Work.Running(stage, index, total)) }
    }

    private fun fail(error: UiError) {
        _state.update { it.copy(work = Work.Idle, error = error) }
    }

    override fun onCleared() {
        job?.cancel()
        _state.value.enhanced?.let { runCatching { engine.deleteTemporary(it) } }
        _messages.close()
    }

    private companion object {
        const val KEY_TOOL = "tool"
        const val KEY_ID = "input_id"
        const val KEY_NAME = "input_name"
        const val KEY_LANGUAGE = "language"
        const val KEY_SETTINGS = "settings_open"
    }
}
