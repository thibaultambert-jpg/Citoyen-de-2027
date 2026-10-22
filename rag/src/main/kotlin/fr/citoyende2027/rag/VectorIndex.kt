package fr.citoyende2027.rag

/** A semantic match: cosine similarity between the query and the passage embeddings. */
data class VectorHit(val passage: Passage, val similarity: Double)

/**
 * Embeds a question with the same model and quantisation as the pipeline (AGENTS.md §7.6). Returns an
 * L2-normalised vector, or null when no embedding model is available (lexical retrieval only).
 */
fun interface QueryEmbedder {
    fun embed(question: String): FloatArray?
}

/**
 * Linear scan over int8 vectors (L2-normalised then scaled by 127 in the pipeline), no ANN index
 * (AGENTS.md §8). [modelId] must equal the device embedder's model id: versions are never mixed.
 */
class VectorIndex(val modelId: String, val dimensions: Int, private val entries: List<Pair<Passage, ByteArray>>) {
    init {
        require(entries.all { it.second.size == dimensions }) { "Vectors must have $dimensions dimensions" }
    }

    fun search(query: FloatArray, filter: PassageFilter, limit: Int): List<VectorHit> {
        require(query.size == dimensions) { "Query has ${query.size} dimensions, expected $dimensions" }
        return entries.asSequence()
            .filter { filter.accepts(it.first) }
            .map { (passage, vector) ->
                var dot = 0.0
                for (i in 0 until dimensions) dot += query[i] * vector[i]
                VectorHit(passage, dot / 127.0)
            }
            .sortedWith(compareByDescending<VectorHit> { it.similarity }.thenBy { it.passage.id })
            .take(limit)
            .toList()
    }
}
