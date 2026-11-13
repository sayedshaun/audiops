package com.audiops.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

actual suspend fun <T> runOnWorker(block: () -> T): T = runInterruptible(Dispatchers.IO) {
    try {
        block()
    } catch (e: Exception) {
        // Cancelling interrupts the worker; blocking I/O then fails with its own exception
        // (InterruptedIOException, ClosedByInterruptException…). Report that as cancellation.
        val interrupted = Thread.currentThread().isInterrupted || e is InterruptedException ||
            e is java.io.InterruptedIOException || e is java.nio.channels.ClosedByInterruptException
        if (interrupted && e !is java.net.SocketTimeoutException) {
            throw java.util.concurrent.CancellationException("Cancelled").apply { initCause(e) }
        }
        throw e
    }
}

actual fun Throwable.isOutOfMemory(): Boolean = this is OutOfMemoryError

actual fun Throwable.isRecoverable(): Boolean = this !is Error || this is OutOfMemoryError

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
