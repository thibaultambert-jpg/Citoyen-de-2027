package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId

/** Corpora of AGENTS.md §8. Structured data (mandates, votes) is never indexed: it is routed to SQL. */
enum class Corpus {
    PROGRAM,
    LEGAL,
}

/**
 * A retrievable passage: a programme excerpt or a legal article. [text] is what is indexed (title,
 * headings and verbatim body); the app shows the original record behind [id].
 */
data class Passage(val id: String, val corpus: Corpus, val candidateId: CandidateId?, val text: String) {
    init {
        require((corpus == Corpus.PROGRAM) == (candidateId != null)) {
            "Programme passages, and only them, have a candidate"
        }
    }
}

/** Mandatory metadata filter (AGENTS.md §8): a corpus, and for programmes the candidates allowed. */
data class PassageFilter(val corpus: Corpus, val candidates: Set<CandidateId>? = null) {
    fun accepts(passage: Passage): Boolean =
        passage.corpus == corpus && (candidates == null || passage.candidateId in candidates)
}
