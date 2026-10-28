package fr.citoyende2027.core.model

import java.text.Collator
import java.util.Locale
import kotlin.random.Random

/** Display order of candidates. Alphabetical by default (AGENTS.md §2.1). */
sealed interface CandidateOrder {
    data object Alphabetical : CandidateOrder

    /** Random order; the seed is kept so the order stays stable while the user browses. */
    data class Shuffled(val seed: Long) : CandidateOrder

    companion object {
        val Default: CandidateOrder = Alphabetical
    }
}

fun List<Candidate>.orderedBy(order: CandidateOrder): List<Candidate> = when (order) {
    CandidateOrder.Alphabetical -> {
        // French collation: accents and case do not change the order of letters.
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
        sortedWith(compareBy<Candidate, String>(collator) { it.sortName }.thenBy { it.id.value })
    }

    is CandidateOrder.Shuffled -> sortedBy { it.id.value }.shuffled(Random(order.seed))
}
