package fr.citoyende2027.rag.answer

/** Why the assistant gave no answer; the app then shows the passages only, with the abstention text. */
enum class AbstentionReason {
    /** No passage fits the context budget. */
    NO_SOURCE,

    /** The model said the passages do not answer the question (answerable = false). */
    NOT_ANSWERABLE,

    /** Invalid output twice (strict JSON expected). */
    INVALID_OUTPUT,

    /** Every claim failed the citation checks. */
    UNSUPPORTED,
}

sealed interface GroundedAnswer {
    /** Claims kept after verification; [sources] are all the passages the model saw, in label order. */
    data class Answered(
        val claims: List<VerifiedClaim>,
        val sources: List<AnswerSource>,
        val rejected: List<Rejection>,
        val stats: GenerationStats?,
    ) : GroundedAnswer

    data class Abstained(val reason: AbstentionReason, val sources: List<AnswerSource>) : GroundedAnswer

    data object Stopped : GroundedAnswer

    data class Failed(val reason: String) : GroundedAnswer
}

/**
 * Answer chain with the local model (AGENTS.md §8, steps 6–8), run only after the refusal gate, the
 * routing and the abstention gate of [fr.citoyende2027.rag.RagEngine] let passages through. Stateless:
 * each question is independent (§8.10).
 */
class GroundedAnswerer(
    private val llm: LlmGenerator,
    private val systemPrompt: String,
    private val budget: ContextBudget = ContextBudget(),
) {
    suspend fun answer(
        question: String,
        candidates: List<SourceCandidate>,
        onText: (String) -> Unit = {},
    ): GroundedAnswer {
        val sources = budget.select(question, systemPrompt, candidates, llm)
        if (sources.isEmpty()) return GroundedAnswer.Abstained(AbstentionReason.NO_SOURCE, sources)
        val request = GenerationRequest(
            system = systemPrompt,
            user = AnswerPrompt.user(question, sources),
            grammar = AnswerGrammar.gbnf(sources.map { it.label }),
        )
        var lastStats: GenerationStats? = null
        repeat(ATTEMPTS) {
            when (val result = llm.generate(request, onText)) {
                GenerationResult.Stopped -> return GroundedAnswer.Stopped

                is GenerationResult.Failed -> return GroundedAnswer.Failed(result.reason)

                is GenerationResult.Completed -> {
                    lastStats = result.stats
                    val output = ModelOutput.parse(result.text) ?: return@repeat
                    if (!output.answerable) {
                        return GroundedAnswer.Abstained(AbstentionReason.NOT_ANSWERABLE, sources)
                    }
                    val (claims, rejected) = ClaimVerifier.verify(output, sources)
                    return if (claims.isEmpty()) {
                        GroundedAnswer.Abstained(AbstentionReason.UNSUPPORTED, sources)
                    } else {
                        GroundedAnswer.Answered(claims, sources, rejected, lastStats)
                    }
                }
            }
        }
        return GroundedAnswer.Abstained(AbstentionReason.INVALID_OUTPUT, sources)
    }

    private companion object {
        /** Strict JSON, one retry, then abstention (§8.7). */
        const val ATTEMPTS = 2
    }
}
