package com.audiops.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.audiops.app.dsp.ScnetSeparator
import com.audiops.app.dsp.SpectrumNetwork
import com.audiops.app.dsp.Stem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Music source separation with SCNet (int8 ONNX, 12.9 MB) through ONNX Runtime. The ONNX file is
 * the audiojs export of the MIT-licensed SCNet checkpoint; it is pinned to one Hugging Face commit
 * and verified by SHA-256. It runs in the isolated worker process (see [EnhanceService]): one
 * 11-second segment needs about 2 GB of memory.
 */
internal object MusicSeparation {
    private const val REVISION = "e71bcdb42bce1d62b7fae25ba90fcbcf90f73f46"
    private const val URL = "https://huggingface.co/audiojs/scnet/resolve/$REVISION"
    private const val MODEL = "scnet.int8.onnx"
    private const val DIGEST = "98228931494151762a1c4ab1ec7899a894b1f81fd4a509921a9d2f9bacc50845"

    /** The whole song is held in memory as floats (4 bytes a sample), so very long audio is refused. */
    private const val MAX_SECONDS = 12 * 60

    /** Distance between segment starts as a fraction of a segment: half the work of the reference 0.25. */
    private const val STEP = 0.5f

    /**
     * Separates decoded [audio] (mono 44.1 kHz PCM16) into one WAV per [SeparatedStem], written to
     * [outputs] in that order. On failure or cancellation every output is deleted.
     */
    fun separate(context: Context, audio: File, outputs: List<File>, listener: ProcessingListener) {
        require(outputs.size == SeparatedStem.entries.size) { "One output file per stem is needed." }
        try {
            val seconds = audio.length() / 2 / PIPELINE_RATE
            require(seconds <= MAX_SECONDS) { "Songs longer than ${MAX_SECONDS / 60} minutes cannot be separated yet." }
            val directory = ModelFiles.ensure(context.noBackupFilesDir, "scnet/$REVISION", URL, mapOf(MODEL to DIGEST), listener)
            val mix = readAll(audio)
            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }
            environment.createSession(File(directory, MODEL).absolutePath, options).use { session ->
                val separator = ScnetSeparator(network(environment, session), STEP)
                val total = separator.segmentCount(mix.size)
                listener.onSection(1, total)
                val writers = outputs.map { WavWriter(it, PIPELINE_RATE) }
                try {
                    var instrumental = FloatArray(0)
                    separator.separate(mix, { done, all -> listener.onSection(minOf(done + 1, all), all) }) { stem, piece ->
                        // Stems of one stretch arrive in the order drums, bass, other, vocals.
                        when (stem) {
                            Stem.DRUMS -> {
                                writers[SeparatedStem.DRUMS.ordinal].append(piece)
                                instrumental = piece.copyOf()
                            }

                            Stem.BASS -> {
                                writers[SeparatedStem.BASS.ordinal].append(piece)
                                for (i in piece.indices) instrumental[i] += piece[i]
                            }

                            Stem.OTHER -> {
                                writers[SeparatedStem.OTHER.ordinal].append(piece)
                                for (i in piece.indices) instrumental[i] += piece[i]
                            }

                            Stem.VOCALS -> {
                                writers[SeparatedStem.VOCALS.ordinal].append(piece)
                                writers[SeparatedStem.INSTRUMENTAL.ordinal].append(instrumental)
                            }
                        }
                    }
                } finally {
                    writers.forEach { it.close() }
                }
            }
        } catch (e: Throwable) {
            outputs.forEach { it.delete() }
            throw e
        }
    }

    private const val THREADS = 4

    /** The network as ONNX Runtime runs it: `mix_spec` in, `stems_spec` out, one segment at a time. */
    private fun network(environment: OrtEnvironment, session: OrtSession): SpectrumNetwork {
        val shape = longArrayOf(1, 4, ScnetSeparator.BINS.toLong(), ScnetSeparator.FRAMES.toLong())
        val inputSize = 4 * ScnetSeparator.BINS * ScnetSeparator.FRAMES
        val outputSize = ScnetSeparator.STEMS * 4 * ScnetSeparator.BINS * ScnetSeparator.FRAMES
        // One native input buffer and one result array, reused by every segment.
        val direct = ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val result = FloatArray(outputSize)
        return SpectrumNetwork { input ->
            throwIfCancelled()
            direct.clear()
            direct.put(input)
            direct.flip()
            OnnxTensor.createTensor(environment, direct, shape).use { tensor ->
                session.run(mapOf("mix_spec" to tensor)).use { outputs ->
                    (outputs[0] as OnnxTensor).floatBuffer.get(result)
                }
            }
            result
        }
    }

    private fun readAll(audio: File): FloatArray {
        val samples = FloatArray((audio.length() / 2).toInt())
        var filled = 0
        AudioChunks.forEachChunk(audio, 20) { chunk ->
            System.arraycopy(chunk, 0, samples, filled, chunk.size)
            filled += chunk.size
        }
        return samples
    }
}
