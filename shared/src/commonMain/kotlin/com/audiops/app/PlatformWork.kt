package com.audiops.app

/**
 * Runs blocking [block] on a background worker. Cancelling the calling coroutine
 * interrupts the worker thread, which the audio loops check (`throwIfCancelled`).
 */
expect suspend fun <T> runOnWorker(block: () -> T): T

/** True for out-of-memory errors, which the UI reports as "recording too long". */
expect fun Throwable.isOutOfMemory(): Boolean

/** False for fatal VM errors other than out-of-memory, which must not be swallowed. */
expect fun Throwable.isRecoverable(): Boolean
