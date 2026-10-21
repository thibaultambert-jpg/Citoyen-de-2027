package fr.citoyende2027.core.model

import fr.citoyende2027.core.testing.FictionalCandidates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTest {
    @Test
    fun `normalize removes accents case and extra spaces`() {
        assertEquals("eleonore d'arc", SearchText.normalize("  Éléonore   D’Arc "))
    }

    @Test
    fun `candidate matches on display name ignoring accents and case`() {
        val candidate = FictionalCandidates.candidateA.copy(displayName = "Éloïse Exemple")
        assertTrue(candidate.matches("eloise"))
        assertTrue(candidate.matches("EXEMPLE"))
        assertTrue(candidate.matches(""))
        assertFalse(candidate.matches("autre"))
    }
}
