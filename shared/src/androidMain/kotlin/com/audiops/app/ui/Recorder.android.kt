package com.audiops.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.audiops.app.PIPELINE_RATE
import com.audiops.app.WavWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

private const val MIN_RECORDING_MS = 500L
private const val KEEP_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * Records mono 16-bit audio at the pipeline rate straight into a WAV file in the app cache
 * (streaming, so memory use is flat however long it runs). Recordings are kept for a week.
 */
private class AndroidRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onRecorded: () -> ((String, String) -> Unit),
    private val onFailed: () -> ((String?) -> Unit),
) : Recorder {
    override val available: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    override var state by mutableStateOf(RecorderState())
        private set
    private val stopRequested = AtomicBoolean(false)
    private var job: Job? = null

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Called by the permission launcher or when permission is already held. */
    var requestPermission: () -> Unit = {}

    override fun toggle() {
        when {
            state.recording -> stop()
            hasPermission() -> start()
            else -> requestPermission()
        }
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun start() {
        if (job?.isActive == true) return
        stopRequested.set(false)
        val directory = File(context.cacheDir, "recordings").apply { mkdirs() }
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - KEEP_MILLIS }?.forEach { it.delete() }
        val label = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        val file = File(directory, "recording-${label.replace(' ', '_')}.wav")
        state = RecorderState(recording = true)
        job = scope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { record(file) } }
            state = RecorderState()
            outcome.onSuccess { milliseconds ->
                if (milliseconds >= MIN_RECORDING_MS) {
                    onRecorded()(android.net.Uri.fromFile(file).toString(), "Recording $label.wav")
                } else {
                    file.delete()
                }
            }.onFailure {
                file.delete()
                onFailed()(it.message)
            }
        }
    }

    /** Writes microphone audio to [file] until [stop]; returns the recorded length in milliseconds. */
    private fun record(file: File): Long {
        val minimum = AudioRecord.getMinBufferSize(PIPELINE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "This device cannot record audio." }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            PIPELINE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, PIPELINE_RATE / 2) * 2,
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "The microphone is not available." }
            val block = ShortArray(PIPELINE_RATE / 10)
            var samples = 0L
            record.startRecording()
            WavWriter(file, PIPELINE_RATE).use { writer ->
                while (!stopRequested.get()) {
                    val read = record.read(block, 0, block.size)
                    check(read >= 0) { "Recording stopped unexpectedly." }
                    if (read == 0) continue
                    var energy = 0.0
                    val floats = FloatArray(read) { i ->
                        val value = block[i] / 32768f
                        energy += value * value
                        value
                    }
                    writer.append(floats)
                    samples += read
                    // Speech sits well below full scale: a square root and a boost make the meter readable.
                    state = RecorderState(true, samples * 1000 / PIPELINE_RATE, (sqrt(energy / read).toFloat() * 4f).coerceIn(0f, 1f))
                }
            }
            return samples * 1000 / PIPELINE_RATE
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}

@Composable
actual fun rememberRecorder(onRecorded: (id: String, name: String) -> Unit, onFailed: (detail: String?) -> Unit): Recorder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val recorder = remember { AndroidRecorder(context, scope, { recorded }, { failed }) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.start() else failed(null)
    }
    recorder.requestPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    // Android blocks microphone access for apps that are not on screen, so stop and keep what was recorded.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) recorder.stop() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            recorder.stop()
        }
    }
    return recorder
}
