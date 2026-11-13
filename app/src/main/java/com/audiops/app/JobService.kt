package com.audiops.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.audiops.app.ui.JobNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app process (and the bound `:enhance` worker, which
 * inherits its priority) running while jobs are queued or running, so switching apps,
 * turning the screen off or swiping Audiops away from Recents does not stop them. It holds
 * a partial wake lock so the CPU keeps working with the screen off.
 *
 * [JobNotifications] publishes [notice]; the service follows it and stops itself when it
 * becomes null (the queue is empty).
 */
class JobService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Audiops:jobs")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_RUN_MS)
            }
        scope.launch {
            notices.collect { notice ->
                if (notice == null) {
                    stop()
                } else {
                    show(notice)
                    // Android drops notification updates beyond a few per second.
                    delay(UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            cancelAll?.invoke()
            return START_NOT_STICKY
        }
        // startForegroundService requires startForeground soon, even if the queue just emptied.
        val notice = notices.value
        if (notice != null) show(notice) else stop()
        return START_NOT_STICKY
    }

    /** Android 15+ limits these service types to 6 hours a day; stop cleanly when the limit is hit. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground time limit reached; cancelling jobs")
        cancelAll?.invoke()
        stop()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun show(notice: JobNotice) {
        val notification = build(notice)
        if (foreground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            return
        }
        try {
            when {
                Build.VERSION.SDK_INT >= 35 -> startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
                )

                Build.VERSION.SDK_INT >= 29 -> startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

                else -> startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (e: RuntimeException) {
            // For example ForegroundServiceStartNotAllowedException: the jobs still run while the app is open.
            Log.w(TAG, "Could not start the foreground service", e)
            stopSelf()
        }
    }

    private fun stop() {
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun build(notice: JobNotice): Notification {
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, JobService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, JobNotifications.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setSubText(notice.waiting)
            .setContentIntent(JobNotifications.openActivity(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .setProgress(PROGRESS_MAX, ((notice.fraction ?: 0f) * PROGRESS_MAX).toInt(), notice.fraction == null)
            .addAction(Notification.Action.Builder(null, notice.cancelLabel, cancel).build())
            .build()
    }

    companion object {
        private const val TAG = "JobService"
        private const val ACTION_CANCEL_ALL = "com.audiops.app.action.CANCEL_ALL"
        private const val NOTIFICATION_ID = 1
        private const val PROGRESS_MAX = 1000
        private const val UPDATE_INTERVAL_MS = 500L

        /** Matches the 6-hour daily limit for media processing and data sync services. */
        private const val MAX_RUN_MS = 6 * 60 * 60 * 1000L

        private val notices = MutableStateFlow<JobNotice?>(null)

        /** The current notice; null stops the service. */
        internal val notice: StateFlow<JobNotice?> = notices.asStateFlow()

        /** Called by the notification's Cancel action. Set by [JobNotifications]. */
        @Volatile internal var cancelAll: (() -> Unit)? = null

        /** Publishes [notice], starting the service when work begins. Call on the main thread. */
        internal fun publish(context: Context, notice: JobNotice?) {
            val starting = notice != null && notices.value == null
            notices.value = notice
            if (!starting) return
            try {
                context.startForegroundService(Intent(context, JobService::class.java))
            } catch (e: RuntimeException) {
                // Not allowed from the background (Android 12+); jobs still run while the app is open.
                Log.w(TAG, "Could not start the job service", e)
            }
        }
    }
}
