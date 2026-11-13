/* Public Hugging Face model downloads shared by every platform engine. */
package com.audiops.app

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Download public Hub models once, using an app-private persistent cache. */
object HubModels {
    private val lock = Any()
    private val components = listOf("denoiser-net", "encoder", "flow-step", "decoder", "vocoder")

    fun fileName(name: String, dtype: String): String = when (dtype) {
        "fp32" -> "$name-float32.tflite"
        "fp16" -> "$name-fp16weights.tflite"
        "int8" -> if (name == "vocoder") "$name-int8-weights.tflite" else "$name-int8-dynamic.tflite"
        else -> throw IllegalArgumentException("dtype must be 'fp32', 'fp16', or 'int8'")
    }

    fun prepare(
        cacheRoot: File,
        repoId: String,
        dtype: String,
        onDownload: (index: Int, total: Int, fileFraction: Float) -> Unit = { _, _, _ -> },
    ): File = synchronized(lock) {
        require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$").matches(repoId)) {
            "repoId must be a public Hugging Face repository in the form owner/model"
        }
        val files = components.map { fileName(it, dtype) } + "mel-filters.f32"
        val hash = MessageDigest.getInstance("SHA-256").digest(repoId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = File(cacheRoot, "resemble-enhance/$hash")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create model cache: $directory" }
        for ((index, name) in files.withIndex()) {
            val destination = File(directory, name)
            if (destination.isFile && destination.length() > 0L) continue
            onDownload(index + 1, files.size, 0f)
            download("https://huggingface.co/$repoId/resolve/main/models/$name", destination) { fraction ->
                onDownload(index + 1, files.size, fraction)
            }
        }
        directory
    }

    /**
     * Downloads [url] to [destination] atomically (temporary file, then rename). Reads in
     * 64 KB chunks so cancellation is noticed quickly; [onProgress] gets 0..1 when the
     * size is known, at most once per percent.
     */
    fun download(url: String, destination: File, onProgress: (Float) -> Unit = {}) {
        val temp = File.createTempFile("download-", ".part", destination.parentFile)
        var connection: HttpURLConnection? = null
        try {
            var current = URL(url)
            for (redirects in 0..10) {
                require(current.protocol == "https") { "Model downloads require HTTPS" }
                val active = current.openConnection() as HttpURLConnection
                connection = active
                active.instanceFollowRedirects = false
                active.connectTimeout = 30000
                active.readTimeout = 120000
                val status = active.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    check(redirects < 10) { "Too many model download redirects" }
                    val location = active.getHeaderField("Location") ?: error("Missing redirect location")
                    current = URL(current, location)
                    active.disconnect()
                    connection = null
                    continue
                }
                check(status in 200..299) { "Model download failed with HTTP $status: ${destination.name}" }
                val expected = active.contentLengthLong
                var copied = 0L
                var reported = -1
                active.inputStream.use { input ->
                    temp.outputStream().buffered(1 shl 16).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            throwIfCancelled()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            if (expected > 0) {
                                val percent = (copied * 100 / expected).toInt()
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
                check(copied > 0L && (expected < 0L || copied == expected)) {
                    "Incomplete model download: ${destination.name}"
                }
                check(temp.renameTo(destination)) { "Cannot save model: ${destination.name}" }
                return
            }
            error("Too many model download redirects")
        } finally {
            connection?.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}
