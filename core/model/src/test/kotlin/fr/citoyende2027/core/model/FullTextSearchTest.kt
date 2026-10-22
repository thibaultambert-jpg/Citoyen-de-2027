package fr.citoyende2027.core.model

import kotlin.math.ln
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FullTextSearchTest {
    @Test
    fun `terms are normalised and FTS syntax cannot be injected`() {
        assertEquals(listOf("election", "du", "president"), FullTextSearch.terms("Élection du\u00a0Président"))
        // Quotes, stars and dashes are dropped; operators lose their meaning once lower-cased.
        assertEquals("texte or fictif*", FullTextSearch.matchExpression("\"texte\" OR- fictif*"))
        assertNull(FullTextSearch.matchExpression(" ** \"\" "))
    }

    @Test
    fun `bm25 follows the Okapi formula on matchinfo pcnalx`() {
        // 1 phrase, 2 columns (the first not indexed), 10 rows, average lengths [0, 20], row lengths [0, 10],
        // hits: column 0 none; column 1: 2 in this row, 5 in all rows, 3 rows with a hit.
        val info = intArrayOf(1, 2, 10, 0, 20, 0, 10, 0, 0, 0, 2, 5, 3)
        val idf = ln((10 - 3 + 0.5) / (3 + 0.5) + 1)
        val expected = idf * 2 * 2.2 / (2 + 1.2 * (1 - 0.75 + 0.75 * 10 / 20.0))
        assertEquals(expected, FullTextSearch.bm25(info), 1e-9)
        val fewerHits = info.copyOf().also { it[10] = 1 }
        assertTrue(FullTextSearch.bm25(fewerHits) < FullTextSearch.bm25(info))
    }

    @Test
    fun `excerpt is verbatim around the first match`() {
        val text =
            (1..60).joinToString(" ") { "mot$it" } + " Élection fictive " + (61..120).joinToString(" ") { "mot$it" }
        val excerpt = FullTextSearch.excerpt(text, listOf("election"), maxLength = 80)
        assertTrue(excerpt.startsWith("… ") && excerpt.endsWith(" …"), excerpt)
        assertTrue("Élection fictive" in excerpt, excerpt)
        assertTrue(text.contains(excerpt.removePrefix("… ").removeSuffix(" …")))
        assertEquals("Texte court.", FullTextSearch.excerpt("Texte court.", listOf("texte")))
    }
}
