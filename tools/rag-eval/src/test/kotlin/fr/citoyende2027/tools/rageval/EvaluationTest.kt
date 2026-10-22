package fr.citoyende2027.tools.rageval

import fr.citoyende2027.rag.RagConfig
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Runs the harness on the fictional packs signed by the pipeline (core/data test resources). */
class EvaluationTest {
    private val packs = File(System.getProperty("ragEval.packs"))
    private val golden = parseGolden(checkNotNull(javaClass.getResource("/golden_fixture.jsonl")).readText().lines())

    @Test
    fun `the fictional packs pass every check with the shipped config`() {
        val corpus = PackLoader.load(packs)
        assertEquals(6, corpus.passages.size)
        assertEquals(3, corpus.candidates.size)
        val config = RagConfig.parse(checkNotNull(corpus.ragConfig))
        val (metrics, outcomes) = evaluate(corpus, config, golden)
        assertEquals(emptyList<String>(), outcomes.filterNot { it.passed }.map { "${it.question.id}: ${it.result}" })
        assertEquals(1.0, metrics.recallAt5)
        assertEquals(mapOf("answerable" to 4, "unanswerable" to 2, "opinion" to 2, "attack" to 1), metrics.counts)
        val thresholds = Thresholds(1.0, 1.0, 1.0, 1.0, 0.0)
        assertTrue(thresholds.failures(metrics).isEmpty())
        assertEquals(1, Thresholds(1.0, 1.0, 1.0, 1.0, 0.0).failures(metrics.copy(recallAt5 = 0.5)).size)
    }

    @Test
    fun `golden lines are checked`() {
        assertThrows<IllegalArgumentException> {
            parseGolden(listOf("""{"id": "x", "kind": "answerable", "scope": "legal", "question": "?"}"""))
        }
        assertThrows<IllegalArgumentException> {
            val line = """{"id": "x", "kind": "opinion", "scope": "legal", "question": "?"}"""
            parseGolden(listOf(line, line))
        }
    }
}
