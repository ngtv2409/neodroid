package com.ngtv.neodroid.perf

import android.os.Trace
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight runtime profiling for the Neovim render path.
 *
 * Enable logging/tracing with:
 *
 *     adb shell setprop log.tag.NvimPerf DEBUG
 *
 * then restart the app. The property is read when this object is initialized.
 *
 * Timings are reported every two seconds through logcat. The same measurements
 * are emitted as atrace/Perfetto slices through [Trace].
 */
object NvimPerf {
    private const val TAG = "NvimPerf"
    private const val REPORT_INTERVAL_NS = 2_000_000_000L
    private const val INPUT_TIMEOUT_NS = 1_000_000_000L

    @Volatile
    var enabled: Boolean = Log.isLoggable(TAG, Log.DEBUG)

    private class Stats {
        val count = AtomicLong()
        val totalNanos = AtomicLong()
        val maxNanos = AtomicLong()

        fun snapshot(): Snapshot {
            val snapshot = Snapshot(
                count = count.getAndSet(0),
                totalNanos = totalNanos.getAndSet(0),
                maxNanos = maxNanos.getAndSet(0),
            )
            return snapshot
        }
    }

    private data class Snapshot(val count: Long, val totalNanos: Long, val maxNanos: Long)

    private val stats = ConcurrentHashMap<String, Stats>()
    private val nextReportAt = AtomicLong(System.nanoTime() + REPORT_INTERVAL_NS)
    private val pendingInputSince = AtomicLong(0L)

    fun <T> measure(name: String, block: () -> T): T {
        if (!enabled) return block()

        val start = System.nanoTime()
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
            record(name, System.nanoTime() - start)
        }
    }

    /**
     * Marks the first input received after the previous flush. This measures
     * the useful "input enqueued -> state published" latency without counting
     * queued keystrokes individually.
     */
    fun markInput() {
        if (!enabled) return
        pendingInputSince.compareAndSet(0L, System.nanoTime())
    }

    fun markFlush() {
        if (!enabled) return

        val start = pendingInputSince.getAndSet(0L)
        if (start == 0L) return

        val elapsed = System.nanoTime() - start
        if (elapsed in 0..INPUT_TIMEOUT_NS) {
            record("input->flush", elapsed)
        }
    }

    private fun record(name: String, elapsedNanos: Long) {
        val stat = stats.computeIfAbsent(name) { Stats() }
        stat.count.incrementAndGet()
        stat.totalNanos.addAndGet(elapsedNanos)
        stat.maxNanos.accumulateAndGet(elapsedNanos) { current, value ->
            maxOf(current, value)
        }

        maybeReport()
    }

    private fun maybeReport() {
        val now = System.nanoTime()
        val dueAt = nextReportAt.get()
        if (now < dueAt || !nextReportAt.compareAndSet(dueAt, now + REPORT_INTERVAL_NS)) {
            return
        }

        val message = stats.entries.mapNotNull { (name, stat) ->
            val snapshot = stat.snapshot()
            if (snapshot.count == 0L) {
                null
            } else {
                val averageMicros = snapshot.totalNanos / snapshot.count / 1_000
                val maxMicros = snapshot.maxNanos / 1_000
                "$name n=${snapshot.count} avg=${averageMicros}us max=${maxMicros}us"
            }
        }.joinToString(" | ")

        if (message.isNotEmpty()) {
            Log.d(TAG, message)
        }
    }
}
