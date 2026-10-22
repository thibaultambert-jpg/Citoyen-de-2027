package fr.citoyende2027.llm.benchmark

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.llm.BuildConfig
import fr.citoyende2027.llm.model.ModelStores
import fr.citoyende2027.llm.service.LlmClient
import fr.citoyende2027.rag.answer.AnswerGrammar
import fr.citoyende2027.rag.answer.AnswerPrompt
import fr.citoyende2027.rag.answer.AnswerSource
import fr.citoyende2027.rag.answer.GenerationRequest
import fr.citoyende2027.rag.answer.GenerationResult
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One benchmark run (AGENTS.md §9): time to first token, generation speed and peak memory. */
data class BenchmarkReport(
    val modelFile: String,
    val promptTokens: Int,
    val timeToFirstTokenMs: Long,
    val tokens: Int,
    val tokensPerSecond: Double,
    val peakResidentMb: Long,
    val threads: Int,
    val cpuFeatures: String,
    val outcome: String,
)

/** Debug screen only: [isAvailable] is false in release builds. */
interface Benchmark {
    val isAvailable: Boolean

    /** Benchmark files copied into the model directory, to compare quantisations (Q4_K_M, Q4_0…). */
    fun files(): List<String>

    suspend fun importFile(uri: Uri, name: String): String?

    /** [file] null: the installed model. */
    suspend fun run(file: String?): BenchmarkReport
}

internal class LlmBenchmark @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: LlmClient,
) : Benchmark {
    override val isAvailable: Boolean = BuildConfig.DEBUG

    private val directory: File get() = ModelStores.directory(context)

    override fun files(): List<String> =
        directory.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(".gguf") }
            ?.map { it.name }?.sorted().orEmpty()

    override suspend fun importFile(uri: Uri, name: String): String? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        val safe = PREFIX + name.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".gguf") + ".gguf"
        directory.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { File(directory, safe).outputStream().use { output -> it.copyTo(output, 1 shl 20) } }
        safe
    }

    override suspend fun run(file: String?): BenchmarkReport {
        // Fixed, fictional prompt of a realistic size (AGENTS.md §2.6): same work for every file.
        val sources = (1..4).map { i ->
            AnswerSource("S$i", "fictif-$i", "Texte fictif $i", FICTIONAL_PASSAGE, truncated = false)
        }
        val request = GenerationRequest(
            system = "Tu es un assistant de test. Réponds uniquement avec les extraits.",
            user = AnswerPrompt.user("Que prévoit le texte fictif sur la règle fictive ?", sources),
            grammar = AnswerGrammar.gbnf(sources.map { it.label }),
        )
        val run = client.generateDetailed(request, modelFile = file)
        val stats = (run.result as? GenerationResult.Completed)?.stats
        return BenchmarkReport(
            modelFile = file ?: ModelStores.spec.url.substringAfterLast('/'),
            promptTokens = stats?.promptTokens ?: 0,
            timeToFirstTokenMs = stats?.timeToFirstTokenMs ?: -1,
            tokens = stats?.tokens ?: 0,
            tokensPerSecond = stats?.tokensPerSecond ?: 0.0,
            peakResidentMb = if (run.peakResidentKb > 0) run.peakResidentKb / 1024 else -1,
            threads = run.threads,
            cpuFeatures = run.cpuFeatures,
            outcome = when (val result = run.result) {
                is GenerationResult.Completed -> "ok"
                GenerationResult.Stopped -> "stopped"
                is GenerationResult.Failed -> result.reason
            },
        )
    }

    private companion object {
        const val PREFIX = "bench-"
        val FICTIONAL_PASSAGE = List(12) {
            "La règle fictive numéro $it s'applique au territoire fictif selon les modalités du texte fictif."
        }.joinToString(" ")
    }
}
