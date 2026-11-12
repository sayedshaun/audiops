package com.audiops.app

import com.audiops.app.dsp.DspPipeline
import com.audiops.app.dsp.PcmFile
import com.audiops.app.dsp.analyze as analyzePcm
import com.audiops.app.dsp.waveformPeaks
import java.io.File

/**
 * Engine logic shared by Android and desktop: decoded-audio cache, DSP tools, analysis,
 * waveform and storage. Platforms supply decoding (and, where available, AI models).
 *
 * @param workDir temporary files (decoded audio, pipeline stages); safe to clear.
 * @param outputDir processed files kept for the Activity tab.
 * @param modelRoot downloaded models.
 */
abstract class JvmAudioEngine(
    protected val workDir: File,
    protected val outputDir: File,
    protected val modelRoot: File,
) : AudioEngine {

    /** Decodes [source] to a new mono 44.1 kHz PCM16 file inside [workDir]. */
    protected abstract fun decode(source: String): File

    private class Entry(val source: String, val file: File) {
        var users = 0
        var lastUsed = System.nanoTime()
    }

    private val lock = Any()
    private val cache = mutableListOf<Entry>()

    init {
        workDir.mkdirs()
        outputDir.mkdirs()
        // Leftovers from a previous run that ended abruptly.
        workDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Runs [block] with the decoded audio of [source]. Decoding is cached (the waveform
     * preview warms it for processing); entries in use are never evicted.
     */
    protected fun <T> withDecoded(source: String, block: (File) -> T): T {
        val existing = synchronized(lock) {
            cache.firstOrNull { it.source == source && it.file.exists() }?.also { it.users++ }
        }
        val entry = existing ?: run {
            val file = decode(source)   // slow: outside the lock
            synchronized(lock) { Entry(source, file).also { it.users++; cache += it } }
        }
        try {
            return block(entry.file)
        } finally {
            synchronized(lock) {
                entry.users--
                entry.lastUsed = System.nanoTime()
                evict()
            }
        }
    }

    private fun evict() {
        while (cache.size > MAX_CACHED) {
            val victim = cache.filter { it.users == 0 }.minByOrNull { it.lastUsed } ?: return
            cache.remove(victim)
            victim.file.delete()
        }
    }

    /** A new, empty file in [outputDir] for a result. */
    protected fun newOutput(extension: String): File = File.createTempFile("audiops-", ".$extension", outputDir)

    /** Stores a transcript as a text output and returns its path. */
    protected fun storeTranscript(text: String): String = newOutput("txt").apply { writeText(text, Charsets.UTF_8) }.absolutePath

    override fun process(source: String, operations: List<AudioOperation>, listener: ProcessingListener): String =
        withDecoded(source) { pcm ->
            val output = newOutput("wav")
            DspPipeline.run(pcm, operations, output, workDir, listener)
            output.absolutePath
        }

    override fun analyze(source: String, listener: ProcessingListener): AudioReport =
        withDecoded(source) { pcm -> analyzePcm(pcm, listener) }

    override fun waveform(source: String, buckets: Int): WaveformData =
        withDecoded(source) { pcm -> WaveformData(waveformPeaks(pcm, buckets), PcmFile.durationMs(pcm)) }

    override fun readText(path: String): String = File(path).readText(Charsets.UTF_8)

    override fun deleteFile(path: String) {
        File(path).delete()
    }

    override fun modelStorageBytes(): Long = ModelFiles.storageBytes(modelRoot)

    override fun deleteModels() = ModelFiles.deleteAll(modelRoot)

    override fun outputStorageBytes(): Long = outputDir.listFiles()?.sumOf { it.length() } ?: 0L

    private companion object {
        const val MAX_CACHED = 3
    }
}
