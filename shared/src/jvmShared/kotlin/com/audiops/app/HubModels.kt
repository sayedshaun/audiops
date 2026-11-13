/* Public Hugging Face model downloads shared by every platform engine. */
package com.audiops.app

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Hugging Face model file names and an atomic HTTPS downloader with progress. */
object HubModels {
    fun fileName(name: String, dtype: String): String = when (dtype) {
        "fp32" -> "$name-float32.tflite"

        "fp16" -> "$name-fp16weights.tflite"

        "int8" -> if (name == "vocoder") "$name-int8-weights.tflite" else "$name-int8-dynamic.tflite"

        // Phones: INT8 dynamic for the small networks; the vocoder re-exported for XNNPACK with
        // float16 weights (the INT8 vocoder needs ~4.5 GB at run time and loses quality).
        "mobile" -> if (name == "vocoder") "$name-mobile-fp16.tflite" else "$name-int8-dynamic.tflite"

        else -> throw IllegalArgumentException("dtype must be 'fp32', 'fp16', 'int8' or 'mobile'")
    }

    private const val ATTEMPTS = 5

    /**
     * Downloads [url] to [destination] atomically: bytes go to `<name>.part` next to it, which
     * is renamed only once complete. A dropped connection (common on mobile data) is retried up
     * to [ATTEMPTS] times with a growing pause, and each retry resumes from the bytes already
     * saved with an HTTP range request; the partial file also survives app restarts. Reads in
     * 64 KB chunks so cancellation is noticed quickly; [onProgress] gets 0..1 when the size is
     * known, at most once per percent. Callers verify the result (see [ModelFiles.ensure]).
     */
    fun download(url: String, destination: File, onProgress: (Float) -> Unit = {}) =
        download(url, destination, onProgress, requireHttps = true, retryPauseMs = 2_000)

    internal fun download(url: String, destination: File, onProgress: (Float) -> Unit, requireHttps: Boolean, retryPauseMs: Long) {
        val part = File(destination.parentFile, destination.name + ".part")
        var failure: java.io.IOException? = null
        for (attempt in 1..ATTEMPTS) {
            throwIfCancelled()
            try {
                downloadOnce(url, part, onProgress, requireHttps)
                check(part.renameTo(destination)) { "Cannot save model: ${destination.name}" }
                return
            } catch (e: java.io.IOException) {
                if (Thread.currentThread().isInterrupted) throw e // cancellation, not a network error
                failure = e
                if (attempt < ATTEMPTS) Thread.sleep(retryPauseMs * attempt) // interruptible: cancel still works
            }
        }
        throw failure!!
    }

    private fun downloadOnce(url: String, part: File, onProgress: (Float) -> Unit, requireHttps: Boolean) {
        var connection: HttpURLConnection? = null
        try {
            var current = URL(url)
            for (redirects in 0..10) {
                require(!requireHttps || current.protocol == "https") { "Model downloads require HTTPS" }
                val offset = if (part.isFile) part.length() else 0L
                val active = current.openConnection() as HttpURLConnection
                connection = active
                active.instanceFollowRedirects = false
                active.connectTimeout = 30000
                active.readTimeout = 120000
                if (offset > 0) active.setRequestProperty("Range", "bytes=$offset-")
                val status = active.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    check(redirects < 10) { "Too many model download redirects" }
                    val location = active.getHeaderField("Location") ?: error("Missing redirect location")
                    current = URL(current, location)
                    active.disconnect()
                    connection = null
                    continue
                }
                if (status == 416 && offset > 0) {
                    // The saved part is already complete (or invalid): start over; verification decides.
                    part.delete()
                    active.disconnect()
                    connection = null
                    continue
                }
                if (status == 429 || status >= 500) {
                    // Temporary server trouble: let the caller retry.
                    throw java.io.IOException("Model download failed with HTTP $status: ${part.name.removeSuffix(".part")}")
                }
                check(status in 200..299) { "Model download failed with HTTP $status: ${part.name.removeSuffix(".part")}" }
                // 206: the server resumed at our offset; 200: it sent the whole file again.
                val resumed = status == 206 && offset > 0
                val start = if (resumed) offset else 0L
                val total = if (active.contentLengthLong >= 0) start + active.contentLengthLong else -1L
                var copied = start
                var reported = -1
                active.inputStream.use { input ->
                    java.io.FileOutputStream(part, resumed).buffered(1 shl 16).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            throwIfCancelled()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            if (total > 0) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
                if (copied <= 0L || (total >= 0L && copied != total)) {
                    throw java.io.IOException("Incomplete model download: ${part.name.removeSuffix(".part")}")
                }
                return
            }
            error("Too many model download redirects")
        } finally {
            connection?.disconnect()
        }
    }
}
