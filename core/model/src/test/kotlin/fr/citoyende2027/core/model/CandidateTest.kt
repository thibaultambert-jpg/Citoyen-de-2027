package fr.citoyende2027.core.model

import fr.citoyende2027.core.testing.FictionalCandidates
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CandidateTest {
    @Test
    fun `initials come from the displayed name`() {
        assertEquals("CA", FictionalCandidates.candidateA.initials)
        assertEquals("JD", FictionalCandidates.candidateA.copy(displayName = "Jeanne d'Arc-Dupont").initials)
    }

    @Test
    fun `coverage requires every facet`() {
        assertThrows<IllegalArgumentException> {
            DataCoverage(mapOf(CoverageFacet.PROGRAM to CoverageLevel.AVAILABLE))
        }
    }

    @Test
    fun `sources must be https`() {
        assertThrows<IllegalArgumentException> {
            SourceRef("Exemple", "http://example.invalid", LocalDate.of(2026, 1, 1), "Licence fictive", "0")
        }
    }
}
