package fr.citoyende2027.rag

import fr.citoyende2027.core.model.SearchText

/**
 * Tokenisation shared by indexing and queries: normalised words (lower case, no diacritics), French
 * function words removed. Queries match longer words by prefix (a light stand-in for stemming:
 * "élection" also finds "élections" through the prefix "electi").
 */
object TextAnalyzer {
    private val word = Regex("[\\p{L}\\p{N}]+")

    /** Words that carry no meaning for retrieval. Kept short on purpose; reviewed with the golden set. */
    val stopwords: Set<String> = (
        "a au aux avec ce ces cet cette d de des du elle en et il ils je l la le les leur leurs lui " +
            "m ma mais me mes moi mon n ne ni nos notre nous on ou par pas pour qu que qui s sa se ses " +
            "si son sont sur ta te tes toi ton tu un une vos votre vous y est etre sont ete a ont " +
            "quel quelle quels quelles quoi comment combien ou quand pourquoi dont lequel laquelle " +
            "est-ce ce-que t il-y"
        ).split(' ').filter { it.isNotBlank() }.toSet()

    /**
     * Index tokens of a text, with repetitions (term frequencies): runs of `[\p{L}\p{N}]` in the
     * normalised text, function words removed. A code-point scan, equal to [tokensReference] but several
     * times faster when the index is built (J8).
     */
    fun tokens(text: String): List<String> {
        val normalized = SearchText.normalize(text)
        val tokens = ArrayList<String>()
        var i = 0
        while (i < normalized.length) {
            val codePoint = normalized.codePointAt(i)
            if (!isWordCharacter(codePoint)) {
                i += Character.charCount(codePoint)
                continue
            }
            val start = i
            while (i < normalized.length) {
                val next = normalized.codePointAt(i)
                if (!isWordCharacter(next)) break
                i += Character.charCount(next)
            }
            val token = normalized.substring(start, i)
            if (token !in stopwords) tokens += token
        }
        return tokens
    }

    /** The definition of [tokens] (regular expression), kept for the equivalence test. */
    internal fun tokensReference(text: String): List<String> =
        word.findAll(SearchText.normalize(text)).map { it.value }.filter { it !in stopwords }.toList()

    /** `\p{L}` or `\p{N}`. */
    private fun isWordCharacter(codePoint: Int): Boolean = Character.isLetter(codePoint) ||
        when (Character.getType(codePoint).toByte()) {
            Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
            else -> false
        }

    /** Distinct query terms, in order. */
    fun queryTerms(question: String, maxTerms: Int = 16): List<String> = tokens(question).distinct().take(maxTerms)

    /**
     * Prefix used to match a query term: whole term up to 5 characters, then the term minus 2. Terms with
     * a digit (article numbers such as "l49") are matched whole: "l49" must not find "l491".
     */
    fun stem(term: String): String = if (term.length <= 5 || term.any(Char::isDigit)) {
        term
    } else {
        term.take(maxOf(5, term.length - 2))
    }
}
