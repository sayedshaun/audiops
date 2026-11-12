package com.audiops.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

actual suspend fun <T> runOnWorker(block: () -> T): T = runInterruptible(Dispatchers.IO) { block() }

actual fun Throwable.isOutOfMemory(): Boolean = this is OutOfMemoryError

actual fun Throwable.isRecoverable(): Boolean = this !is Error || this is OutOfMemoryError
