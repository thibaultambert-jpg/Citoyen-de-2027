package fr.citoyende2027.rag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Retrieval settings, calibrated on pipeline/eval/golden.jsonl and shipped in the signed `core` pack
 * (meta `rag_config`), never hard-coded in the app (AGENTS.md §8, step 4).
 */
@Serializable
data class RagConfig(
    /** Passages kept per group (3 to 5 for the LLM context, AGENTS.md §8). */
    @SerialName("top_k") val topK: Int = 5,
    /** Candidates taken from each index before fusion. */
    @SerialName("pool_size") val poolSize: Int = 30,
    @SerialName("rrf_k") val rrfK: Int = 60,
    /** Abstention: the best passage must contain at least this many distinct query terms... */
    @SerialName("min_matched_terms") val minMatchedTerms: Int,
    /** ...and this share of them, unless its embedding similarity reaches [minSimilarity]. */
    @SerialName("min_coverage") val minCoverage: Double,
    @SerialName("min_similarity") val minSimilarity: Double = 2.0,
) {
    init {
        require(topK in 1..10 && poolSize >= topK && rrfK > 0) { "Invalid retrieval sizes" }
        require(minMatchedTerms >= 1 && minCoverage in 0.0..1.0) { "Invalid abstention thresholds" }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RagConfig = json.decodeFromString(serializer(), text)
    }
}
