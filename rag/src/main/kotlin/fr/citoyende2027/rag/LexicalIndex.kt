package fr.citoyende2027.rag

import kotlin.math.ln

/** A lexical match: BM25 score and the number of distinct query terms found. */
data class LexicalHit(val passage: Passage, val score: Double, val matchedTerms: Int, val queryTerms: Int) {
    /** Share of the query terms present in the passage. */
    val coverage: Double get() = if (queryTerms == 0) 0.0 else matchedTerms.toDouble() / queryTerms
}

/**
 * In-memory BM25 index (Okapi, k1 = 1.2, b = 0.75). A query term matches every indexed word starting
 * with its stem ([TextAnalyzer.stem]); its frequency in a passage is the sum over those words.
 */
class LexicalIndex(passages: List<Passage>, private val k1: Double = 1.2, private val b: Double = 0.75) {
    private val docs = passages.toList()
    private val lengths = IntArray(docs.size)

    /** Term → (document, term frequency) pairs, flattened, in document order (no boxing). */
    private val postings: Map<String, IntArray>
    private val vocabulary: List<String>
    private val averageLength: Double

    init {
        val builders = HashMap<String, IntPairs>()
        val counts = HashMap<String, Int>()
        docs.forEachIndexed { index, passage ->
            val tokens = TextAnalyzer.tokens(passage.text)
            lengths[index] = tokens.size
            counts.clear()
            tokens.forEach { token -> counts[token] = (counts[token] ?: 0) + 1 }
            counts.forEach { (token, tf) -> builders.getOrPut(token) { IntPairs() }.add(index, tf) }
        }
        postings = builders.mapValues { it.value.toArray() }
        vocabulary = postings.keys.sorted()
        averageLength = if (docs.isEmpty()) 0.0 else lengths.average().coerceAtLeast(1.0)
    }

    /** Growable array of (document, frequency) pairs. */
    private class IntPairs {
        private var values = IntArray(4)
        private var size = 0

        fun add(doc: Int, tf: Int) {
            if (size + 2 > values.size) values = values.copyOf(values.size * 2)
            values[size++] = doc
            values[size++] = tf
        }

        fun toArray(): IntArray = values.copyOf(size)
    }

    val size: Int get() = docs.size

    fun search(question: String, filter: PassageFilter, limit: Int): List<LexicalHit> {
        val terms = TextAnalyzer.queryTerms(question)
        if (terms.isEmpty() || docs.isEmpty()) return emptyList()
        val scores = HashMap<Int, Double>()
        val matched = HashMap<Int, Int>()
        for (term in terms) {
            val frequencies = HashMap<Int, Int>()
            wordsFor(term).forEach { word ->
                val pairs = postings[word] ?: return@forEach
                for (i in pairs.indices step 2) frequencies.merge(pairs[i], pairs[i + 1], Int::plus)
            }
            if (frequencies.isEmpty()) continue
            val df = frequencies.size.toDouble()
            val idf = ln((docs.size - df + 0.5) / (df + 0.5) + 1.0)
            frequencies.forEach { (doc, tf) ->
                if (!filter.accepts(docs[doc])) return@forEach
                val norm = tf * (k1 + 1) / (tf + k1 * (1 - b + b * lengths[doc] / averageLength))
                scores.merge(doc, idf * norm, Double::plus)
                matched.merge(doc, 1, Int::plus)
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { docs[it.key].id })
            .take(limit)
            .map { (doc, score) -> LexicalHit(docs[doc], score, matched.getValue(doc), terms.size) }
    }

    private fun wordsFor(term: String): List<String> {
        val stem = TextAnalyzer.stem(term)
        if (stem == term && term.length <= 5) return listOfNotNull(term.takeIf { it in postings })
        var index = vocabulary.binarySearch(stem).let { if (it < 0) -it - 1 else it }
        val words = mutableListOf<String>()
        while (index < vocabulary.size && vocabulary[index].startsWith(stem)) words += vocabulary[index++]
        return words
    }
}
