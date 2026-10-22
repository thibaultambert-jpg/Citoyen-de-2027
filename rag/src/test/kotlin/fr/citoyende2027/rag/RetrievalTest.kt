package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fictional passages only (AGENTS.md §2.6). */
class RetrievalTest {
    private val a = CandidateId("fictif-a")
    private val b = CandidateId("fictif-b")

    private val passages = listOf(
        Passage("legal-1", Corpus.LEGAL, null, "Article 1. Le mandat fictif dure cinq ans fictifs."),
        Passage("legal-2", Corpus.LEGAL, null, "Article 2. Les élections fictives ont lieu au suffrage fictif."),
        Passage("legal-3", Corpus.LEGAL, null, "Article 3. Une disposition sans rapport."),
        Passage("a-1", Corpus.PROGRAM, a, "Mesure fictive sur les hôpitaux et la santé fictive."),
        Passage("a-2", Corpus.PROGRAM, a, "Mesure fictive sur l'école."),
        Passage("b-1", Corpus.PROGRAM, b, "Mesure fictive : un hôpital fictif par commune."),
    )
    private val index = LexicalIndex(passages)

    @Test
    fun `analyzer drops function words and stems by prefix`() {
        assertEquals(
            listOf("duree", "mandat", "president"),
            TextAnalyzer.queryTerms("Quelle est la durée du mandat du Président ?"),
        )
        assertEquals("electi", TextAnalyzer.stem("election"))
        assertEquals("ecole", TextAnalyzer.stem("ecole"))
        assertEquals("article12", TextAnalyzer.stem("article12"))
    }

    @Test
    fun `lexical search ranks, filters by corpus and candidate, and counts matched terms`() {
        val legal = index.search("Combien de temps dure le mandat ?", PassageFilter(Corpus.LEGAL), 5)
        assertEquals("legal-1", legal.first().passage.id)
        assertEquals(2 to 3, legal.first().matchedTerms to legal.first().queryTerms)
        // Prefix matching: "élections" and "élection" share the stem "electi".
        assertEquals(listOf("legal-2"), index.search("élection", PassageFilter(Corpus.LEGAL), 5).map { it.passage.id })
        val onlyB = index.search("hôpitaux", PassageFilter(Corpus.PROGRAM, setOf(b)), 5)
        assertEquals(listOf("b-1"), onlyB.map { it.passage.id })
        assertTrue(index.search("hôpitaux", PassageFilter(Corpus.LEGAL), 5).isEmpty())
    }

    @Test
    fun `vector search and reciprocal rank fusion`() {
        val vectors = VectorIndex(
            "fictif",
            2,
            listOf(
                passages[0] to byteArrayOf(127, 0),
                passages[1] to byteArrayOf(0, 127),
                passages[3] to byteArrayOf(90, 90),
            ),
        )
        val hits = vectors.search(floatArrayOf(1f, 0f), PassageFilter(Corpus.LEGAL), 5)
        assertEquals(listOf("legal-1", "legal-2"), hits.map { it.passage.id })
        assertEquals(1.0, hits.first().similarity, 1e-9)

        val fused = reciprocalRankFusion(listOf(listOf("x", "y"), listOf("y", "z")), k = 60)
        assertEquals(listOf("y", "x", "z"), fused.map { it.first })
        assertEquals(1.0 / 62 + 1.0 / 61, fused.first().second, 1e-12)
    }
}
