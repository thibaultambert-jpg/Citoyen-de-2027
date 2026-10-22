package fr.citoyende2027.rag.answer

import fr.citoyende2027.core.model.SearchText

/** A claim that passed the checks, with the sources it cites (at least one). */
data class VerifiedClaim(val text: String, val sources: List<AnswerSource>)

/** Why a claim was removed (counted by the benchmark, never shown as a judgement). */
enum class Rejection {
    NO_SOURCE,
    EMPTY,
    UNSUPPORTED_NUMBER,
    UNSUPPORTED_NAME,
    UNSUPPORTED_QUOTE,
    UNSUPPORTED_MONTH,
}

/**
 * Post-generation checks (AGENTS.md §8.8): each claim cites at least one existing source, and its
 * numbers, month names, proper nouns and quotations must be found in the cited passages (or their
 * titles), compared after normalisation. A claim that fails is removed; the displayed text is rebuilt
 * from the claims kept, never taken from the model's free text.
 */
object ClaimVerifier {
    private val labelRefs = Regex("""\s*\[\s*S\d+(\s*[,;]\s*S\d+)*\s*]""")
    private val numbers = Regex("""\d+(?:[.,]\d+)*""")
    private val quotes = Regex("""«\s*([^»]+?)\s*»|“([^”]+)”|"([^"]+)"""")
    private val words = Regex("""[\p{L}\p{N}]+""")
    private val sentenceStart = Regex("""(^|[.!?:;]\s+|[(«“"]\s*)$""")
    private val months = setOf(
        "janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre",
        "novembre", "decembre",
    )

    fun verify(output: ModelOutput, sources: List<AnswerSource>): Pair<List<VerifiedClaim>, List<Rejection>> {
        val byLabel = sources.associateBy { it.label }
        val kept = mutableListOf<VerifiedClaim>()
        val rejected = mutableListOf<Rejection>()
        for (claim in output.claims) {
            val cited = claim.sources.distinct().mapNotNull { byLabel[it] }
            val text = claim.text.replace(labelRefs, "").replace(Regex("\\s+"), " ").trim()
            val rejection = when {
                cited.isEmpty() -> Rejection.NO_SOURCE
                text.isEmpty() -> Rejection.EMPTY
                else -> check(text, cited)
            }
            if (rejection == null) {
                kept += VerifiedClaim(text, cited.sortedBy { sources.indexOf(it) })
            } else {
                rejected += rejection
            }
        }
        return kept.distinctBy { it.text } to rejected
    }

    private fun check(claim: String, cited: List<AnswerSource>): Rejection? {
        val support = SearchText.normalize(cited.joinToString("\n") { it.title + "\n" + it.text })
        val supportWords = words.findAll(support).map { it.value }.toSet()
        val supportNumbers = numbers.findAll(support).map { it.value }.toSet()

        if (numbers.findAll(claim).any { it.value !in supportNumbers }) return Rejection.UNSUPPORTED_NUMBER

        for (match in quotes.findAll(claim)) {
            val quoted = match.groupValues.drop(1).first { it.isNotEmpty() }
            if (SearchText.normalize(quoted) !in support) return Rejection.UNSUPPORTED_QUOTE
        }

        for (match in words.findAll(claim)) {
            val normalized = SearchText.normalize(match.value)
            if (normalized in months && normalized !in supportWords) return Rejection.UNSUPPORTED_MONTH
            val capitalised = match.value.first().isUpperCase()
            val atSentenceStart = sentenceStart.containsMatchIn(claim.substring(0, match.range.first))
            if (capitalised && !atSentenceStart && normalized !in supportWords) return Rejection.UNSUPPORTED_NAME
        }
        return null
    }
}
