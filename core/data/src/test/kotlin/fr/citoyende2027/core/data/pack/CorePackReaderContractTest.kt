package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.database.CorePackReader
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
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
    }
}
