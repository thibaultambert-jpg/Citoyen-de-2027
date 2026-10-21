package fr.citoyende2027.core.testing

import fr.citoyende2027.core.model.BallotSubject
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.model.Vote
import java.time.LocalDate

/**
 * Fictional fixtures only (AGENTS.md §2.6): no real name, vote, measure or date.
 * URLs use the reserved ".invalid" TLD (RFC 2606) so they can never resolve.
 */
object FictionalCandidates {
    val source = SourceRef(
        name = "Source fictive",
        url = "https://example.invalid/source",
        retrievedOn = LocalDate.of(2026, 1, 1),
        license = "Licence fictive",
        packVersion = "0.0.0-fixture",
    )

    val candidateA = Candidate(
        id = CandidateId("fictif-a"),
        displayName = "Candidat A",
        sortName = "A",
        status = CandidacyStatus.DECLARE,
        statusDate = LocalDate.of(2026, 1, 10),
        statusSource = source,
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
        statusSource = source,
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
        statusSource = source,
        coverage = DataCoverage.Unavailable,
    )

    val all: List<Candidate> = listOf(candidateA, candidateB, candidateC)

    val packInfo = DataPackInfo(
        id = DataPackInfo.CORE,
        version = "0.0.0-fixture",
        generatedOn = LocalDate.of(2026, 1, 1),
        isFictional = true,
    )

    /** Fictional mandate and votes of Candidat A (coverage: mandates available, votes partial). */
    val mandates = listOf(
        Mandate(
            id = "fictif-a-mandat-1",
            candidateId = candidateA.id,
            body = Body.AN,
            label = "Mandat fictif",
            startDate = LocalDate.of(2024, 7, 1),
            endDate = null,
            source = source,
        ),
    )

    val votes = listOf(
        Vote(
            id = "fictif-a-vote-1",
            candidateId = candidateA.id,
            body = Body.AN,
            ballotId = "scrutin-fictif-1",
            ballotDate = LocalDate.of(2025, 3, 4),
            ballotTitle = "Scrutin fictif n° 1",
            ballotType = "Type fictif",
            ballotSubject = BallotSubject.AMENDMENT,
            position = "pour",
            ballotUrl = "https://example.invalid/scrutin-1",
            source = source,
        ),
    )

    val speeches = listOf(
        Speech(
            id = "fictif-a-discours-1",
            candidateId = candidateA.id,
            kind = SpeechKind.OFFICIAL_STATEMENT,
            body = null,
            date = LocalDate.of(2025, 6, 1),
            title = "Déclaration fictive",
            detail = "Interview — Média fictif",
            url = "https://example.invalid/discours-1",
            source = source,
        ),
    )

    val corePack = CorePackContent(packInfo, all, mandates, votes, speeches)

    private fun coverage(vararg levels: Pair<CoverageFacet, CoverageLevel>) = DataCoverage(levels.toMap())
}
