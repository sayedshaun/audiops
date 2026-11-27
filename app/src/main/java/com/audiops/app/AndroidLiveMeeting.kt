package com.audiops.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.Vad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Live meeting transcription on Android.
 *
 * A capture thread reads the microphone at 16 kHz, writes it to a WAV file and feeds Silero VAD,
 * which cuts it into utterances. A worker thread transcribes each utterance with the model chosen
 * in Settings, tells the speaker from a voice embedding ([MeetingTranscriber]) and, when it has
 * nothing else to do, transcribes the utterance still in progress so words appear while people
 * talk. [MeetingService] keeps the process and microphone alive with the screen off.
 */
class AndroidLiveMeeting(context: Context) : LiveMeeting {
    private val context = context.applicationContext
    private val _state = MutableStateFlow<MeetingState>(MeetingState.Idle)

    override val available: Boolean = this.context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    override val state: StateFlow<MeetingState> = _state.asStateFlow()

    @Volatile private var session: Session? = null

    @Synchronized
    override fun start(model: TranscriptionModel, language: String, name: String, speakerName: (Int) -> String) {
        if (session != null) return
        _state.value = MeetingState.Preparing(null)
        session = Session(model, language, name, speakerName).also { it.start() }
        MeetingService.start(context)
    }

    override fun stop() {
        session?.stop()
    }

    override fun discard() {
        session?.discard()
    }

    @Synchronized
    override fun acknowledge() {
        val current = _state.value
        if (session == null && (current is MeetingState.Finished || current is MeetingState.Failed)) _state.value = MeetingState.Idle
    }

    @Synchronized
    private fun ended(session: Session, outcome: MeetingState) {
        if (this.session === session) this.session = null
        _state.value = outcome
    }

    private class Segment(val start: Long, val samples: FloatArray)

    private class PartialRequest(val epoch: Int, val samples: FloatArray)

