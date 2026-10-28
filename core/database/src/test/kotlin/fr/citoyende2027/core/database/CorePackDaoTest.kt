package fr.citoyende2027.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.dao.LegalRows
import fr.citoyende2027.core.database.dao.ProgramsRows
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.database.model.ftsEntity
import fr.citoyende2027.core.database.model.programStatusEntity
import fr.citoyende2027.core.database.model.themeEntities
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.FullTextSearch
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.FictionalLegal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CorePackDaoTest {
    private lateinit var database: CitoyenDatabase
    private lateinit var dao: CorePackDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CitoyenDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.corePackDao()
    }

    @After
    fun tearDown() = database.close()

    private val programs = FictionalCandidates.programsPack.let { content ->
        ProgramsRows(
            pack = content.info.asEntity(),
            statuses = content.statuses.map { (id, status) -> programStatusEntity(id, status) },
            documents = content.documents.map { it.asEntity() },
            chunks = content.chunks.map { it.asEntity() },
            chunkThemes = content.chunks.flatMap { it.themeEntities() },
        )
    }

    private suspend fun replace(
        info: DataPackInfo,
        candidates: List<Candidate>,
        withPrograms: Boolean = true,
        withLegal: Boolean = true,
    ) = dao.replaceCorePack(
        pack = info.asEntity(),
        candidates = candidates.map { it.asEntity() },
        coverage = candidates.flatMap { it.coverageEntities() },
        mandates = FictionalCandidates.mandates.filter { m -> candidates.any { it.id == m.candidateId } }
            .map { it.asEntity() },
        votes = FictionalCandidates.votes.filter { v -> candidates.any { it.id == v.candidateId } }
            .map { it.asEntity() },
        speeches = FictionalCandidates.speeches.filter { s -> candidates.any { it.id == s.candidateId } }
            .map { it.asEntity() },
        themes = FictionalCandidates.themes.map { it.asEntity() },
        voteThemes = FictionalCandidates.votes.filter { v -> candidates.any { it.id == v.candidateId } }
            .flatMap { it.themeEntities() },
        programs = programs.takeIf { withPrograms },
        legal = legal.takeIf { withLegal },
    )

    private val legal = FictionalLegal.pack.let { content ->
        LegalRows(
            pack = content.info.asEntity(),
            texts = content.texts.map { it.asEntity() },
            articles = content.articles.map { it.asEntity() },
            fts = content.articles.map { it.ftsEntity() },
        )
    }

    @Test
    fun legal_roundTripAndAccentInsensitiveSearchRankedByBm25() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)
        assertEquals(
            listOf(FictionalLegal.textA, FictionalLegal.textB),
            dao.observeLegalTexts().first().map { it.asModel() },
        )
        val textA = FictionalLegal.articles.filter { it.textId == FictionalLegal.textA.id }
        assertEquals(textA, dao.observeLegalArticles(FictionalLegal.textA.id).first().map { it.asModel() })

        val matches = dao.searchLegal(checkNotNull(FullTextSearch.matchExpression("ELECTION fictiv")))
        val ranked = matches.sortedByDescending { FullTextSearch.bm25(it.matchInfo.toInts()) }.map { it.articleId }
        // The article repeating the term ranks first; the unrelated one is not found.
        assertEquals(listOf("fictif-texte-b-art-1", "fictif-texte-a-art-2"), ranked)
        assertEquals(
            setOf("fictif-texte-a-art-2", "fictif-texte-b-art-1"),
            dao.searchLegal("article 1").map { it.articleId }.toSet(),
        )

        replace(FictionalCandidates.packInfo, FictionalCandidates.all, withLegal = false)
        assertEquals(emptyList<Any>(), dao.observeLegalTexts().first())
        assertEquals(emptyList<Any>(), dao.searchLegal("election*"))
        assertNull(dao.observePack(DataPackInfo.LEGAL).first())
    }

    private fun ByteArray.toInts(): IntArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder()).asIntBuffer()
        return IntArray(buffer.remaining()).also { buffer.get(it) }
    }

    @Test
    fun candidates_roundTripWithCoverageAndSource() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)

        val stored = dao.observeCandidates().first()
        assertEquals(FictionalCandidates.all.toSet(), stored.map { it.asModel() }.toSet())
        assertEquals(FictionalCandidates.packInfo, dao.observePack(DataPackInfo.CORE).first()?.asModel())
        val a = FictionalCandidates.candidateA.id.value
        assertEquals(FictionalCandidates.mandates, dao.observeMandates(a).first().map { it.asModel() })
        assertEquals(FictionalCandidates.votes, dao.observeVotes(a).first().map { it.asModel() })
        assertEquals(FictionalCandidates.speeches, dao.observeSpeeches(a).first().map { it.asModel() })
        assertEquals(FictionalCandidates.themes.toSet(), dao.observeThemes().first().map { it.asModel() }.toSet())
    }

    @Test
    fun programs_roundTripAndAreReplacedWithTheCorePack() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)
        val b = FictionalCandidates.candidateB.id
        val expected = FictionalCandidates.programsPack.program(b)
        assertEquals(expected.status.name, dao.observeProgramStatus(b.value).first())
        assertEquals(expected.documents, dao.observeProgramDocuments(b.value).first().map { it.asModel() })
        assertEquals(expected.chunks, dao.observeProgramChunks(b.value).first().map { it.asModel() })
        assertEquals(FictionalCandidates.programsInfo, dao.observePack(DataPackInfo.PROGRAMS).first()?.asModel())

        replace(FictionalCandidates.packInfo, FictionalCandidates.all, withPrograms = false)
        assertNull(dao.observeProgramStatus(b.value).first())
        assertEquals(emptyList<Any>(), dao.observeProgramChunks(b.value).first())
        assertNull(dao.observePack(DataPackInfo.PROGRAMS).first())
    }

    @Test
    fun replace_removesCandidatesAndCoverageAbsentFromNewPack() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)
        val newInfo = FictionalCandidates.packInfo.copy(version = "0.0.1-fixture")
        replace(newInfo, listOf(FictionalCandidates.candidateB), withPrograms = false)

        assertEquals(listOf(FictionalCandidates.candidateB), dao.observeCandidates().first().map { it.asModel() })
        assertNull(dao.observeCandidate(FictionalCandidates.candidateA.id.value).first())
        assertEquals(emptyList<Any>(), dao.observeMandates(FictionalCandidates.candidateA.id.value).first())
        assertEquals(emptyList<Any>(), dao.observeVotes(FictionalCandidates.candidateA.id.value).first())
        assertEquals("0.0.1-fixture", dao.observePack(DataPackInfo.CORE).first()?.version)
        assertEquals(1, dao.countPacks(DataPackInfo.CORE))
    }

    @Test
    fun observers_receiveTheNewPackAfterReplacement() = runTest {
        dao.observeCandidates().test {
            assertEquals(emptyList<Any>(), awaitItem())
            replace(FictionalCandidates.packInfo, FictionalCandidates.all)
            // Room may re-emit the previous state before the new one; the new pack must arrive.
            while (awaitItem().size != FictionalCandidates.all.size) Unit
            cancelAndIgnoreRemainingEvents()
        }
    }
}
