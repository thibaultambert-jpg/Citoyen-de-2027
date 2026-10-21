package fr.citoyende2027.core.testing

import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage
import fr.citoyende2027.core.model.SourceRef
import java.time.LocalDate

/**
 * Fictional fixtures only (AGENTS.md §2.6): no real name, vote, measure or date.
 * URLs use the reserved ".invalid" TLD (RFC 2606) so they can never resolve.
 */
object FictionalCandidates {
    val candidateA = Candidate(
        id = CandidateId("fictif-a"),
        displayName = "Candidat A",
        sortName = "A",
        status = CandidacyStatus.DECLARE,
        statusDate = LocalDate.of(2026, 1, 10),
        coverage = coverage(
            CoverageFacet.PROGRAM to CoverageLevel.AVAILABLE,
            CoverageFacet.MANDATES to CoverageLevel.AVAILABLE,
            CoverageFacet.VOTES to CoverageLevel.PARTIAL,
            CoverageFacet.SPEECHES to CoverageLevel.AVAILABLE,
        ),
    )

    val candidateB = Candidate(
        id = CandidateId("fictif-b"),
        displayName = "Candidat B",
        sortName = "B",
        status = CandidacyStatus.DECLARE,
        statusDate = LocalDate.of(2026, 2, 20),
        coverage = coverage(
            CoverageFacet.PROGRAM to CoverageLevel.PARTIAL,
            CoverageFacet.MANDATES to CoverageLevel.NOT_APPLICABLE,
            CoverageFacet.VOTES to CoverageLevel.NOT_APPLICABLE,
            CoverageFacet.SPEECHES to CoverageLevel.UNAVAILABLE,
        ),
    )

    val candidateC = Candidate(
        id = CandidateId("fictif-c"),
        displayName = "Candidat C",
        sortName = "C",
        status = CandidacyStatus.RETIRE,
        statusDate = LocalDate.of(2026, 3, 30),
        coverage = DataCoverage.Unavailable,
    )

    val all: List<Candidate> = listOf(candidateA, candidateB, candidateC)

    val source = SourceRef(
        name = "Source fictive",
        url = "https://example.invalid/source",
        retrievedOn = LocalDate.of(2026, 1, 1),
        license = "Licence fictive",
        packVersion = "0.0.0-fixture",
    )

    private fun coverage(vararg levels: Pair<CoverageFacet, CoverageLevel>) = DataCoverage(levels.toMap())
}
