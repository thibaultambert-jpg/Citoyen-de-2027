package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.database.CorePackReader
import fr.citoyende2027.core.database.ProgramsPackReader
import fr.citoyende2027.core.model.BallotSubject
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.model.TagMethod
import fr.citoyende2027.core.model.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Android reader reads exactly what the Python pipeline writes (pack schema contract). */
@RunWith(RobolectricTestRunner::class)
class CorePackReaderContractTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun readsThePipelineTestPack() {
        val content = CorePackReader().read(TestPack.packFile(folder.root))

        assertEquals(TestPack.VERSION, content.info.version)
        assertFalse(content.info.isFictional)
        assertEquals(listOf("fictif-a", "fictif-b", "fictif-c"), content.candidates.map { it.id.value }.sorted())
        val a = content.candidates.first { it.id.value == "fictif-a" }
        assertEquals(CandidacyStatus.DECLARE, a.status)
        assertEquals("https://example.invalid/a", a.statusSource.url)
        assertEquals(TestPack.VERSION, a.statusSource.packVersion)
        assertEquals(CoverageLevel.AVAILABLE, a.coverage[CoverageFacet.VOTES])
        val b = content.candidates.first { it.id.value == "fictif-b" }
        assertEquals(CoverageLevel.NOT_APPLICABLE, b.coverage[CoverageFacet.VOTES])
        assertEquals(listOf(Body.AN), content.mandates.map { it.body })
        assertEquals(listOf("pour"), content.votes.map { it.position })
        assertEquals(listOf(BallotSubject.AMENDMENT), content.votes.map { it.ballotSubject })
        assertEquals(listOf(SpeechKind.OFFICIAL_STATEMENT), content.speeches.map { it.kind })
        assertEquals(CoverageLevel.PARTIAL, a.coverage[CoverageFacet.SPEECHES])
        assertEquals(CoverageLevel.AVAILABLE, a.coverage[CoverageFacet.PROGRAM])
        assertEquals(CoverageLevel.PARTIAL, b.coverage[CoverageFacet.PROGRAM])
        assertEquals(16, content.themes.size)
        assertEquals(listOf(listOf(ThemeId("fictif-01"))), content.votes.map { vote -> vote.themes.map { it.themeId } })
    }

    @Test
    fun readsThePipelineProgramsPack() {
        val content = ProgramsPackReader().read(TestPack.programsFile(folder.root))

        assertEquals(TestPack.PROGRAMS_VERSION, content.info.version)
        val a = content.program(CandidateId("fictif-a"))
        assertEquals(ProgramStatus.COMPLET, a.status)
        assertEquals(listOf(1, 3), a.chunks.map { it.page })
        assertEquals(listOf("Partie fictive 1", "Section fictive"), a.chunks.first().headingPath)
        assertEquals("Mesure fictive n° 2.\n\nParagraphe fictif sans thème reconnu.", a.chunks[1].text)
        assertEquals("https://example.invalid/fictif-a-programme", a.documents.single().source.url)
        assertEquals("Conditions fictives", a.documents.single().source.license)
        val b = content.program(CandidateId("fictif-b"))
        assertEquals(ProgramStatus.PARTIEL, b.status)
        assertEquals("section-fictive", b.chunks.single().anchor)
        assertEquals(emptyList<String>(), b.chunks.single().headingPath)
        assertEquals(listOf(TagMethod.RULE, TagMethod.MANUAL), b.chunks.single().themes.map { it.method })
        val c = content.program(CandidateId("fictif-c"))
        assertEquals(ProgramStatus.NON_PUBLIE, c.status)
    }
}
