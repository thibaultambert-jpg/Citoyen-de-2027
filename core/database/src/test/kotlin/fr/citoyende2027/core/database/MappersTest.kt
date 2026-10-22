package fr.citoyende2027.core.database

import fr.citoyende2027.core.database.model.CandidateCoverageEntity
import fr.citoyende2027.core.database.model.CandidateWithCoverage
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.testing.FictionalCandidates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MappersTest {
    private val candidate = FictionalCandidates.candidateA

    @Test
    fun `candidate survives an entity round trip`() {
        val stored = CandidateWithCoverage(candidate.asEntity(), candidate.coverageEntities())
        assertEquals(candidate, stored.asModel())
    }

    @Test
    fun `missing or unknown coverage is shown as unavailable`() {
        val stored = CandidateWithCoverage(
            candidate.asEntity(),
            listOf(CandidateCoverageEntity(candidate.id.value, CoverageFacet.PROGRAM.name, "SOMETHING_NEW")),
        )
        val coverage = stored.asModel().coverage
        CoverageFacet.entries.forEach { assertEquals(CoverageLevel.UNAVAILABLE, coverage[it]) }
    }
}
