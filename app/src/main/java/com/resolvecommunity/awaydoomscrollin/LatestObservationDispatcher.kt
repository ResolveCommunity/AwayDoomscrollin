package com.resolvecommunity.awaydoomscrollin

import java.util.concurrent.Executor
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong

/**
 * One in-flight read and one conflated follow-up, with UI-owned cancellation.
 * Call request/cancel/close on the owner executor. Only read runs on the worker;
 * results, including failures, are delivered to the owner. Content bursts do
 * not invalidate a useful observation; navigation explicitly advances its epoch.
 */
internal class LatestObservationDispatcher<T>(
    private val worker: Executor,
    private val owner: Executor,
    private val read: (isCurrent: () -> Boolean) -> T,
    private val accept: (Result<T>) -> Unit
) {
    private val epoch = AtomicLong()
    val generation: Long get() = epoch.get()
    fun isCurrent(generation: Long): Boolean = epoch.get() == generation
    private var running = false
    private var pending = false
    private var closed = false

    fun request(invalidate: Boolean = false) {
        if (closed) return
        if (invalidate) epoch.incrementAndGet()
        if (running) pending = true else start()
    }

    fun cancel() {
        epoch.incrementAndGet()
        pending = false
    }

    fun close() {
        closed = true
        cancel()
    }

    private fun start() {
        running = true
        val token = epoch.get()
        worker.execute {
            val observation = if (isCurrent(token)) runCatching { read { isCurrent(token) } }
                else Result.failure(CancellationException("Superseded observation"))
            owner.execute {
                try {
                    if (!closed && isCurrent(token)) accept(observation)
                } finally {
                    running = false
                    if (!closed && pending) {
                        pending = false
                        start()
                    }
                }
            }
        }
    }
}
