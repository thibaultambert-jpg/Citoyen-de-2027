package fr.citoyende2027.core.model

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchTextTest {
    @Test
    fun `examples`() {
        assertEquals("l'election a lieu", SearchText.normalize("  L’Élection\n\ta  LIEU "))
        assertEquals("cœur ecole", SearchText.normalize("Cœur École"))
    }

    @Test
    fun `the one-pass normalisation equals the reference definition`() {
        val alphabet = listOf(
            "a", "Z", "é", "È", "à", "ç", "Ç", "œ", "Œ", "æ", "ß", "ï", "Ô", "’", "'", " ", "\t", "\n", "\r", "\u000B",
            "\u000C", " ", " ", "é", "́", "̧", "1", "²", "½", "İ", "ı", "Σ", "σ", "ς", "한",
            "ﬁ", "Å", "Ǆ", "😀", ".", "-", "«", "»",
        )
        val random = Random(2027)
        repeat(100_000) {
            val text = List(random.nextInt(0, 12)) { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
            assertEquals(SearchText.reference(text), SearchText.normalize(text), "for [$text]")
        }
    }
}
