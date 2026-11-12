package com.audiops.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

enum class Tool { ENHANCE, TRANSCRIBE }

enum class SpeechLanguage(val code: String, @StringRes val label: Int) {
    AUTO("", R.string.language_auto),
    BANGLA("bn", R.string.language_bangla),
    ENGLISH("en", R.string.language_english),
    HINDI("hi", R.string.language_hindi),
}

data class AudioInput(val uri: String, val name: String)

sealed interface Work {
    data object Idle : Work

    /** [progress] is null while the amount of work is unknown. */
    data class Running(val status: String, val progress: Float?) : Work
}

data class AudioOpsState(
    val tool: Tool? = null,
    val input: AudioInput? = null,
    val language: SpeechLanguage = SpeechLanguage.AUTO,
    val work: Work = Work.Idle,
    val enhanced: File? = null,
    val transcript: String? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = work is Work.Running
}

/**
 * Owns the app state and runs the audio pipelines. Results survive rotation through the
 * ViewModel; the selected tool, recording and language survive process death through
 * [SavedStateHandle].
 */
class AudioOpsViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        AudioOpsState(
            tool = savedState.get<String>(KEY_TOOL)?.let(Tool::valueOf),
            input = savedState.get<String>(KEY_URI)?.let { AudioInput(it, savedState.get<String>(KEY_NAME).orEmpty()) },
            language = savedState.get<String>(KEY_LANGUAGE)?.let(SpeechLanguage::valueOf) ?: SpeechLanguage.AUTO,
        ),
    )
    val state: StateFlow<AudioOpsState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-shot messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var job: Job? = null

    private val app: Application get() = getApplication()

    fun open(tool: Tool) {
        if (_state.value.busy) return
        savedState[KEY_TOOL] = tool.name
        _state.update { it.copy(tool = tool, error = null) }
    }

    fun back() {
        if (_state.value.busy) return
        savedState[KEY_TOOL] = null
        _state.update { it.copy(tool = null, error = null) }
    }

    fun chooseInput(uri: Uri, name: String) {
        if (_state.value.busy) return
        discardEnhanced()
        savedState[KEY_URI] = uri.toString()
        savedState[KEY_NAME] = name
        _state.update {
            it.copy(input = AudioInput(uri.toString(), name), transcript = null, error = null)
        }
    }

    fun setLanguage(language: SpeechLanguage) {
        if (_state.value.busy) return
        savedState[KEY_LANGUAGE] = language.name
        _state.update { it.copy(language = language, transcript = null, error = null) }
    }

    fun startEnhance() {
        val input = _state.value.input ?: return
        if (_state.value.busy) return
        discardEnhanced()
        launchWork(
            initialStatus = app.getString(R.string.stage_preparing),
            block = { listener -> AudioPipelines.enhance(app, input.uri.toUri(), listener) },
        ) { file ->
            _state.update { it.copy(enhanced = file) }
            _messages.trySend(app.getString(R.string.message_enhanced))
        }
    }

    fun startTranscribe() {
        val input = _state.value.input ?: return
        if (_state.value.busy) return
        val language = _state.value.language
        _state.update { it.copy(transcript = null, error = null) }
        launchWork(
            initialStatus = app.getString(R.string.stage_preparing),
            block = { listener -> AudioPipelines.transcribe(app, input.uri.toUri(), language.code, listener) },
        ) { text ->
            if (text.isBlank()) {
                _state.update { it.copy(error = app.getString(R.string.error_no_speech)) }
            } else {
                _state.update { it.copy(transcript = text) }
                _messages.trySend(app.getString(R.string.message_transcribed))
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun discardEnhanced() {
        _state.value.enhanced?.delete()
        _state.update { it.copy(enhanced = null) }
    }

    /** Writes the enhanced WAV to [destination], a document URI chosen by the user. */
    fun saveEnhanced(destination: Uri?) {
        val source = _state.value.enhanced ?: return
        destination ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(destination)
                        ?.use { out -> source.inputStream().use { it.copyTo(out) } }
                        ?: error("Could not open the destination.")
                }
            }
            reportSave(result.exceptionOrNull())
        }
    }

    /** Writes the transcript as UTF-8 text to [destination]. */
    fun saveTranscript(destination: Uri?) {
        val text = _state.value.transcript ?: return
        destination ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(destination)
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(text) }
                        ?: error("Could not open the destination.")
                }
            }
            reportSave(result.exceptionOrNull())
        }
    }

    private fun reportSave(error: Throwable?) {
        _messages.trySend(
            if (error == null) {
                app.getString(R.string.message_saved)
            } else {
                app.getString(R.string.error_save, error.localizedMessage ?: app.getString(R.string.error_unknown))
            },
        )
    }

    private fun <T> launchWork(
        initialStatus: String,
        block: (ProcessingListener) -> T,
        onResult: (T) -> Unit,
    ) {
        job = viewModelScope.launch {
            _state.update { it.copy(work = Work.Running(initialStatus, null), error = null) }
            val listener = object : ProcessingListener {
                override fun onModelDownload(index: Int, total: Int) = setProgress(
                    app.getString(R.string.stage_model_download, index, total),
                    (index - 1).toFloat() / total,
                )

                override fun onSection(index: Int, total: Int) = setProgress(
                    app.getString(sectionStage(), index, total),
                    (index - 1).toFloat() / total,
                )
            }
            try {
                // runInterruptible interrupts the worker thread on cancellation, which the
                // pipelines check between units of work.
                val result = runInterruptible(Dispatchers.IO) { block(listener) }
                _state.update { it.copy(work = Work.Idle) }
                onResult(result)
            } catch (e: CancellationException) {
                _state.update { it.copy(work = Work.Idle) }
                _messages.trySend(app.getString(R.string.message_cancelled))
                throw e
            } catch (e: OutOfMemoryError) {
                fail(e)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun sectionStage(): Int =
        if (_state.value.tool == Tool.TRANSCRIBE) R.string.stage_transcribe_section else R.string.stage_enhance_section

    private fun setProgress(status: String, fraction: Float) {
        _state.update { it.copy(work = Work.Running(status, fraction.coerceIn(0f, 1f))) }
    }

    private fun fail(e: Throwable) {
        val detail = if (e is OutOfMemoryError) {
            app.getString(R.string.error_out_of_memory)
        } else {
            app.getString(R.string.error_processing_failed, e.localizedMessage ?: app.getString(R.string.error_unknown))
        }
        _state.update { it.copy(work = Work.Idle, error = detail) }
    }

    override fun onCleared() {
        job?.cancel()
        _state.value.enhanced?.delete()
        _messages.close()
    }

    private companion object {
        const val KEY_TOOL = "tool"
        const val KEY_URI = "input_uri"
        const val KEY_NAME = "input_name"
        const val KEY_LANGUAGE = "language"
    }
}

/** The display name of a document URI, falling back to a generic label. */
internal fun Context.queryDisplayName(uri: Uri): String {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: getString(R.string.recording_fallback_name)
}
