package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local, debug-only protection trace.
 *
 * The accessibility callback never performs disk I/O. It only appends a
 * sanitized structural line to a bounded in-memory ring. A bounded background
 * worker periodically writes a snapshot to app-private storage, so the
 * recorder cannot create the delay that it is intended to measure.
 */
internal object ProtectionFlightRecorder {
    const val ENABLED = true

    private const val MAX_RING_CHARS = 512 * 1024
    private const val MAX_DETAIL_CHARS = 900
    private const val FLUSH_INTERVAL_MS = 1_500L

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var ringChars = 0
    private var generation = 0L
    private var persistedGeneration = -1L
    private var lastFlushRequestedAt = 0L
    private val flushQueued = AtomicBoolean(false)

    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2),
        { runnable ->
            Thread(runnable, "protection-flight-recorder").apply { isDaemon = true }
        },
        RejectedExecutionHandler { _, _ -> flushQueued.set(false) }
    )

    fun record(context: Context, category: String, detail: String = "") {
        val elapsed = SystemClock.elapsedRealtime()
        val line = buildString {
            append(elapsed)
            append('|')
            append(sanitize(category, 64))
            append('|')
            append(sanitize(detail, MAX_DETAIL_CHARS))
            append('\n')
        }
        synchronized(lock) {
            lines.addLast(line)
            ringChars += line.length
            generation++
            while (ringChars > MAX_RING_CHARS && lines.isNotEmpty()) {
                ringChars -= lines.removeFirst().length
            }
        }

        // ADB can read a recent snapshot without touching UiAutomation. Throttle
        // snapshots so bursty accessibility events never turn into bursty I/O.
        val now = SystemClock.elapsedRealtime()
        if (now - lastFlushRequestedAt >= FLUSH_INTERVAL_MS) {
            lastFlushRequestedAt = now
            enqueueFlush(context.applicationContext)
        } else if (trailingFlushScheduled.compareAndSet(false, true)) {
            // Without a trailing write, the last lines before a quiet period
            // never reached the file until some unrelated later event.
            // Robolectric test contexts can have no base; never fail a caller.
            val appContext = runCatching { context.applicationContext }.getOrNull()
            if (appContext == null) {
                trailingFlushScheduled.set(false)
                return
            }
            trailingFlush.schedule({
                trailingFlushScheduled.set(false)
                lastFlushRequestedAt = SystemClock.elapsedRealtime()
                enqueueFlush(appContext)
            }, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    private val trailingFlushScheduled = AtomicBoolean(false)
    private val trailingFlush = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "protection-flight-trailing").apply { isDaemon = true }
    }

    fun startSession(context: Context, versionName: String) {
        // A service reconnect is itself valuable evidence. Never erase the
        // immediately preceding events here.
        record(context, "session.begin", "version=$versionName sdk=${Build.VERSION.SDK_INT} " +
            "device=${sanitize(Build.MANUFACTURER, 40)}/${sanitize(Build.MODEL, 60)}")
    }

    private fun sanitize(value: String?, maxChars: Int): String = value.orEmpty()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('|', '/')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxChars)

    private data class Snapshot(val generation: Long, val text: String)

    private fun snapshot(): Snapshot {
        // Copy references under the lock and join outside it: joining 512 KB
        // while holding the lock made record() on the accessibility thread wait.
        val (snapshotGeneration, copy) = synchronized(lock) { generation to lines.toTypedArray() }
        return Snapshot(snapshotGeneration, copy.joinToString(separator = ""))
    }

    private fun enqueueFlush(context: Context) {
        if (!flushQueued.compareAndSet(false, true)) return
        writer.execute {
            try {
                val snapshot = snapshot()
                if (snapshot.generation != persistedGeneration) {
                    val file = traceFile(context)
                    file.parentFile?.mkdirs()
                    file.writeText(snapshot.text.takeLast(MAX_RING_CHARS), Charsets.UTF_8)
                    persistedGeneration = snapshot.generation
                }
            } finally {
                flushQueued.set(false)
            }
        }
    }

    private fun traceFile(context: Context): File =
        File(context.filesDir, "debug/protection-flight.log")
}
