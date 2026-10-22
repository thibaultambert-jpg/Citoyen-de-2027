package fr.citoyende2027.rag

/** Reciprocal rank fusion (AGENTS.md §8, k = 60): score(d) = Σ 1 / (k + rank(d)), ranks from 1. */
fun reciprocalRankFusion(rankings: List<List<String>>, k: Int = 60): List<Pair<String, Double>> {
    val scores = LinkedHashMap<String, Double>()
    rankings.forEach { ranking ->
        ranking.forEachIndexed { index, id -> scores.merge(id, 1.0 / (k + index + 1), Double::plus) }
    }
    return scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }
}
