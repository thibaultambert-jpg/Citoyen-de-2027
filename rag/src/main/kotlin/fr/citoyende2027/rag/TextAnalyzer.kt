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

    /** Index tokens of a text, with repetitions (term frequencies). */
    fun tokens(text: String): List<String> =
        word.findAll(SearchText.normalize(text)).map { it.value }.filter { it !in stopwords }.toList()

    /** Distinct query terms, in order. */
    fun queryTerms(question: String, maxTerms: Int = 16): List<String> = tokens(question).distinct().take(maxTerms)

    /** Prefix used to match a query term: whole term up to 5 characters, then the term minus 2. */
    fun stem(term: String): String = if (term.length <= 5 ||
        term.all(Char::isDigit)
    ) {
        term
    } else {
        term.take(maxOf(5, term.length - 2))
    }
}
