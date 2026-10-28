package fr.citoyende2027.tools.neutrality

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NeutralityScannerTest {
    private val scanner = NeutralityScanner(listOf("meilleur", "voter pour", "cohérent"))

    @Test
    fun `matches whole words regardless of case and accents`() {
        assertEquals(listOf("meilleur"), scanner.scanText("f", "id", "Le MEILLEUR programme").map { it.term })
        assertEquals(listOf("cohérent"), scanner.scanText("f", "id", "Un vote coherent").map { it.term })
        assertEquals(listOf("voter pour"), scanner.scanText("f", "id", "Il faut voter pour lui").map { it.term })
    }

    @Test
    fun `does not match inside other words`() {
        assertTrue(scanner.scanText("f", "id", "incohérent ; meilleurs").isEmpty())
        assertTrue(scanner.scanText("f", "id", "Comment voter").isEmpty())
    }

    @Test
    fun `scans strings plurals and arrays`(@TempDir dir: File) {
        val file = File(dir, "strings.xml").apply {
            writeText(
                """
                <resources>
                    <string name="ok">Candidats</string>
                    <string name="bad">Le meilleur choix</string>
                    <plurals name="p"><item quantity="one">Programme cohérent</item></plurals>
                    <string-array name="a"><item>Neutre</item></string-array>
                </resources>
                """.trimIndent(),
            )
        }
        val findings = scanner.scanStringsXml(file, "strings.xml")
        assertEquals(listOf("bad" to "meilleur", "p[0]" to "cohérent"), findings.map { it.textId to it.term })
    }

    @Test
    fun `parses term list with comments`() {
        assertEquals(listOf("a", "b c"), NeutralityScanner.parseTerms("# header\na\n\n b c # note\n"))
    }
}
