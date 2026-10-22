package fr.citoyende2027.core.data.trace

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * System trace sections measured by the device benchmarks (benchmarks/, TraceSectionMetric) against
 * the budgets of AGENTS.md §4. Async sections, since coroutines may resume on another thread. They
 * carry no content (never a question), only a fixed name, and are only written while a trace is captured.
 */
object PerfTrace {
    /** A candidate's votes, from the query to the first list (budget: 50 ms). */
    const val VOTES = "citoyen.votes"

    /** Routing and retrieval of a question, without generation (budget: 300 ms). */
    const val ASK = "citoyen.ask"

    private val cookies = AtomicInteger()

    /** False on the JVM of unit tests, where android.os.Trace is a stub that throws. */
    private val available = runCatching { Trace.isEnabled() }.isSuccess

    /** Starts a section when a trace is being captured; returns its cookie, or null. */
    private fun begin(name: String): Int? {
        if (!available || !Trace.isEnabled()) return null
        return cookies.incrementAndGet().also { Trace.beginAsyncSection(name, it) }
    }

    private fun end(name: String, cookie: Int?) {
        if (cookie != null) Trace.endAsyncSection(name, cookie)
    }

    suspend fun <T> traced(name: String, block: suspend () -> T): T {
        val cookie = begin(name)
        try {
            return block()
        } finally {
            end(name, cookie)
        }
    }

    /** Traces the time from collection to the first value; later values are not traced. */
    fun <T> Flow<T>.tracedUntilFirst(name: String): Flow<T> = flow {
        val cookie = begin(name)
        var open = true
        try {
            collect { value ->
                if (open) {
                    end(name, cookie)
                    open = false
                }
                emit(value)
            }
        } finally {
            if (open) end(name, cookie)
        }
    }
}
