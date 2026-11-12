package com.audiops.app

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Reports progress of long-running work. Methods are called on the worker thread. */
internal interface ProcessingListener {
    /** A model file is being downloaded. [index] is 1-based. */
    fun onModelDownload(index: Int, total: Int) {}

    /** A section of the recording is being processed. [index] is 1-based. */
    fun onSection(index: Int, total: Int) {}

    object None : ProcessingListener
}

/** Downloads a fixed set of model files once, verifies each SHA-256 digest, and reuses them. */
internal object ModelFiles {
    /** Top-level folders in `noBackupFilesDir` that hold downloaded models. */
    private val FOLDERS = listOf("resemble-enhance", "whisper-tiny", "bangla-zipformer")

    /** Total bytes used by downloaded models. */
    fun storageBytes(context: Context): Long = FOLDERS.sumOf { folder ->
        File(context.noBackupFilesDir, folder).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Deletes every downloaded model. They are downloaded again on next use. */
    fun deleteAll(context: Context) {
        FOLDERS.forEach { File(context.noBackupFilesDir, it).deleteRecursively() }
    }

    /**
     * Returns the directory holding every file in [digests], keyed by relative file name.
     * Files that are missing or fail verification are downloaded from `$baseUrl/$name`.
     */
    fun ensure(
        context: Context,
        folder: String,
        baseUrl: String,
        digests: Map<String, String>,
        listener: ProcessingListener,
    ): File {
        val directory = File(context.noBackupFilesDir, folder)
        check(directory.isDirectory || directory.mkdirs()) { "Could not create model storage." }
        var index = 0
        for ((name, digest) in digests) {
            index++
            val file = File(directory, name)
            if (file.isFile && sha256(file) == digest) continue
            file.delete()
            file.parentFile?.mkdirs()
            listener.onModelDownload(index, digests.size)
            HubModels.download("$baseUrl/$name", file)
            if (sha256(file) != digest) {
                file.delete()
                error("Model file verification failed: $name")
            }
        }
        return directory
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
