package fr.citoyende2027.core.model

import java.text.Normalizer
import java.util.Locale

/** Normalisation used by every text search: lower case, no diacritics, single spaces. */
object SearchText {
    private val diacritics = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(diacritics, "")
        .replace('’', '\'')
        .lowercase(Locale.ROOT)
        .replace(spaces, " ")
        .trim()
}
