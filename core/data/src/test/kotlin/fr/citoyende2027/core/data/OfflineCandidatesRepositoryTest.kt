package fr.citoyende2027.core.data

import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.data.repository.OfflineCandidatesRepository
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.DataPackInfo
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
        repository.observeSpeeches(FictionalCandidates.candidateA.id).test {
            assertEquals(FictionalCandidates.speeches, awaitItem())
        }
        repository.observeThemes().test { assertEquals(FictionalCandidates.themes, awaitItem()) }
        repository.observeSponsorships(FictionalCandidates.candidateA.id).test {
            assertEquals(FictionalCandidates.sponsorships.single(), awaitItem())
        }
        repository.observeSponsorships(FictionalCandidates.candidateB.id).test { assertEquals(null, awaitItem()) }
    }

    @Test
    fun `programmes are installed with the core pack and exposed per candidate`() = runTest {
        store.install(FictionalCandidates.corePack, FictionalCandidates.programsPack)

        FictionalCandidates.all.forEach { candidate ->
            repository.observeProgram(candidate.id).test {
                assertEquals(FictionalCandidates.programsPack.program(candidate.id), awaitItem())
            }
        }
        assertEquals("0.0.0-fixture", dao.getPack(DataPackInfo.PROGRAMS)?.version)

        // A core pack installed without programmes removes the previous ones.
        store.install(FictionalCandidates.corePack)
        repository.observeProgram(FictionalCandidates.candidateA.id).test {
            assertEquals(CandidateProgram.notPublished(FictionalCandidates.candidateA.id), awaitItem())
        }
        assertNull(dao.getPack(DataPackInfo.PROGRAMS))
    }
}