    private inner class Session(
        private val model: TranscriptionModel,
        private val language: String,
        private val name: String,
        private val speakerName: (Int) -> String,
    ) {
        private val thread = Thread(::run, "meeting")
        private val stopRequested = AtomicBoolean(false)
        private val discarded = AtomicBoolean(false)
        private val segments = LinkedBlockingQueue<Segment>()
        private val partialRequest = AtomicReference<PartialRequest?>(null)

        // Live view, written by both threads under this session's lock.
        private var elapsedMs = 0L
        private var level = 0f
        private var lines: List<MeetingLine> = emptyList()
        private var partial: String? = null
        private var speakers = 0

        @Volatile private var epoch = 0

        @Volatile private var workerError: Throwable? = null

        fun start() = thread.start()

        fun stop() = stopRequested.set(true)

        fun discard() {
            discarded.set(true)
            stopRequested.set(true)
            // Interrupting stops a model download; capture notices the flag within 100 ms.
            thread.interrupt()
        }

        private fun publish() {
            if (discarded.get()) return
            _state.value = synchronized(this) { MeetingState.Live(elapsedMs, level, lines, partial, speakers) }
        }

        private fun run() {
            val startedAt = System.currentTimeMillis()
            val outputs = File(context.filesDir, "outputs").apply { mkdirs() }
            val audio = File(outputs, "meeting-$startedAt.wav")
            var vad: Vad? = null
            var extractor: SpeakerEmbeddingExtractor? = null
            var recognizer: OfflineRecognizer? = null
            val outcome = try {
                val listener = object : ProcessingListener {
                    override fun onModelDownload(index: Int, total: Int) {
                        _state.value = MeetingState.Preparing(JobProgress(Stage.DOWNLOADING_MODEL, index, total))
                    }

                    override fun onModelDownloadProgress(index: Int, total: Int, fileFraction: Float) {
                        // Once the last file is in, the model loads: show "Getting ready" again.
                        val done = index == total && fileFraction >= 1f
                        _state.value =
                            MeetingState.Preparing(if (done) null else JobProgress(Stage.DOWNLOADING_MODEL, index, total, fileFraction))
                    }
                }
                vad = MeetingModels.vad(context, listener)
                throwIfCancelled()
                extractor = MeetingModels.speakerExtractor(context, listener)
                throwIfCancelled()
                recognizer = AudioPipelines.recognizer(context, model, language, listener)
                if (stopRequested.get()) {
                    MeetingState.Idle
                } else {
                    record(vad, extractor, recognizer, audio, startedAt)
                }
            } catch (e: Throwable) {
                audio.delete()
                when {
                    discarded.get() || e is InterruptedException -> MeetingState.Idle

                    e is OutOfMemoryError -> MeetingState.Failed(FailureKind.OUT_OF_MEMORY, null)

                    else -> {
                        Log.w(TAG, "Meeting failed", e)
                        MeetingState.Failed(FailureKind.PROCESSING, e.message)
                    }
                }
            } finally {
                runCatching { recognizer?.release() }
                runCatching { extractor?.release() }
                runCatching { vad?.release() }
            }
            ended(this, if (discarded.get()) MeetingState.Idle else outcome)
        }

        private fun record(
            vad: Vad,
            extractor: SpeakerEmbeddingExtractor,
            recognizer: OfflineRecognizer,
            audio: File,
            startedAt: Long,
        ): MeetingState {
            val transcriber = MeetingTranscriber(
                recognizer = { samples -> recognizeUtterance(recognizer, samples) },
                embedder = { samples -> embedUtterance(extractor, samples) },
            )
            publish()
            val worker = Thread({ work(transcriber) }, "meeting-worker").apply { start() }
            try {
                capture(vad, audio)
            } finally {
                segments.put(END)
                worker.join()
            }
            workerError?.let { throw it }
            if (discarded.get()) {
                audio.delete()
                return MeetingState.Idle
            }
            _state.value = MeetingState.Finishing(synchronized(this) { lines })
            val final = transcriber.finish()
            if (final.isEmpty()) {
                audio.delete()
                return MeetingState.Failed(FailureKind.NO_SPEECH, null)
            }
            val lines = if (!model.punctuates) SpeechSentences.punctuate(final, language, MeetingModels.MAX_SPEECH_MS) else final
            val speakersFile = SpeakerTranscript.of(lines, transcriber.voices(final), VoiceBook(File(context.filesDir, VOICES_FILE)))
            val document = speakersFile.transcript(speakerName)
            val transcript = File(audio.parentFile, audio.nameWithoutExtension + ".txt").apply { writeText(document.text, Charsets.UTF_8) }
            val subtitles = File(audio.parentFile, audio.nameWithoutExtension + ".srt")
                .apply { writeText(Subtitles.srt(document.cues), Charsets.UTF_8) }
            val speakers = SpeakerTranscript.fileFor(transcript.absolutePath).apply { speakersFile.write(this) }
            val label = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date(startedAt))
            return MeetingState.Finished(
                MeetingResult(
                    name = name.replace("%1\$s", label),
                    transcriptPath = transcript.absolutePath,
                    subtitlesPath = subtitles.absolutePath,
                    audioPath = audio.absolutePath,
                    speakersPath = speakers.absolutePath,
                    startedAt = startedAt,
                    durationMs = synchronized(this) { elapsedMs },
                    speakers = final.map { it.speaker }.distinct().size,
                    model = model,
                    preview = lines.joinToString(" ") { it.text },
                ),
            )
        }

