package fr.citoyende2027.core.model

import kotlin.math.ln

/**
 * Lexical search helpers shared by the legal corpus (J5) and, later, the RAG (J6). Indexed text and
 * queries go through [SearchText.normalize], so accents and case never change a result, whatever
 * the SQLite version of the device.
 */
object FullTextSearch {
    private val word = Regex("[\\p{L}\\p{N}]+")

    /** Normalised terms of a user query, at most [maxTerms]. */
    fun terms(query: String, maxTerms: Int = 12): List<String> =
        word.findAll(SearchText.normalize(query)).map { it.value }.distinct().take(maxTerms).toList()

    /**
     * SQLite FTS MATCH expression: every term must appear, the last one as a prefix (search as you
     * type). Terms only contain letters and digits, so no FTS syntax can be injected. Null when the
     * query has no term.
     */
    fun matchExpression(query: String): String? {
        val terms = terms(query).ifEmpty { return null }
        return terms.mapIndexed { index, term -> if (index == terms.lastIndex) "$term*" else term }.joinToString(" ")
    }

    /**
     * Okapi BM25 from an FTS4 `matchinfo(table, 'pcnalx')` result, as 32-bit integers.
     * Columns whose average length is 0 (not indexed) are skipped.
     */
    fun bm25(matchInfo: IntArray, k1: Double = 1.2, b: Double = 0.75): Double {
        val phrases = matchInfo[0]
        val columns = matchInfo[1]
        val rows = matchInfo[2].toDouble()
        val averages = 3
        val lengths = averages + columns
        val hits = lengths + columns
        var score = 0.0
        for (phrase in 0 until phrases) {
            for (column in 0 until columns) {
                val average = matchInfo[averages + column].toDouble()
                if (average <= 0.0) continue
                val base = hits + 3 * (column + phrase * columns)
                val frequency = matchInfo[base].toDouble()
                if (frequency == 0.0) continue
                val documents = matchInfo[base + 2].toDouble()
                val idf = ln((rows - documents + 0.5) / (documents + 0.5) + 1.0)
                val length = matchInfo[lengths + column].toDouble()
                score += idf * frequency * (k1 + 1) / (frequency + k1 * (1 - b + b * length / average))
            }
        }
        return score
    }

    /**
     * Verbatim excerpt of [text] around the first occurrence of a term (accent-insensitive), cut at
     * word boundaries, with "…" where text is left out. The excerpt itself is never altered.
     */
    fun excerpt(text: String, terms: List<String>, maxLength: Int = 240): String {
        if (text.length <= maxLength) return text
        val folded = foldPreservingLength(text)
        val position = terms.mapNotNull { term -> folded.indexOf(term).takeIf { it >= 0 } }.minOrNull() ?: 0
        var start = (position - maxLength / 3).coerceAtLeast(0)
        var end = (start + maxLength).coerceAtMost(text.length)
        start = (end - maxLength).coerceAtLeast(0)
        if (start > 0) start = text.indexOf(' ', start).let { if (it in start until position) it + 1 else start }
        if (end < text.length) end = text.lastIndexOf(' ', end).let { if (it > position) it else end }
        return buildString {
            if (start > 0) append("… ")
            append(text, start, end)
            if (end < text.length) append(" …")
        }
    }

    /** Lower case without diacritics, one output character per input character (offsets match). */
    private fun foldPreservingLength(text: String): String = buildString(text.length) {
        text.forEach { char ->
            val folded = SearchText.normalize(char.toString())
            append(if (folded.length == 1) folded[0] else char.lowercaseChar())
        }
    }
}
