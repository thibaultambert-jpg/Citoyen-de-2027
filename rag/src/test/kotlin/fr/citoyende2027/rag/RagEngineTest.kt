package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RagEngineTest {
    private val a = CandidateId("fictif-a")
    private val b = CandidateId("fictif-b")
    private val passages = listOf(
        Passage("legal-1", Corpus.LEGAL, null, "Article 1. Le mandat fictif dure cinq ans fictifs."),
        Passage("legal-2", Corpus.LEGAL, null, "Article 2. Les élections fictives ont lieu au suffrage fictif."),
        Passage("a-1", Corpus.PROGRAM, a, "Mesure fictive sur les hôpitaux et la santé."),
        Passage("b-1", Corpus.PROGRAM, b, "Mesure fictive sur l'école et les enseignants."),
    )
    private val config = RagConfig(topK = 3, minMatchedTerms = 1, minCoverage = 0.5)

    private fun engine(config: RagConfig = this.config) = RagEngine(
        config = config,
        lexical = LexicalIndex(passages),
        refusal = RefusalGate.bundled(),
        router = IntentRouter(listOf(CandidateName(a, "Candidat A"), CandidateName(b, "Candidat B"))),
    )

    @Test
    fun `the refusal gate comes first`() {
        assertEquals(
            RagResult.Refused(RefusalCategory.VOTE_ADVICE),
            engine().answer("Pour qui voter ? mandat", RagScope.Legal),
        )
    }

    @Test
    fun `structured questions are routed before retrieval`() {
        assertEquals(
            RagResult.Structured(StructuredIntent.Mandates(a)),
            engine().answer("Mandats de Candidat A ?", RagScope.Legal),
        )
    }

    @Test
    fun `legal retrieval returns ranked passages`() {
        val result =
            assertInstanceOf(
                RagResult.Passages::class.java,
                engine().answer("Combien de temps dure le mandat ?", RagScope.Legal),
            )
        assertEquals("legal-1", result.groups.single().passages.first().passage.id)
    }

    @Test
    fun `nothing relevant means abstention`() {
        assertEquals(RagResult.Abstained, engine().answer("Quel est le budget de la défense ?", RagScope.Legal))
        // Strict thresholds: one matched term out of three is not enough.
        val strict = engine(config.copy(minMatchedTerms = 2, minCoverage = 0.6))
        assertEquals(RagResult.Abstained, strict.answer("mandat présidentiel renouvelable", RagScope.Legal))
    }

    @Test
    fun `programmes side by side keep one group per candidate in the given order`() {
        val result = assertInstanceOf(
            RagResult.Passages::class.java,
            engine().answer("Que proposent-ils sur la santé ?", RagScope.Programs(listOf(a, b))),
        )
        assertEquals(listOf(a, b), result.groups.map { it.candidateId })
        assertEquals(listOf("a-1"), result.groups[0].passages.map { it.passage.id })
        assertTrue(result.groups[1].abstained)
    }

    @Test
    fun `config is read from the pack json`() {
        val parsed = RagConfig.parse("""{"top_k": 4, "min_matched_terms": 2, "min_coverage": 0.5, "unknown": 1}""")
        assertEquals(RagConfig(topK = 4, minMatchedTerms = 2, minCoverage = 0.5), parsed)
    }
}
