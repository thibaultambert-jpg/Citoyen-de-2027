package fr.citoyende2027.rag

import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.SearchText

/** Why a question gets the fixed neutral answer instead of retrieval (AGENTS.md §8, step 1). */
enum class RefusalCategory {
    VOTE_ADVICE,
    OPINION,
    PREDICTION,
    EVALUATIVE_COMPARISON,

    /** An instruction aimed at the assistant (prompt injection in the question). */
    INSTRUCTION,
}

/** Lower case, no diacritics, punctuation replaced by spaces: the form the gate rules are written for. */
internal fun ruleText(question: String): String =
    SearchText.normalize(question).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Deterministic refusal gate, applied before anything else. Rules: `refusal_fr.txt`. */
class RefusalGate(private val rules: List<Pair<RefusalCategory, Regex>>) {
    fun check(question: String): RefusalCategory? {
        val text = ruleText(question)
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first
    }

    companion object {
        fun parse(lines: List<String>): RefusalGate = RefusalGate(
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val (category, pattern) = line.split('\t', limit = 2)
                    RefusalCategory.valueOf(category) to Regex(pattern)
                },
        )

        /** Rules shipped with the app. */
        fun bundled(): RefusalGate = parse(
            checkNotNull(RefusalGate::class.java.getResourceAsStream("refusal_fr.txt")) { "Missing refusal rules" }
                .bufferedReader(Charsets.UTF_8).readLines(),
        )
    }
}

/** A candidate as the router knows it: names used to recognise a structured question. */
data class CandidateName(val id: CandidateId, val displayName: String)

/** Structured questions answered from the data with fixed templates, never by retrieval or an LLM. */
sealed interface StructuredIntent {
    val candidateId: CandidateId

    data class Mandates(override val candidateId: CandidateId) : StructuredIntent

    data class Votes(override val candidateId: CandidateId) : StructuredIntent
}

/**
 * Routing (AGENTS.md §8, step 2): a question naming exactly one candidate and asking about mandates or
 * votes goes to the structured data. A candidate is recognised by the full displayed name, or by the
 * last word of that name when no other candidate shares it.
 */
class IntentRouter(candidates: List<CandidateName>) {
    private val fullNames = candidates.map { ruleText(it.displayName) to it.id }
    private val lastNames = candidates.groupBy { ruleText(it.displayName).substringAfterLast(' ') }
        .filter { (name, group) -> group.size == 1 && name.length >= 3 }
        .mapValues { (_, group) -> group.single().id }

    private val votes = Regex("\\b(vote|votes|voté|vot\\w*|scrutin|scrutins)\\b")
    private val mandates =
        Regex(
            "\\b(mandat|mandats|elu|elue|elus|fonction|fonctions|depute|deputee|senateur|senatrice|ministre|maire|eurodepute)\\b",
        )

    fun route(question: String): StructuredIntent? {
        val text = " ${ruleText(question)} "
        val named = (
            fullNames.filter { (name, _) -> " $name " in text }.map { it.second } +
                lastNames.filter { (name, _) -> " $name " in text }.map { it.value }
            ).distinct()
        val candidate = named.singleOrNull() ?: return null
        return when {
            votes.containsMatchIn(text) -> StructuredIntent.Votes(candidate)
            mandates.containsMatchIn(text) -> StructuredIntent.Mandates(candidate)
            else -> null
        }
    }
}
