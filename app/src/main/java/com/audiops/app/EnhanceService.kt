package com.audiops.app

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val MSG_START = 1
private const val MSG_PID = 10
private const val MSG_DOWNLOAD_START = 11
private const val MSG_DOWNLOAD_PROGRESS = 12
private const val MSG_SECTION = 13
private const val MSG_DONE = 14
private const val MSG_ERROR = 15
private const val KEY_PCM = "pcm"
private const val KEY_OUT = "out"
private const val KEY_FRACTION = "fraction"
private const val KEY_MESSAGE = "message"

/**
 * Runs AI enhancement in its own process (`:enhance`, see the manifest). The vocoder needs
 * up to ~3 GB while it runs; on phones with little free memory Android's low-memory killer
 * stops the process. Isolated, only this worker dies: the app stays open and reports a
 * clear error instead of disappearing.
 */
class EnhanceService : Service() {
    private lateinit var ipc: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        ipc = HandlerThread("enhance-ipc").apply { start() }
        messenger = Messenger(
            Handler(ipc.looper) { message ->
                if (message.what == MSG_START) start(message)
                true
            },
        )
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        ipc.quitSafely()
        super.onDestroy()
    }

    private fun start(request: Message) {
        val reply = request.replyTo ?: return
        val pcm = File(request.data.getString(KEY_PCM) ?: return)
        val output = File(request.data.getString(KEY_OUT) ?: return)
        fun send(what: Int, arg1: Int = 0, arg2: Int = 0, data: Bundle? = null) {
            runCatching { reply.send(Message.obtain(null, what, arg1, arg2).apply { if (data != null) this.data = data }) }
        }
        send(MSG_PID, Process.myPid())
        Thread({
            val listener = object : ProcessingListener {
                override fun onModelDownload(index: Int, total: Int) = send(MSG_DOWNLOAD_START, index, total)
                override fun onModelDownloadProgress(index: Int, total: Int, fileFraction: Float) =
                    send(MSG_DOWNLOAD_PROGRESS, index, total, Bundle().apply { putFloat(KEY_FRACTION, fileFraction) })
                override fun onSection(index: Int, total: Int) = send(MSG_SECTION, index, total)
            }
            try {
                AudioPipelines.enhance(applicationContext, pcm, output, listener)
                send(MSG_DONE)
            } catch (e: Throwable) {
                send(MSG_ERROR, data = Bundle().apply { putString(KEY_MESSAGE, e.message ?: e.javaClass.simpleName) })
            }
        }, "enhance").start()
    }
}

/** Main-process side of [EnhanceService]: a blocking call with progress and cancellation. */
internal object EnhanceClient {
    const val LOW_MEMORY_MESSAGE =
        "The AI model ran out of memory on this device. Close other apps and try again, or use a phone with more RAM."

    /**
     * Enhances decoded [pcm] into [output] in the worker process. Blocks the calling worker
     * thread; cancelling (thread interrupt) stops the worker process immediately.
     */
    fun run(context: Context, pcm: File, output: File, listener: ProcessingListener) {
        val app = context.applicationContext
        val replies = HandlerThread("enhance-client").apply { start() }
        val connected = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var service: Messenger? = null
        var binder: IBinder? = null
        // Written by the reply handler and binder threads, read by the calling worker.
        val workerPid = AtomicInteger(0)
        val done = AtomicBoolean(false)
        val died = AtomicBoolean(false)
        val error = AtomicReference<String?>(null)

        val replyMessenger = Messenger(
            Handler(replies.looper) { m ->
                when (m.what) {
                    MSG_PID -> workerPid.set(m.arg1)
                    MSG_DOWNLOAD_START -> listener.onModelDownload(m.arg1, m.arg2)
                    MSG_DOWNLOAD_PROGRESS -> listener.onModelDownloadProgress(m.arg1, m.arg2, m.data.getFloat(KEY_FRACTION))
                    MSG_SECTION -> listener.onSection(m.arg1, m.arg2)
                    MSG_DONE -> {
                        done.set(true)
                        finished.countDown()
                    }
                    MSG_ERROR -> {
                        error.set(m.data.getString(KEY_MESSAGE))
                        finished.countDown()
                    }
                }
                true
            },
        )
        val death = IBinder.DeathRecipient {
            died.set(true)
            finished.countDown()
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, b: IBinder) {
                binder = b
                runCatching { b.linkToDeath(death, 0) }.onFailure { died.set(true); finished.countDown() }
                service = Messenger(b)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                died.set(true)
                finished.countDown()
            }
        }

        check(app.bindService(Intent(app, EnhanceService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            "Could not start the enhancement service."
        }
        try {
            while (!connected.await(200, TimeUnit.MILLISECONDS)) throwIfCancelled()
            service!!.send(
                Message.obtain(null, MSG_START).apply {
                    replyTo = replyMessenger
                    data = Bundle().apply {
                        putString(KEY_PCM, pcm.absolutePath)
                        putString(KEY_OUT, output.absolutePath)
                    }
                },
            )
            while (!finished.await(200, TimeUnit.MILLISECONDS)) throwIfCancelled()
            when {
                done.get() -> return
                died.get() -> throw IllegalStateException(LOW_MEMORY_MESSAGE)
                else -> throw IllegalStateException(error.get() ?: "Enhancement failed.")
            }
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            binder?.let { runCatching { it.unlinkToDeath(death, 0) } }
            runCatching { app.unbindService(connection) }
            // Always stop the worker: it frees all native model memory at once, and on
            // cancellation it stops inference immediately.
            val pid = workerPid.get()
            if (pid != 0 && pid != Process.myPid()) Process.killProcess(pid)
            replies.quitSafely()
        }
    }
}
