package fr.citoyende2027.core.model

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Normalisation used by every text search: lower case, no diacritics, single spaces. */
object SearchText {
    private val diacritics = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    /** Decomposed, mark-free, lower-case form of each non-ASCII character met so far. */
    private val folded = ConcurrentHashMap<Char, String>()

    /**
     * Same result as [reference], in one pass: indexing every passage goes through here, and the
     * whole-text Unicode normalisation was the main cost of building the search index (J8).
     */
    fun normalize(text: String): String {
        // Context-dependent cases (supplementary characters, Greek final sigma): reference path.
        if (text.any { it.isSurrogate() || it == 'Σ' }) return reference(text)
        val out = StringBuilder(text.length)
        for (c in text) {
            when {
                c == '’' -> out.append('\'')
                c.code < 0x80 -> out.append(if (c in 'A'..'Z') c + ('a' - 'A') else c)
                else -> out.append(folded.getOrPut(c) { fold(c) })
            }
        }
        return collapseSpaces(out).trim()
    }

    /** The definition: NFD, marks removed, typographic apostrophe, lower case, `\s+` collapsed, trimmed. */
    fun reference(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(diacritics, "")
        .replace('’', '\'')
        .lowercase(Locale.ROOT)
        .replace(spaces, " ")
        .trim()

    private fun fold(c: Char): String =
        Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(diacritics, "").lowercase(Locale.ROOT)

    /** Java's `\s` (without UNICODE_CHARACTER_CLASS): space, \t, \n, \u000B, \f, \r. */
    private fun isRegexSpace(c: Char) = c == ' ' || c in '\t'..'\r'

    private fun collapseSpaces(text: CharSequence): String {
        val out = StringBuilder(text.length)
        var inSpaces = false
        for (c in text) {
            if (isRegexSpace(c)) {
                if (!inSpaces) out.append(' ')
                inSpaces = true
            } else {
                out.append(c)
                inSpaces = false
            }
        }
        return out.toString()
    }
}
