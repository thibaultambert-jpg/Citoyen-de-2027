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
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.ProgramsPackContent
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.model.Sponsorships
import fr.citoyende2027.core.model.TagConfidence
import fr.citoyende2027.core.model.TagMethod
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.ThemeTag
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

    val theme1 = Theme(ThemeId("fictif-01"), "Thème fictif 01")
    val theme2 = Theme(ThemeId("fictif-02"), "Thème fictif 02")
    val unknownTheme = Theme(ThemeId(ThemeId.UNKNOWN), "Thème non déterminé")
    val themes = listOf(theme1, theme2, unknownTheme)

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
            themes = listOf(ThemeTag(theme1.id, TagMethod.RULE, TagConfidence.LOW)),
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

    /** Same counts as the test pack of the pipeline (pipeline/citoyen_pipeline/testpack.py). */
    val sponsorships = listOf(
        Sponsorships(
            candidateId = candidateA.id,
            total = 3,
            departments = 2,
            byMandate = listOf(
                Sponsorships.MandateCount("Mandat fictif 1", 2),
                Sponsorships.MandateCount("Mandat fictif 2", 1),
            ),
            publishedOn = LocalDate.of(2026, 1, 10),
            source = source,
        ),
    )

    /** Same thresholds as pipeline/tests/fixtures/rag_config.json. */
    const val RAG_CONFIG =
        """{"min_coverage":0.5,"min_matched_terms":2,"min_similarity":2.0,"pool_size":30,"rrf_k":60,"top_k":5}"""

    val corePack =
        CorePackContent(packInfo, all, mandates, votes, speeches, themes, RAG_CONFIG, sponsorships = sponsorships)

    val programsInfo = packInfo.copy(id = DataPackInfo.PROGRAMS)

    private fun programSource(documentId: String) = source.copy(
        name = "Programme fictif",
        url = "https://example.invalid/$documentId",
        license = "Conditions fictives",
    )

    /** Candidat A: complete programme (PDF pages); B: partial (web page anchor); C: none. */
    val programDocuments = listOf(
        ProgramDocument(
            id = "fictif-a-programme",
            candidateId = candidateA.id,
            title = "Programme fictif (fictif-a)",
            publishedOn = LocalDate.of(2026, 1, 5),
            version = "1",
            source = programSource("fictif-a-programme"),
        ),
        ProgramDocument(
            id = "fictif-b-programme",
            candidateId = candidateB.id,
            title = "Programme fictif (fictif-b)",
            publishedOn = LocalDate.of(2026, 1, 5),
            version = "1",
            source = programSource("fictif-b-programme"),
        ),
    )

    val programChunks = listOf(
        ProgramChunk(
            id = "fictif-a-programme-0001",
            documentId = "fictif-a-programme",
            candidateId = candidateA.id,
            ordinal = 1,
            headingPath = listOf("Partie fictive 1", "Section fictive"),
            page = 1,
            anchor = null,
            text = "Mesure fictive n° 1 : texte fictif sur le motfictif01.",
            themes = listOf(ThemeTag(theme1.id, TagMethod.RULE, TagConfidence.LOW)),
        ),
        ProgramChunk(
            id = "fictif-a-programme-0002",
            documentId = "fictif-a-programme",
            candidateId = candidateA.id,
            ordinal = 2,
            headingPath = listOf("Partie fictive 2"),
            page = 3,
            anchor = null,
            text = "Mesure fictive n° 2.\n\nParagraphe fictif sans thème reconnu.",
            themes = listOf(ThemeTag(unknownTheme.id, TagMethod.RULE, TagConfidence.LOW)),
        ),
        ProgramChunk(
            id = "fictif-b-programme-0001",
            documentId = "fictif-b-programme",
            candidateId = candidateB.id,
            ordinal = 1,
            headingPath = emptyList(),
            page = null,
            anchor = "section-fictive",
            text = "Mesure fictive de B sur le motfictif01 et le motfictif02.",
            themes = listOf(
                ThemeTag(theme1.id, TagMethod.RULE, TagConfidence.LOW),
                ThemeTag(theme2.id, TagMethod.MANUAL, TagConfidence.HIGH),
            ),
        ),
    )

    val programsPack = ProgramsPackContent(
        info = programsInfo,
        statuses = mapOf(
            candidateA.id to ProgramStatus.COMPLET,
            candidateB.id to ProgramStatus.PARTIEL,
            candidateC.id to ProgramStatus.NON_PUBLIE,
        ),
        documents = programDocuments,
        chunks = programChunks,
    )

    private fun coverage(vararg levels: Pair<CoverageFacet, CoverageLevel>) = DataCoverage(levels.toMap())
}
