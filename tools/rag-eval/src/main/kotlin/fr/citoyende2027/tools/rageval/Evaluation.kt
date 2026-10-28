package fr.citoyende2027.tools.rageval

import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.rag.IntentRouter
import fr.citoyende2027.rag.LexicalIndex
import fr.citoyende2027.rag.RagConfig
import fr.citoyende2027.rag.RagEngine
import fr.citoyende2027.rag.RagResult
import fr.citoyende2027.rag.RagScope
import fr.citoyende2027.rag.RefusalGate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Question kinds of AGENTS.md §7.7 (40 / 30 / 15 / 15 %). */
enum class Kind {
    @SerialName("answerable")
    ANSWERABLE,

    @SerialName("unanswerable")
    UNANSWERABLE,

    @SerialName("opinion")
    OPINION,

    @SerialName("attack")
    ATTACK,
}

/**
 * One line of pipeline/eval/golden.jsonl. [scope]: `legal`, `candidate:<id>` or `programs:<id>,<id>`.
 * [expected]: passage ids of which at least one must be in the top 5 (answerable questions).
 */
@Serializable
data class GoldenQuestion(
    val id: String,
    val kind: Kind,
    val scope: String,
    val question: String,
    val expected: List<String> = emptyList(),
) {
    fun ragScope(): RagScope = when {
        scope == "legal" -> RagScope.Legal

        scope.startsWith("candidate:") -> RagScope.Candidate(CandidateId(scope.removePrefix("candidate:")))

        scope.startsWith(
            "programs:",
        ) -> RagScope.Programs(scope.removePrefix("programs:").split(',').map(::CandidateId))

        else -> error("Unknown scope $scope in $id")
    }
}

@Serializable
data class Metrics(
    @SerialName("recall_at_5") val recallAt5: Double,
    @SerialName("abstention_correct") val abstentionCorrect: Double,
    @SerialName("refusal_correct") val refusalCorrect: Double,
    @SerialName("attack_blocked") val attackBlocked: Double,
    /** Answerable questions wrongly refused or abstained on (not counted in recall's numerator). */
    @SerialName("answerable_refused") val answerableRefused: Double,
    @SerialName("answerable_abstained") val answerableAbstained: Double,
    val counts: Map<String, Int>,
)

data class Outcome(val question: GoldenQuestion, val result: RagResult, val passed: Boolean)

private val json = Json { ignoreUnknownKeys = false }

fun parseGolden(lines: List<String>): List<GoldenQuestion> = lines.map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("//") }
    .map { json.decodeFromString(GoldenQuestion.serializer(), it) }
    .also { questions ->
        val ids = questions.map { it.id }
        require(ids.size == ids.toSet().size) { "Duplicate golden ids" }
        require(questions.filter { it.kind == Kind.ANSWERABLE }.all { it.expected.isNotEmpty() }) {
            "Every answerable question needs expected passages"
        }
    }

/** Runs the whole chain (gates included) on every question. */
fun evaluate(corpus: EvalCorpus, config: RagConfig, questions: List<GoldenQuestion>): Pair<Metrics, List<Outcome>> {
    val engine = RagEngine(
        config = config,
        lexical = LexicalIndex(corpus.passages),
        refusal = RefusalGate.bundled(),
        router = IntentRouter(corpus.candidates),
    )
    val known = corpus.passages.map { it.id }.toSet()
    questions.flatMap { it.expected }.filterNot { it in known }.let { missing ->
        require(missing.isEmpty()) { "Expected passages missing from the packs: $missing" }
    }
    val outcomes = questions.map { question ->
        val result = engine.answer(question.question, question.ragScope())
        val passed = when (question.kind) {
            Kind.ANSWERABLE ->
                result is RagResult.Passages &&
                    result.groups.any { group -> group.passages.take(5).any { it.passage.id in question.expected } }

            Kind.UNANSWERABLE -> result is RagResult.Abstained

            Kind.OPINION, Kind.ATTACK -> result is RagResult.Refused
        }
        Outcome(question, result, passed)
    }
    fun rate(kind: Kind, predicate: (Outcome) -> Boolean): Double {
        val ofKind = outcomes.filter { it.question.kind == kind }
        return if (ofKind.isEmpty()) 1.0 else ofKind.count(predicate).toDouble() / ofKind.size
    }
    val metrics = Metrics(
        recallAt5 = rate(Kind.ANSWERABLE) { it.passed },
        abstentionCorrect = rate(Kind.UNANSWERABLE) { it.passed },
        refusalCorrect = rate(Kind.OPINION) { it.passed },
        attackBlocked = rate(Kind.ATTACK) { it.passed },
        answerableRefused = rate(Kind.ANSWERABLE) { it.result is RagResult.Refused },
        answerableAbstained = rate(Kind.ANSWERABLE) { it.result is RagResult.Abstained },
        counts = Kind.entries.associate { kind -> kind.name.lowercase() to questions.count { it.kind == kind } },
    )
    return metrics to outcomes
}

/** Minimum values of each metric (pipeline/eval/thresholds.json); the publication fails below them. */
@Serializable
data class Thresholds(
    @SerialName("recall_at_5") val recallAt5: Double,
    @SerialName("abstention_correct") val abstentionCorrect: Double,
    @SerialName("refusal_correct") val refusalCorrect: Double,
    @SerialName("attack_blocked") val attackBlocked: Double,
    @SerialName("answerable_refused_max") val answerableRefusedMax: Double,
) {
    fun failures(metrics: Metrics): List<String> = buildList {
        if (metrics.recallAt5 < recallAt5) add("recall@5 ${metrics.recallAt5} < $recallAt5")
        if (metrics.abstentionCorrect <
            abstentionCorrect
        ) {
            add("abstention ${metrics.abstentionCorrect} < $abstentionCorrect")
        }
        if (metrics.refusalCorrect < refusalCorrect) add("refusal ${metrics.refusalCorrect} < $refusalCorrect")
        if (metrics.attackBlocked < attackBlocked) add("attacks ${metrics.attackBlocked} < $attackBlocked")
        if (metrics.answerableRefused > answerableRefusedMax) {
            add("answerable refused ${metrics.answerableRefused} > $answerableRefusedMax")
        }
    }
}
