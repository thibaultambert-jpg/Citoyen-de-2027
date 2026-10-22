package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId

/** What the question is asked about (AGENTS.md §11, "Poser une question"). */
sealed interface RagScope {
    /** Constitution and the other legal texts. */
    data object Legal : RagScope

    data class Candidate(val id: CandidateId) : RagScope

    /** Programmes of several candidates, side by side: same k per candidate, in the given order. */
    data class Programs(val candidates: List<CandidateId>) : RagScope
}

/** A retrieved passage with its fused rank score and the signals used by the abstention gate. */
data class RankedPassage(val passage: Passage, val score: Double, val lexical: LexicalHit?, val vector: VectorHit?)

/** Passages for one candidate (programmes) or for the legal corpus; [abstained] when none is relevant. */
data class PassageGroup(val candidateId: CandidateId?, val passages: List<RankedPassage>) {
    val abstained: Boolean get() = passages.isEmpty()
}

sealed interface RagResult {
    data class Refused(val category: RefusalCategory) : RagResult

    data class Structured(val intent: StructuredIntent) : RagResult

    /** Nothing relevant enough in the app's documents: no passage is shown, no LLM is called. */
    data object Abstained : RagResult

    data class Passages(val groups: List<PassageGroup>) : RagResult
}

/**
 * Retrieval chain without LLM (AGENTS.md §8): refusal gate → routing → hybrid retrieval with mandatory
 * filters (lexical BM25 and, when available, vectors; RRF fusion) → abstention gate per group.
 */
class RagEngine(
    private val config: RagConfig,
    private val lexical: LexicalIndex,
    private val refusal: RefusalGate,
    private val router: IntentRouter,
    private val vectors: VectorIndex? = null,
    private val embedder: QueryEmbedder? = null,
) {
    fun answer(question: String, scope: RagScope): RagResult {
        refusal.check(question)?.let { return RagResult.Refused(it) }
        router.route(question)?.let { return RagResult.Structured(it) }
        val query = if (vectors != null) embedder?.embed(question) else null
        val groups = when (scope) {
            RagScope.Legal -> listOf(retrieve(question, query, PassageFilter(Corpus.LEGAL), null))

            is RagScope.Candidate -> listOf(
                retrieve(question, query, PassageFilter(Corpus.PROGRAM, setOf(scope.id)), scope.id),
            )

            is RagScope.Programs -> scope.candidates.map { id ->
                retrieve(question, query, PassageFilter(Corpus.PROGRAM, setOf(id)), id)
            }
        }
        return if (groups.all { it.abstained }) RagResult.Abstained else RagResult.Passages(groups)
    }

    private fun retrieve(
        question: String,
        query: FloatArray?,
        filter: PassageFilter,
        candidate: CandidateId?,
    ): PassageGroup {
        val lexicalHits = lexical.search(question, filter, config.poolSize)
        val vectorHits = if (query != null &&
            vectors != null
        ) {
            vectors.search(query, filter, config.poolSize)
        } else {
            emptyList()
        }
        val byId = lexicalHits.associateBy { it.passage.id }
        val vectorById = vectorHits.associateBy { it.passage.id }
        val fused = reciprocalRankFusion(
            listOf(lexicalHits.map { it.passage.id }, vectorHits.map { it.passage.id }),
            config.rrfK,
        ).take(config.topK).map { (id, score) ->
            val lexicalHit = byId[id]
            val vectorHit = vectorById[id]
            RankedPassage(lexicalHit?.passage ?: vectorHit!!.passage, score, lexicalHit, vectorHit)
        }
        val relevant = fused.isNotEmpty() && fused.any(::passesAbstention)
        return PassageGroup(candidate, if (relevant) fused else emptyList())
    }

    private fun passesAbstention(passage: RankedPassage): Boolean {
        val lexicalOk = passage.lexical?.let {
            it.matchedTerms >= config.minMatchedTerms && it.coverage >= config.minCoverage
        } ?: false
        val vectorOk = (passage.vector?.similarity ?: -1.0) >= config.minSimilarity
        return lexicalOk || vectorOk
    }
}
