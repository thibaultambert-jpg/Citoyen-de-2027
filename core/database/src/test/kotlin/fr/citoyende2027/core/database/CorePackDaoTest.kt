package fr.citoyende2027.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.testing.FictionalCandidates
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

    private suspend fun replace(info: DataPackInfo, candidates: List<Candidate>) = dao.replaceCorePack(
        pack = info.asEntity(),
        candidates = candidates.map { it.asEntity() },
        coverage = candidates.flatMap { it.coverageEntities() },
    )

    @Test
    fun candidates_roundTripWithCoverageAndSource() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)

        val stored = dao.observeCandidates().first()
        assertEquals(FictionalCandidates.all.toSet(), stored.map { it.asModel() }.toSet())
        assertEquals(FictionalCandidates.packInfo, dao.observePack(DataPackInfo.CORE).first()?.asModel())
    }

    @Test
    fun replace_removesCandidatesAndCoverageAbsentFromNewPack() = runTest {
        replace(FictionalCandidates.packInfo, FictionalCandidates.all)
        val newInfo = FictionalCandidates.packInfo.copy(version = "0.0.1-fixture")
        replace(newInfo, listOf(FictionalCandidates.candidateB))

        assertEquals(listOf(FictionalCandidates.candidateB), dao.observeCandidates().first().map { it.asModel() })
        assertNull(dao.observeCandidate(FictionalCandidates.candidateA.id.value).first())
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
