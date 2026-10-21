package fr.citoyende2027.core.data

import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.data.repository.OfflineCandidatesRepository
import fr.citoyende2027.core.testing.FictionalCandidates
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineCandidatesRepositoryTest {
    private val dao = FakeCorePackDao()
    private val repository = OfflineCandidatesRepository(dao)
    private val store = CorePackStore(dao)

    @Test
    fun `empty storage exposes no candidate and no pack`() = runTest {
        assertFalse(store.hasCorePack())
        repository.observeCandidates().test { assertEquals(emptyList<Any>(), awaitItem()) }
        repository.observeCorePackInfo().test { assertNull(awaitItem()) }
    }

    @Test
    fun `installed pack is exposed as domain models`() = runTest {
        store.install(FictionalCandidates.corePack)

        assertTrue(store.hasCorePack())
        repository.observeCandidates().test { assertEquals(FictionalCandidates.all, awaitItem()) }
        repository.observeCandidate(FictionalCandidates.candidateB.id).test {
            assertEquals(FictionalCandidates.candidateB, awaitItem())
        }
        repository.observeCorePackInfo().test { assertEquals(FictionalCandidates.packInfo, awaitItem()) }
        repository.observeMandates(FictionalCandidates.candidateA.id).test {
            assertEquals(FictionalCandidates.mandates, awaitItem())
        }
        repository.observeVotes(FictionalCandidates.candidateA.id).test {
            assertEquals(FictionalCandidates.votes, awaitItem())
        }
    }
}
