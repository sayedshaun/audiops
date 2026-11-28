package com.audiops.app.ui

import com.audiops.app.Job
import com.audiops.app.JobStatus
import com.audiops.app.MeetingDocument
import com.audiops.app.MeetingState
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_cancel
import com.audiops.shared.resources.meeting_finishing
import com.audiops.shared.resources.meeting_preparing
import com.audiops.shared.resources.meeting_speakers
import com.audiops.shared.resources.message_job_failed
import com.audiops.shared.resources.message_job_finished
import com.audiops.shared.resources.notification_cancel_all
import com.audiops.shared.resources.notification_channel_finished
import com.audiops.shared.resources.notification_channel_finished_description
import com.audiops.shared.resources.notification_channel_running
import com.audiops.shared.resources.notification_channel_running_description
import com.audiops.shared.resources.notification_meeting_stop
import com.audiops.shared.resources.notification_meeting_text
import com.audiops.shared.resources.notification_meeting_title
import com.audiops.shared.resources.notification_waiting
import com.audiops.shared.resources.stage_model_download_percent
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** What the ongoing job notification shows: the current job, its progress and the queue. */
data class JobNotice(
    val title: String,
    val text: String,
    /** 0..1, or null while unknown. */
    val fraction: Float?,
    /** "2 more waiting", or null when nothing else is queued. */
    val waiting: String?,
    val cancelLabel: String,
)

/** Notice for the [active] jobs (queue order), or null when there are none. */
suspend fun jobNotice(active: List<Job>): JobNotice? {
    val current = active.firstOrNull { it.status == JobStatus.RUNNING } ?: active.firstOrNull() ?: return null
    val waiting = active.size - 1
    return JobNotice(
        title = getString(current.tool.title),
        text = current.progressLabel(),
        fraction = current.progress?.fraction,
        waiting = if (waiting > 0) getString(Res.string.notification_waiting, waiting) else null,
        cancelLabel = getString(if (waiting > 0) Res.string.notification_cancel_all else Res.string.action_cancel),
    )
}

/** "Enhance speech finished." or "… failed." for a finished job. */
suspend fun Job.resultTitle(): String =
    getString(if (status == JobStatus.DONE) Res.string.message_job_finished else Res.string.message_job_failed, getString(tool.title))

/** Names and descriptions of the Android notification channels. */
data class NotificationChannelText(
    val running: String,
    val runningDescription: String,
    val finished: String,
    val finishedDescription: String,
)

suspend fun notificationChannelText(): NotificationChannelText = NotificationChannelText(
    running = getString(Res.string.notification_channel_running),
    runningDescription = getString(Res.string.notification_channel_running_description),
    finished = getString(Res.string.notification_channel_finished),
    finishedDescription = getString(Res.string.notification_channel_finished_description),
)

/**
 * What the ongoing meeting notification shows. [progress] is the model download (0..1) while one
 * runs, -1 for a bar without a known end (getting ready, sorting out speakers), and null for no bar.
 */
data class MeetingNotice(val title: String, val text: String, val stopLabel: String, val progress: Float? = null)

/** Notice for a meeting in [state], or null when no meeting is starting, running or finishing. */
suspend fun meetingNotice(state: MeetingState): MeetingNotice? {
    val download = (state as? MeetingState.Preparing)?.progress
    val text = when (state) {
        is MeetingState.Preparing -> if (download != null) {
            getString(Res.string.stage_model_download_percent, download.index, download.total, (download.subFraction * 100).toInt())
        } else {
            getString(Res.string.meeting_preparing)
        }

        is MeetingState.Live -> getString(
            Res.string.notification_meeting_text,
            MeetingDocument.clock(state.elapsedMs),
            getPluralString(Res.plurals.meeting_speakers, state.speakers, state.speakers),
        )

        is MeetingState.Finishing -> getString(Res.string.meeting_finishing)

        else -> return null
    }
    val progress = when (state) {
        is MeetingState.Preparing -> download?.fraction ?: -1f
        is MeetingState.Finishing -> -1f
        else -> null
    }
    return MeetingNotice(getString(Res.string.notification_meeting_title), text, getString(Res.string.notification_meeting_stop), progress)
}
