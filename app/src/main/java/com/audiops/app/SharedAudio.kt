package com.audiops.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Audio another app shared with, or opened in, Audiops. Android only lets the receiving Activity
 * read such a file for a short while, and jobs may run after it has closed, so the file is copied
 * into the app's cache and the copy is used from then on. Copies are removed after two days.
 */
internal object SharedAudio {
    private const val FOLDER = "shared"
    private const val KEEP_MILLIS = 2L * 24 * 60 * 60 * 1000

    /** The copy of [uri] and the name to show for it. */
    fun import(context: Context, uri: Uri): Pair<File, String> {
        val directory = File(context.cacheDir, FOLDER).apply { mkdirs() }
        prune(directory)
        val name = displayName(context, uri)
        val file = File(directory, "${System.currentTimeMillis()}-${safeFileName(name)}")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the shared file.")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return file to name
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "shared-audio"

    private fun prune(directory: File) {
        val limit = System.currentTimeMillis() - KEEP_MILLIS
        directory.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }
}
