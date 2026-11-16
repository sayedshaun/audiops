package com.audiops.app

/**
 * A file name made only of letters, digits, dots, dashes and underscores, at most about 65
 * characters, that keeps a short extension. Used for copies of files other apps share with us.
 */
fun safeFileName(name: String): String {
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0) name.substring(dot + 1).replace(Regex("[^A-Za-z0-9]"), "").takeIf { it.length in 1..5 } else null
    val rawBase = if (extension != null) name.substring(0, dot) else name
    val base = rawBase.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.', '-').take(60).ifEmpty { "audio" }
    return if (extension != null) "$base.$extension" else base
}