        /** Reads audio until [stop], writing it to [file] and handing utterances to the worker. */
        private fun capture(vad: Vad, file: File) {
            openSource().use { source ->
                WavWriter(file, MEETING_RATE).use { writer ->
                    val block = FloatArray(MEETING_RATE / 10)
                    val speech = SpeechTail()
                    val padding = UtterancePadding()
                    var samples = 0L
                    while (!stopRequested.get()) {
                        val read = source.read(block)
                        if (read < 0) break
                        if (read == 0) continue
                        val chunk = block.copyOf(read)
                        writer.append(chunk)
                        vad.acceptWaveform(chunk)
                        if (drain(vad, chunk, padding)) speech.reset()
                        if (vad.isSpeechDetected()) {
                            speech.append(chunk)?.let { partialRequest.set(PartialRequest(epoch, it)) }
                        } else if (speech.active) {
                            speech.reset()
                            endUtterance()
                        }
                        speech.remember(chunk)
                        samples += read
                        synchronized(this) {
                            elapsedMs = samples * 1000 / MEETING_RATE
                            level = (rms(chunk) * 4f).coerceIn(0f, 1f)
                        }
                        publish()
                    }
                    vad.flush()
                    drain(vad, null, padding)
                }
            }
        }

        /**
         * Hands finished utterances to the worker, widened by [padding] once the audio after them has
         * arrived; [chunk] is the audio just read, or null at the end. True when the VAD ended any.
         */
        private fun drain(vad: Vad, chunk: FloatArray?, padding: UtterancePadding): Boolean {
            val found = mutableListOf<Utterance>()
            while (!vad.empty()) {
                val segment = vad.front()
                found += Utterance(segment.start.toLong(), segment.samples)
                vad.pop()
            }
            val ready = if (chunk != null) padding.add(chunk, found) else padding.flush(found)
            ready.forEach { segments.put(Segment(it.startSample, it.samples)) }
            if (found.isNotEmpty()) endUtterance()
            return found.isNotEmpty()
        }

        /** The utterance in progress ended: its partial text is about to be replaced by the final line. */
        private fun endUtterance() {
            epoch++
            partialRequest.set(null)
        }

