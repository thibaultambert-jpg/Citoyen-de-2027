package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Retrieval budget of AGENTS.md §4 (< 300 ms) on synthetic, fictional corpora larger than the real
 * one: 10 000 programme passages of 200 words (lexical) and 60 000 vectors of 384 dimensions (§8).
 * Times are measured on the JVM; a phone CPU is slower, hence the margin kept below the budget.
 */
class PerformanceBudgetTest {
    private val random = Random(2027)
    private val vocabulary = List(20_000) { "terme${it}fictif" }

    /** Zipf-like draw: frequent words are frequent, like in real text. */
    private fun word() = vocabulary[(vocabulary.size * random.nextDouble().let { it * it * it }).toInt()]

    private fun median(times: List<Long>) = times.sorted()[times.size / 2] / 1_000_000.0

    @Test
    fun `lexical retrieval stays well under 300 ms`() {
        val candidates = List(10) { CandidateId("fictif-$it") }
        val passages = List(10_000) { i ->
            Passage("p$i", Corpus.PROGRAM, candidates[i % candidates.size], List(200) { word() }.joinToString(" "))
        }
        lateinit var index: LexicalIndex
        val buildMs = measureNanoTime { index = LexicalIndex(passages) } / 1_000_000.0
        val queries = List(30) { List(5) { word() }.joinToString(" ") }
        val times = queries.map { query ->
            measureNanoTime { index.search(query, PassageFilter(Corpus.PROGRAM, setOf(candidates[0])), 30) }
        }
        val queryMs = median(times)
        println("Lexical: build %.0f ms for %d passages, median query %.1f ms".format(buildMs, passages.size, queryMs))
        assertTrue(queryMs < BUDGET_MS / 3, "Median lexical query $queryMs ms")
    }

    @Test
    fun `vector scan of 60 000 passages stays well under 300 ms`() {
        val dimensions = 384
        val entries = List(60_000) { i ->
            Passage("v$i", Corpus.LEGAL, null, "") to ByteArray(dimensions) { (random.nextInt(255) - 127).toByte() }
        }
        val index = VectorIndex("fictif", dimensions, entries)
        val query = FloatArray(dimensions) { random.nextFloat() - 0.5f }
        val times = List(15) { measureNanoTime { index.search(query, PassageFilter(Corpus.LEGAL), 30) } }
        val queryMs = median(times)
        println("Vectors: median scan %.1f ms for %d vectors".format(queryMs, entries.size))
        assertTrue(queryMs < BUDGET_MS / 3, "Median vector scan $queryMs ms")
    }

    private companion object {
        const val BUDGET_MS = 300.0
    }
}
