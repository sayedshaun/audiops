package com.audiops.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelFilesTest {
    private val root = File.createTempFile("audiops-models", "").apply {
        delete()
        mkdirs()
    }

    @After fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(root, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    @Test fun verifiedFilesAreReusedWithoutDownloadingAndOldRevisionsAreRemoved() {
        val model = write("whisper-tiny/new/model.onnx", "weights")
        val old = write("whisper-tiny/old/model.onnx", "old weights")
        val other = write("bangla-zipformer/rev/model.onnx", "other model")
        var downloads = 0
        val listener = object : ProcessingListener {
            override fun onModelDownload(index: Int, total: Int) {
                downloads++
            }
        }
        // An unreachable URL proves nothing is downloaded when the digest matches.
        val directory = ModelFiles.ensure(
            root,
            "whisper-tiny/new",
            "https://invalid.example",
            mapOf("model.onnx" to ModelFiles.sha256(model)),
            listener,
        )
        assertEquals(model.parentFile, directory)
        assertEquals(0, downloads)
        assertFalse("old revision removed", old.parentFile.exists())
        assertTrue("other models kept", other.exists())
    }

    @Test fun unchangedFilesMoveFromTheOldRevisionInsteadOfDownloading() {
        val old = write("resemble-enhance/old/encoder.tflite", "same weights")
        val digest = ModelFiles.sha256(old)
        var downloads = 0
        val listener = object : ProcessingListener {
            override fun onModelDownload(index: Int, total: Int) {
                downloads++
            }
        }
        // An unreachable URL proves the file is reused, not downloaded.
        val directory = ModelFiles.ensure(
            root,
            "resemble-enhance/new",
            "https://invalid.example",
            mapOf("encoder.tflite" to digest),
            listener,
        )
        assertEquals(0, downloads)
        assertEquals("same weights", File(directory, "encoder.tflite").readText())
        assertFalse("old revision removed", File(root, "resemble-enhance/old").exists())
    }

    @Test fun storageCountsAndDeleteRemovesEveryModel() {
        write("resemble-enhance/rev/a.tflite", "12345")
        write("whisper-tiny/rev/b.onnx", "123")
        write("unrelated/c.txt", "1234567")
        assertEquals(8L, ModelFiles.storageBytes(root))
        ModelFiles.deleteAll(root)
        assertEquals(0L, ModelFiles.storageBytes(root))
        assertTrue(File(root, "unrelated/c.txt").exists())
    }

    @Test fun sha256MatchesAKnownDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelFiles.sha256(write("abc.txt", "abc")),
        )
    }
}
