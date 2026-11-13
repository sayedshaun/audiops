package com.audiops.app

import java.io.File
import java.security.MessageDigest

/** Downloads a fixed set of model files once, verifies each SHA-256 digest, and reuses them. */
object ModelFiles {
    /** Top-level folders under the model root that hold downloaded models. */
    private val FOLDERS = listOf("resemble-enhance", "whisper-tiny", "bangla-zipformer", "xnnpack-cache")

    /** Total bytes used by downloaded models. */
    fun storageBytes(root: File): Long = FOLDERS.sumOf { folder ->
        File(root, folder).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Deletes every downloaded model. They are downloaded again on next use. */
    fun deleteAll(root: File) {
        FOLDERS.forEach { File(root, it).deleteRecursively() }
    }

    /**
     * Returns the directory holding every file in [digests], keyed by relative file name.
     * Files that are missing or fail verification are downloaded from `$baseUrl/$name`.
     * When [folder] is versioned (`name/revision`), other revisions of the same model are
     * deleted once this one is complete, so an update does not leave old files behind.
     */
    fun ensure(root: File, folder: String, baseUrl: String, digests: Map<String, String>, listener: ProcessingListener): File {
        val directory = File(root, folder)
        check(directory.isDirectory || directory.mkdirs()) { "Could not create model storage." }
        var index = 0
        for ((name, digest) in digests) {
            index++
            val file = File(directory, name)
            if (file.isFile && sha256(file) == digest) continue
            file.delete()
            if (reuseFromOtherRevision(directory, name, digest, file)) continue
            file.parentFile?.mkdirs()
            listener.onModelDownload(index, digests.size)
            val fileIndex = index
            HubModels.download("$baseUrl/$name", file) { fraction ->
                listener.onModelDownloadProgress(fileIndex, digests.size, fraction)
            }
            if (sha256(file) != digest) {
                file.delete()
                error("Model file verification failed: $name")
            }
        }
        if (folder.contains('/')) {
            directory.parentFile?.listFiles()?.filter { it != directory }?.forEach { it.deleteRecursively() }
        }
        return directory
    }

    /**
     * When a model moves to a new revision, files that did not change are moved over from
     * the previous revision's folder instead of being downloaded again.
     */
    private fun reuseFromOtherRevision(directory: File, name: String, digest: String, target: File): Boolean {
        val siblings = directory.parentFile?.listFiles()?.filter { it.isDirectory && it != directory } ?: return false
        for (sibling in siblings) {
            val candidate = File(sibling, name)
            if (candidate.isFile && sha256(candidate) == digest) {
                target.parentFile?.mkdirs()
                if (candidate.renameTo(target)) return true
            }
        }
        return false
    }

    fun sha256(file: File): String {
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
