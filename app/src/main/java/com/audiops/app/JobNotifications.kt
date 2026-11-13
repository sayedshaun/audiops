package com.audiops.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.audiops.app.ui.jobNotice
import com.audiops.app.ui.notificationChannelText
import com.audiops.app.ui.resultTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Turns the job queue into Android notifications: an ongoing progress notification from
 * [JobService] while jobs are active, and a "finished" or "failed" notification for jobs
 * that end while no Audiops screen is visible.
 */
internal class JobNotifications(private val context: Context, private val isAppVisible: () -> Boolean) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun watch(scope: CoroutineScope, state: StateFlow<AudiopsState>, cancelAll: () -> Unit) {
        JobService.cancelAll = cancelAll
        scope.launch {
            createChannels()
            var previouslyActive = emptySet<String>()
            state.map { it.jobs }.distinctUntilChanged().collect { jobs ->
                val active = jobs.filter { it.status.active }
                JobService.publish(context, jobNotice(active))
                val ended = previouslyActive - active.map { it.id }.toSet()
                previouslyActive = active.map { it.id }.toSet()
                if (ended.isNotEmpty() && !isAppVisible()) {
                    jobs.filter { it.id in ended && (it.status == JobStatus.DONE || it.status == JobStatus.FAILED) }
                        .forEach { postResult(it) }
                }
            }
        }
    }

    private suspend fun postResult(job: Job) {
        if (!canPost()) return
        val done = job.status == JobStatus.DONE
        val notification = Notification.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(job.resultTitle())
            .setContentText(job.inputName)
            .setContentIntent(openActivity(context))
            .setAutoCancel(true)
            .setCategory(if (done) Notification.CATEGORY_STATUS else Notification.CATEGORY_ERROR)
            .build()
        manager.notify(job.id.hashCode(), notification)
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private suspend fun createChannels() {
        val text = notificationChannelText()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, text.running, NotificationManager.IMPORTANCE_LOW).apply {
                    description = text.runningDescription
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_FINISHED, text.finished, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = text.finishedDescription
                },
            ),
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "jobs_running"
        const val CHANNEL_FINISHED = "jobs_finished"

        /** Extra on the launch intent: show the Activity tab. */
        const val EXTRA_SHOW_ACTIVITY = "com.audiops.app.extra.SHOW_ACTIVITY"

        /** Opens the app on the Activity tab, reusing the open screen if there is one. */
        fun openActivity(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_ACTIVITY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
