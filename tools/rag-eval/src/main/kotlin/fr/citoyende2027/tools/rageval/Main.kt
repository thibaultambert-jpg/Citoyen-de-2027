package fr.citoyende2027.tools.rageval

import fr.citoyende2027.rag.RagConfig
import fr.citoyende2027.rag.RagResult
import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json

private val pretty = Json { prettyPrint = true }

/**
 * Evaluates the retrieval chain on the packs of `--packs` (AGENTS.md §7.7). `--calibrate=true` prints
 * the metrics for a grid of abstention thresholds instead, to choose pipeline/inputs/rag_config.json.
 */
fun main(args: Array<String>) {
    val options = args.associate { arg -> arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=', "") }
    val root = File(options["root"] ?: ".")
    fun file(name: String, default: String) = File(options[name] ?: File(root, default).path)
    val corpus = PackLoader.load(File(checkNotNull(options["packs"]) { "--packs=<directory> is required" }))
    val questions = parseGolden(file("golden", "pipeline/eval/golden.jsonl").readLines())
    val config = options["ragConfig"]?.let { RagConfig.parse(File(it).readText()) }
        ?: corpus.ragConfig?.let(RagConfig::parse)
        ?: RagConfig.parse(file("ragConfig", "pipeline/inputs/rag_config.json").readText())
    println(
        "Corpus: ${corpus.passages.size} passages, ${corpus.candidates.size} candidates; ${questions.size} questions",
    )

    if (options["calibrate"] == "true") {
        for (terms in 1..3) {
            for (coverage in listOf(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)) {
                val (metrics, _) = evaluate(
                    corpus,
                    config.copy(minMatchedTerms = terms, minCoverage = coverage),
                    questions,
                )
                println(
                    "min_matched_terms=$terms min_coverage=$coverage -> recall@5=%.3f abstention=%.3f answerable_abstained=%.3f"
                        .format(metrics.recallAt5, metrics.abstentionCorrect, metrics.answerableAbstained),
                )
            }
        }
        return
    }

    val (metrics, outcomes) = evaluate(corpus, config, questions)
    println(pretty.encodeToString(Metrics.serializer(), metrics))
    outcomes.filterNot { it.passed }.forEach { outcome ->
        val got = when (val result = outcome.result) {
            is RagResult.Passages ->
                "passages " +
                    result.groups.flatMap { g -> g.passages.map { it.passage.id } }.take(5)

            else -> result.toString()
        }
        println("FAILED ${outcome.question.id} [${outcome.question.kind}] ${outcome.question.question} -> $got")
    }
    options["report"]?.let { File(it).writeText(pretty.encodeToString(Metrics.serializer(), metrics)) }
    val thresholds = Json.decodeFromString(
        Thresholds.serializer(),
        file("thresholds", "pipeline/eval/thresholds.json").readText(),
    )
    val failures = thresholds.failures(metrics)
    if (failures.isNotEmpty()) {
        System.err.println("Evaluation below thresholds: $failures")
        exitProcess(1)
    }
    println("Evaluation passed.")
}
