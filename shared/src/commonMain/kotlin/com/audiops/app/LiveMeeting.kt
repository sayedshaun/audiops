package com.audiops.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One utterance of a meeting: [speaker] (0-based) said [text] from [startMs] to [endMs]. */
data class MeetingLine(val startMs: Long, val endMs: Long, val speaker: Int, val text: String)

/** The files and facts of a finished meeting, which the ViewModel turns into a history entry. */
data class MeetingResult(
    val name: String,
    val transcriptPath: String,
    val subtitlesPath: String?,
    val audioPath: String,
    val startedAt: Long,
    val durationMs: Long,
    val speakers: Int,
    val model: TranscriptionModel,
    val preview: String,
)

/** Where a live meeting is. */
sealed interface MeetingState {
    data object Idle : MeetingState

    /** Models are loading or downloading; [progress] is the download when one runs. */
    data class Preparing(val progress: JobProgress?) : MeetingState

    /**
     * Listening. [lines] are final; [partial] is what the current speaker has said so far (it changes
     * until they pause). [level] is the microphone level, 0..1.
     */
    data class Live(val elapsedMs: Long, val level: Float, val lines: List<MeetingLine>, val partial: String?, val speakers: Int) :
        MeetingState

    /** Stopped; the last words are being transcribed and speakers sorted out. */
    data class Finishing(val lines: List<MeetingLine>) : MeetingState

    data class Finished(val result: MeetingResult) : MeetingState

    data class Failed(val kind: FailureKind, val detail: String?) : MeetingState
}

/**
 * Live transcription of a meeting from the microphone, with speakers told apart. The platform
 * runs it in the background (the screen can be off); shared code only starts, stops and watches it.
 */
interface LiveMeeting {
    val available: Boolean
    val state: StateFlow<MeetingState>

    /**
     * Starts listening, transcribing with [model] ([language] is a Whisper code, used only by Whisper).
     * [speakerName] names a speaker in the saved transcript (1-based). [name] names the meeting;
     * `%1$s` in it is replaced by the date and time it started.
     */
    fun start(model: TranscriptionModel, language: String, name: String, speakerName: (Int) -> String)

    /** Stops listening and finishes the transcript (the state becomes [MeetingState.Finished]). */
    fun stop()

    /** Stops and throws away everything recorded so far. */
    fun discard()

    /** Returns a [MeetingState.Finished] or [MeetingState.Failed] state to [MeetingState.Idle]. */
    fun acknowledge()

    /** For platforms (and tests) without live meetings. */
    object Unavailable : LiveMeeting {
        override val available: Boolean = false
        override val state: StateFlow<MeetingState> = MutableStateFlow<MeetingState>(MeetingState.Idle).asStateFlow()

        override fun start(model: TranscriptionModel, language: String, name: String, speakerName: (Int) -> String) = Unit

        override fun stop() = Unit

        override fun discard() = Unit

        override fun acknowledge() = Unit
    }
}
