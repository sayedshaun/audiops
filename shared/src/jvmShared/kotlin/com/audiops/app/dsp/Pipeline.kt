package com.audiops.app.dsp

import com.audiops.app.AudioOperation
import com.audiops.app.AudioReport
import com.audiops.app.PIPELINE_RATE
import com.audiops.app.ProcessingListener
import com.audiops.app.WavWriter
import com.audiops.app.throwIfCancelled
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Raw mono PCM16 files at [PIPELINE_RATE], the intermediate format of every tool. */
object PcmFile {
    private const val BLOCK = 16_384

    fun sampleCount(file: File): Long = file.length() / 2

    fun durationMs(file: File): Long = sampleCount(file) * 1000 / PIPELINE_RATE

    /** Streams samples [from, to) in blocks. */
    fun forEachBlock(file: File, from: Long = 0, to: Long = sampleCount(file), block: (FloatArray) -> Unit) {
        val end = to.coerceAtMost(sampleCount(file))
        if (from >= end) return
        RandomAccessFile(file, "r").use { input ->
            input.seek(from * 2)
            val bytes = ByteArray(BLOCK * 2)
            var position = from
            while (position < end) {
                throwIfCancelled()
                val n = minOf(BLOCK.toLong(), end - position).toInt()
                input.readFully(bytes, 0, n * 2)
                val buffer = ByteBuffer.wrap(bytes, 0, n * 2).order(ByteOrder.LITTLE_ENDIAN)
                block(FloatArray(n) { buffer.short / 32768f })
                position += n
            }
        }
    }

    /** Writes PCM16 to [file]. */
    class Writer(file: File) : AutoCloseable {
        private val output = BufferedOutputStream(file.outputStream(), 1 shl 16)
        private val bytes = ByteArray(BLOCK * 2)

        fun write(samples: FloatArray) {
            var i = 0
            while (i < samples.size) {
                val n = minOf(BLOCK, samples.size - i)
                for (j in 0 until n) {
                    val v = (samples[i + j].coerceIn(-1f, 1f) * 32767f).roundToInt()
                    bytes[2 * j] = v.toByte()
                    bytes[2 * j + 1] = (v shr 8).toByte()
                }
                output.write(bytes, 0, n * 2)
                i += n
            }
        }

        override fun close() = output.close()
    }
}

/** Peak envelope for drawing: the largest |sample| in each of [buckets] equal slices. */
fun waveformPeaks(file: File, buckets: Int): FloatArray {
    val total = PcmFile.sampleCount(file)
    val peaks = FloatArray(buckets)
    if (total == 0L || buckets <= 0) return peaks
    var index = 0L
    PcmFile.forEachBlock(file) { block ->
        for (x in block) {
            val bucket = (index * buckets / total).toInt().coerceAtMost(buckets - 1)
            val a = abs(x)
            if (a > peaks[bucket]) peaks[bucket] = a
            index++
        }
    }
    return peaks
}

/** Measures a decoded recording. */
fun analyze(file: File, listener: ProcessingListener): AudioReport {
    val analyzer = AudioAnalyzer(PIPELINE_RATE)
    val total = PcmFile.sampleCount(file)
    var done = 0L
    PcmFile.forEachBlock(file) { block ->
        analyzer.add(block)
        done += block.size
        listener.onSection(progressStep(done, total), 100)
    }
    return analyzer.report()
}

private fun progressStep(done: Long, total: Long): Int =
    if (total <= 0) 100 else (1 + done * 99 / total).toInt().coerceIn(1, 100)

/**
 * Runs [operations] over a decoded recording and writes a WAV to [output]. Memory use is
 * constant: audio streams through in blocks. Stages split where an operation needs to
 * measure its input first (normalization, noise reduction); [workDir] holds the
 * intermediate files, which are deleted before returning.
 */
object DspPipeline {
    fun run(
        input: File,
        operations: List<AudioOperation>,
        output: File,
        workDir: File,
        listener: ProcessingListener,
    ) {
        val stages = splitStages(operations)
        val outputRate = operations.filterIsInstance<AudioOperation.Resample>().lastOrNull()?.sampleRate ?: PIPELINE_RATE
        var current = input
        val temporaries = mutableListOf<File>()
        try {
            stages.forEachIndexed { index, stage ->
                val last = index == stages.lastIndex
                val progress = StageProgress(listener, index, stages.size)
                if (last) {
                    WavWriter(output, outputRate).use { writer -> runStage(current, stage, progress) { writer.append(it) } }
                } else {
                    val next = File.createTempFile("stage-", ".pcm", workDir).also(temporaries::add)
                    PcmFile.Writer(next).use { writer -> runStage(current, stage, progress) { writer.write(it) } }
                    current = next
                }
            }
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            temporaries.forEach { it.delete() }
        }
    }

