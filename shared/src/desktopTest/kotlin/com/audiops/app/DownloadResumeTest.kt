package com.audiops.app

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.random.Random

/** The model downloader retries dropped connections and resumes with HTTP range requests. */
class DownloadResumeTest {
    private val payload = Random(5).nextBytes(300_000)
    private val ranges: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    private val dir = File.createTempFile("audiops-download", "").apply {
        delete()
        mkdirs()
    }
    private var requests = 0

    /** First response announces the full size but drops the connection halfway; later ones honour Range. */
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/model.bin") { exchange ->
            requests++
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            if (requests == 1) {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.write(payload, 0, payload.size / 2)
                exchange.responseBody.flush()
                exchange.close() // connection reset mid-download
            } else {
                val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                val body = payload.copyOfRange(start, payload.size)
                if (start > 0) exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                exchange.sendResponseHeaders(if (start > 0) 206 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        start()
    }

    @After fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private val url get() = "http://127.0.0.1:${server.address.port}/model.bin"

    @Test fun droppedConnectionIsResumedFromTheSavedBytes() {
        val destination = File(dir, "model.bin")
        val progress = mutableListOf<Float>()
        HubModels.download(url, destination, { progress += it }, requireHttps = false, retryPauseMs = 1)
        assertArrayEquals(payload, destination.readBytes())
        assertEquals(2, ranges.size)
        assertEquals(null, ranges[0])
        val resumedAt = ranges[1]!!.removePrefix("bytes=").removeSuffix("-").toInt()
        assertTrue("resumed after the saved half, at $resumedAt", resumedAt > 0)
        assertFalse("partial file cleaned up", File(dir, "model.bin.part").exists())
        assertEquals(1f, progress.last(), 0f)
    }

    @Test fun httpsIsRequiredForRealDownloads() {
        val error = runCatching { HubModels.download(url, File(dir, "x.bin")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