        private fun work(transcriber: MeetingTranscriber) {
            try {
                while (!discarded.get()) {
                    val segment = segments.poll(100, TimeUnit.MILLISECONDS)
                    if (segment === END) break
                    if (segment != null) {
                        val line = transcriber.add(segment.start, segment.samples)
                        synchronized(this) {
                            if (line != null) lines = lines + line
                            speakers = transcriber.speakers
                            partial = null
                        }
                        publish()
                        continue
                    }
                    // Nothing final to do: show what the current speaker has said so far.
                    val request = partialRequest.getAndSet(null) ?: continue
                    if (request.epoch != epoch) continue
                    val text = transcriber.partial(request.samples)
                    synchronized(this) { if (request.epoch == epoch && text.isNotEmpty()) partial = text }
                    publish()
                }
            } catch (e: Throwable) {
                workerError = e
                stopRequested.set(true)
            }
        }
    }

    /**
     * The audio of the utterance in progress, for partial transcripts: it starts a little before
     * the VAD noticed speech (the VAD needs some speech to be sure), and asks for a new partial
     * after every [PARTIAL_STEP] of new speech.
     */
    private class SpeechTail {
        private val lead = FloatArray(LEAD)
        private var leadSize = 0
        private var buffer = FloatArray(0)
        private var size = 0
        private var requested = 0

        val active: Boolean get() = size > 0

        fun remember(chunk: FloatArray) {
            val keep = minOf(LEAD, leadSize + chunk.size)
            val combined = FloatArray(leadSize + chunk.size)
            System.arraycopy(lead, 0, combined, 0, leadSize)
            System.arraycopy(chunk, 0, combined, leadSize, chunk.size)
            System.arraycopy(combined, combined.size - keep, lead, 0, keep)
            leadSize = keep
        }

        /** Adds speech; returns the audio so far when a new partial transcript is due. */
        fun append(chunk: FloatArray): FloatArray? {
            if (size == 0) {
                ensure(leadSize + chunk.size)
                System.arraycopy(lead, 0, buffer, 0, leadSize)
                size = leadSize
            }
            if (size + chunk.size > MAX) return null
            ensure(size + chunk.size)
            System.arraycopy(chunk, 0, buffer, size, chunk.size)
            size += chunk.size
            if (size - requested < PARTIAL_STEP) return null
            requested = size
            return buffer.copyOf(size)
        }

        fun reset() {
            size = 0
            requested = 0
            leadSize = 0
        }

        private fun ensure(capacity: Int) {
            if (buffer.size < capacity) buffer = buffer.copyOf(maxOf(capacity, buffer.size * 2))
        }

        companion object {
            const val LEAD = MEETING_RATE * 4 / 10
            const val PARTIAL_STEP = MEETING_RATE * 8 / 10

            /** Partials stop growing here; the VAD cuts the utterance at 12 s anyway. */
            const val MAX = MEETING_RATE * 13
        }
    }

    private interface AudioSource : Closeable {
        /** Fills [into]; returns the samples read, or -1 at the end. */
        fun read(into: FloatArray): Int
    }

    /** The microphone. [MediaRecorder.AudioSource.MIC] keeps the device's gain control, which helps with people across a table. */
    @SuppressLint("MissingPermission")
    private class MicSource : AudioSource {
        private val shorts = ShortArray(MEETING_RATE / 10)
        private val record: AudioRecord

        init {
            val minimum = AudioRecord.getMinBufferSize(MEETING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "This device cannot record audio." }
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                MEETING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, MEETING_RATE) * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("The microphone is not available.")
            }
            record.startRecording()
        }

        override fun read(into: FloatArray): Int {
            val read = record.read(shorts, 0, minOf(shorts.size, into.size))
            check(read >= 0) { "Recording stopped unexpectedly." }
            for (i in 0 until read) into[i] = shorts[i] / 32768f
            return read
        }

        override fun close() {
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * A 16 kHz mono PCM16 WAV played in real time instead of the microphone. Debug builds only,
     * for testing on emulators, which have no useful microphone.
     */
    private class FileSource(file: File) : AudioSource {
        private val input = RandomAccessFile(file, "r")
        private val bytes = ByteArray(MEETING_RATE / 10 * 2)
        private val startNanos = System.nanoTime()
        private var delivered = 0L

        init {
            input.seek(dataOffset())
        }

        private fun dataOffset(): Long {
            var position = 12L
            val header = ByteArray(8)
            while (position + 8 <= input.length()) {
                input.seek(position)
                input.readFully(header)
                val size = (header[4].toLong() and 0xff) or ((header[5].toLong() and 0xff) shl 8) or
                    ((header[6].toLong() and 0xff) shl 16) or ((header[7].toLong() and 0xff) shl 24)
                if (String(header, 0, 4, Charsets.US_ASCII) == "data") return position + 8
                position += 8 + size + (size and 1)
            }
            error("Not a WAV file.")
        }

        override fun read(into: FloatArray): Int {
            // Keep to real time, like a microphone.
            val due = startNanos + delivered * 1_000_000_000L / MEETING_RATE
            val wait = (due - System.nanoTime()) / 1_000_000
            if (wait > 0) Thread.sleep(wait)
            val count = input.read(bytes, 0, minOf(bytes.size, into.size * 2))
            if (count <= 0) return -1
            val samples = count / 2
            for (i in 0 until samples) {
                into[i] = ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
            }
            delivered += samples
            return samples
        }

        override fun close() = input.close()
    }

    private fun openSource(): AudioSource {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val test = context.getExternalFilesDir(null)?.let { File(it, TEST_FILE) }
        return if (debuggable && test?.isFile == true) FileSource(test) else MicSource()
    }

    private companion object {
        const val TAG = "LiveMeeting"

        /** Remembered voices (the engine's [VoiceBook] file). */
        const val VOICES_FILE = "voices.json"

        /** Debug builds play this file (in the app's external files folder) instead of the microphone. */
        const val TEST_FILE = "meeting-test.wav"

        val END = Segment(-1, FloatArray(0))

        fun rms(chunk: FloatArray): Float {
            var energy = 0.0
            for (value in chunk) energy += value * value
            return sqrt(energy / chunk.size).toFloat()
        }
    }
}
