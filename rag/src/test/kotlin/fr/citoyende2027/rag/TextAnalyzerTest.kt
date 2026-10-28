package fr.citoyende2027.rag

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextAnalyzerTest {
    @Test
    fun `the code-point scan equals the regular expression`() {
        val alphabet = listOf(
            "Le", "la", " ", "-", "’", "'", ".", ",", "é", "œ", "1", "²", "½", "Ⅻ", "١", "é", "😀", "𝐀", " ",
            "article", "L49", "_", "«", "»", "\n", "한", "ﬁ",
        )
        val random = Random(8)
        repeat(50_000) {
            val text = List(random.nextInt(0, 15)) { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
            assertEquals(TextAnalyzer.tokensReference(text), TextAnalyzer.tokens(text), "for [$text]")
        }
    }
}
