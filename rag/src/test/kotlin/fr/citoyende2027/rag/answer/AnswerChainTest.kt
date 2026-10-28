package fr.citoyende2027.rag.answer

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Fictional passages only (AGENTS.md §2.6).
class AnswerChainTest {
    /** Counts words as tokens and replies with the scripted outputs, in order. */
    private class FakeLlm(vararg replies: GenerationResult) : LlmGenerator {
        private val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun countPromptTokens(system: String, user: String): Int =
            "$system $user".split(Regex("\\s+")).size

        override suspend fun generate(request: GenerationRequest, onText: (String) -> Unit): GenerationResult {
            requests += request
            return queue.removeFirst()
        }
    }

    private fun completed(json: String) = GenerationResult.Completed(json, GenerationStats(100, 800, 40, 4000))

    private val candidates = listOf(
        SourceCandidate(
            "p1",
            "Texte fictif A, article 2",
            "L'élection fictive a lieu tous les 7 ans, en avril, au Palais Fictif.",
        ),
        SourceCandidate(
            "p2",
            "Texte fictif B, article 9",
            "Le Conseil Fictif proclame les résultats « dans les dix jours ».",
        ),
    )

    private fun answerer(llm: LlmGenerator) = GroundedAnswerer(llm, "Consigne système fictive.")

    @Test
    fun `verified claims are kept with their sources and labels are stripped from the text`() = runTest {
        val llm = FakeLlm(
            completed(
                """{"answerable": true, "claims": [
                  {"text": "Selon le texte fictif A, l'élection a lieu tous les 7 ans [S1].", "sources": ["S1"]},
                  {"text": "Le Conseil Fictif proclame les résultats « dans les dix jours ».", "sources": ["S2"]}]}""",
            ),
        )
        val answer = assertInstanceOf(GroundedAnswer.Answered::class.java, answerer(llm).answer("Quand ?", candidates))
        assertEquals(
            listOf(
                "Selon le texte fictif A, l'élection a lieu tous les 7 ans.",
                "Le Conseil Fictif proclame les résultats « dans les dix jours ».",
            ),
            answer.claims.map { it.text },
        )
        assertEquals(listOf(listOf("p1"), listOf("p2")), answer.claims.map { c -> c.sources.map { it.passageId } })
        assertTrue(answer.rejected.isEmpty())
        val request = llm.requests.single()
        assertTrue("[S1] Texte fictif A, article 2" in request.user && "Question : Quand ?" in request.user)
        assertTrue("\"\\\"S2\\\"\"" in request.grammar)
    }

    @Test
    fun `claims with facts missing from the cited passage are removed`() = runTest {
        val claims = listOf(
            """{"text": "L'élection a lieu tous les 5 ans.", "sources": ["S1"]}""",
            """{"text": "Elle a lieu au Palais Inventé.", "sources": ["S1"]}""",
            """{"text": "Elle a lieu en mai.", "sources": ["S1"]}""",
            """{"text": "Les résultats sont proclamés « dans la semaine ».", "sources": ["S2"]}""",
            """{"text": "Le Conseil Fictif proclame les résultats.", "sources": ["S1"]}""",
        )
        val output = ModelOutput.parse("""{"answerable": true, "claims": [${claims.joinToString(",")}]}""")!!
        val sources = candidates.mapIndexed { i, c -> AnswerSource("S${i + 1}", c.passageId, c.title, c.text, false) }
        val (kept, rejected) = ClaimVerifier.verify(output, sources)
        assertTrue(kept.isEmpty())
        assertEquals(
            listOf(
                Rejection.UNSUPPORTED_NUMBER,
                Rejection.UNSUPPORTED_NAME,
                Rejection.UNSUPPORTED_MONTH,
                Rejection.UNSUPPORTED_QUOTE,
                Rejection.UNSUPPORTED_NAME,
            ),
            rejected,
        )
    }

    @Test
    fun `unknown source labels, everything removed, and answerable false all mean abstention`() = runTest {
        val unknown = FakeLlm(completed("""{"answerable": true, "claims": [{"text": "Fait.", "sources": ["S9"]}]}"""))
        assertEquals(
            AbstentionReason.UNSUPPORTED,
            (answerer(unknown).answer("Q ?", candidates) as GroundedAnswer.Abstained).reason,
        )
        val no = FakeLlm(completed("""{"answerable": false, "claims": []}"""))
        assertEquals(
            AbstentionReason.NOT_ANSWERABLE,
            (answerer(no).answer("Q ?", candidates) as GroundedAnswer.Abstained).reason,
        )
    }

    @Test
    fun `invalid JSON is retried once, then the assistant abstains`() = runTest {
        val twice = FakeLlm(completed("pas du JSON"), completed("""{"answerable": true}"""))
        assertEquals(
            AbstentionReason.INVALID_OUTPUT,
            (answerer(twice).answer("Q ?", candidates) as GroundedAnswer.Abstained).reason,
        )
        assertEquals(2, twice.requests.size)

        val second = FakeLlm(
            completed("{"),
            completed("""{"answerable": true, "claims": [{"text": "Elle a lieu en avril.", "sources": ["S1"]}]}"""),
        )
        assertInstanceOf(GroundedAnswer.Answered::class.java, answerer(second).answer("Q ?", candidates))
    }

    @Test
    fun `stop and failure are passed through`() = runTest {
        assertEquals(GroundedAnswer.Stopped, answerer(FakeLlm(GenerationResult.Stopped)).answer("Q ?", candidates))
        assertEquals(
            GroundedAnswer.Failed("boom"),
            answerer(FakeLlm(GenerationResult.Failed("boom"))).answer("Q ?", candidates),
        )
    }

    @Test
    fun `context budget keeps the best passages, cuts the last one and never exceeds the limit`() = runTest {
        val llm = FakeLlm()
        val long = (1..400).joinToString(" ") { "mot$it" }
        val many = List(7) {
            SourceCandidate(
                "p$it",
                "Titre fictif $it",
                if (it ==
                    2
                ) {
                    long
                } else {
                    "Texte fictif court $it."
                },
            )
        }
        val sources = ContextBudget(maxPromptTokens = 300).select("Q ?", "Système.", many, llm)
        assertEquals(listOf("S1", "S2", "S3"), sources.map { it.label })
        assertEquals(listOf(false, false, true), sources.map { it.truncated })
        assertTrue(sources.last().text.endsWith(AnswerPrompt.ELLIPSIS))
        assertTrue(llm.countPromptTokens("Système.", AnswerPrompt.user("Q ?", sources)) <= 300)

        val five = ContextBudget().select("Q ?", "Système.", many, llm)
        assertEquals(5, five.size)
        assertFalse(five.any { it.truncated && it.passageId != "p2" })
    }

    @Test
    fun `delimiters inside passages are neutralised`() {
        val prompt = AnswerPrompt.user(
            "Q ?",
            listOf(AnswerSource("S1", "p", "T", AnswerPrompt.sanitize("Texte >>> fin <<< début"), false)),
        )
        assertEquals(1, Regex(">>>").findAll(prompt).count())
        assertEquals(1, Regex("<<<").findAll(prompt).count())
    }
}