    /** Operations grouped so that measuring operations start a new stage. */
    private fun splitStages(operations: List<AudioOperation>): List<List<AudioOperation>> {
        if (operations.isEmpty()) return listOf(emptyList())
        val stages = mutableListOf<MutableList<AudioOperation>>(mutableListOf())
        for (op in operations) {
            val measures = op is AudioOperation.NormalizePeak || op is AudioOperation.NormalizeLoudness ||
                op is AudioOperation.Denoise
            if (measures && stages.last().isNotEmpty()) stages += mutableListOf<AudioOperation>()
            stages.last() += op
        }
        return stages
    }

    private fun runStage(input: File, stage: List<AudioOperation>, progress: StageProgress, sink: (FloatArray) -> Unit) {
        val total = PcmFile.sampleCount(input)
        // Trim selects the range read; it is only meaningful as the first operation of a stage.
        val trim = stage.firstOrNull() as? AudioOperation.Trim
        val from = trim?.let { (it.startMs * PIPELINE_RATE / 1000).coerceIn(0, total) } ?: 0L
        val to = trim?.let { (it.endMs * PIPELINE_RATE / 1000).coerceIn(from, total) } ?: total
        val processors = stage.mapNotNull { op -> processorFor(op, input, from, to, progress) }
        val chain = ProcessorChain(processors)
        var done = 0L
        PcmFile.forEachBlock(input, from, to) { block ->
            sink(chain.process(block))
            done += block.size
            progress.report(done, to - from)
        }
        sink(chain.flush())
    }

    private fun processorFor(
        op: AudioOperation,
        input: File,
        from: Long,
        to: Long,
        progress: StageProgress,
    ): AudioProcessor? = when (op) {
        is AudioOperation.Trim -> null
        // Boosts go through the limiter so the result cannot hard-clip.
        is AudioOperation.Gain -> if (op.db > 0f) {
            ProcessorChain(listOf(GainProcessor(op.db), Limiter(-0.3f, PIPELINE_RATE)))
        } else {
            GainProcessor(op.db)
        }
        is AudioOperation.Fade -> FadeProcessor(
            (op.inMs * PIPELINE_RATE / 1000).toInt(),
            (op.outMs * PIPELINE_RATE / 1000).toInt(),
        )
        is AudioOperation.Equalizer -> ProcessorChain(
            listOf(FilterChain.equalizer(op.preset, PIPELINE_RATE), Limiter(-0.5f, PIPELINE_RATE)),
        )
        is AudioOperation.SpeedPitch -> speedPitchProcessor(op.speed, op.semitones, PIPELINE_RATE)
        is AudioOperation.RemoveSilence -> SilenceRemover(op.thresholdDb, op.minSilenceMs, op.paddingMs, PIPELINE_RATE)
        is AudioOperation.Resample -> if (op.sampleRate == PIPELINE_RATE) null else ProcessorChain(
            listOfNotNull(
                if (op.sampleRate < PIPELINE_RATE) FilterChain.antiAlias(PIPELINE_RATE, op.sampleRate) else null,
                Resampler(PIPELINE_RATE.toDouble() / op.sampleRate),
            ),
        )
        is AudioOperation.NormalizePeak -> {
            progress.measuring()
            val analyzer = AudioAnalyzer(PIPELINE_RATE)
            PcmFile.forEachBlock(input, from, to) { analyzer.add(it) }
            GainProcessor(gainToward(toDb(analyzer.peakLevel), op.peakDb))
        }
        is AudioOperation.NormalizeLoudness -> {
            progress.measuring()
            val analyzer = AudioAnalyzer(PIPELINE_RATE)
            PcmFile.forEachBlock(input, from, to) { analyzer.add(it) }
            val loudness = analyzer.integratedLoudness()
            if (loudness == null) {
                null
            } else {
                ProcessorChain(listOf(GainProcessor(gainToward(loudness, op.target.lufs)), Limiter(-1f, PIPELINE_RATE)))
            }
        }
        is AudioOperation.Denoise -> {
            progress.measuring()
            val profile = NoiseProfile.estimate { consume -> PcmFile.forEachBlock(input, from, to, consume) }
            noiseReducer(profile, op.strength)
        }
    }

    /** Maps per-stage progress to one 1..100 scale across all stages. */
    private class StageProgress(private val listener: ProcessingListener, private val index: Int, private val count: Int) {
        fun measuring() = listener.onSection(1 + index * 99 / count, 100)

        fun report(done: Long, total: Long) {
            val within = if (total <= 0) 1.0 else done.toDouble() / total
            listener.onSection((1 + (index + within) * 99 / count).toInt().coerceIn(1, 100), 100)
        }
    }
}
