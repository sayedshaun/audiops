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
import com.audiops.app.ui.MeetingNotice
import com.audiops.app.ui.meetingNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service (type microphone) for a live meeting: Android lets an app use the microphone
 * with the screen off or another app open only while such a service runs. It holds a partial wake
 * lock, shows the elapsed time and speakers in an ongoing notification with Stop and save, and stops
 * itself when the meeting ends.
 */
class MeetingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false
    private val meeting get() = (application as AudiopsApplication).meeting

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Audiops:meeting")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_RUN_MS)
            }
        scope.launch {
            meeting.state.collect { state ->
                val notice = meetingNotice(state)
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
        if (intent?.action == ACTION_STOP) {
            meeting.stop()
            return START_NOT_STICKY
        }
        // startForegroundService requires startForeground soon, before the first notice is ready.
        if (!foreground) show(MeetingNotice(getString(R.string.app_name), "", ""))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun show(notice: MeetingNotice) {
        val notification = build(notice)
        if (foreground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (e: RuntimeException) {
            // The meeting still runs while Audiops is on screen.
            Log.w(TAG, "Could not start the meeting service", e)
            stopSelf()
        }
    }

    private fun stop() {
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun build(notice: MeetingNotice): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, MeetingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, JobNotifications.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setContentIntent(JobNotifications.openActivity(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .apply { if (notice.stopLabel.isNotEmpty()) addAction(Notification.Action.Builder(null, notice.stopLabel, stop).build()) }
            .build()
    }

    companion object {
        private const val TAG = "MeetingService"
        private const val ACTION_STOP = "com.audiops.app.action.STOP_MEETING"
        private const val NOTIFICATION_ID = 2
        private const val UPDATE_INTERVAL_MS = 1000L

        /** A long meeting is fine; the lock is released when the meeting ends. */
        private const val MAX_RUN_MS = 12 * 60 * 60 * 1000L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MeetingService::class.java))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not start the meeting service", e)
            }
        }
    }
}
