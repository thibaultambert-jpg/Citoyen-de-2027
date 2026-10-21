package fr.citoyende2027.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.DataStoreUserPreferencesRepository
import fr.citoyende2027.core.model.CandidateOrder
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DataStoreUserPreferencesRepositoryTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.repository() = DataStoreUserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "test.preferences_pb") },
    )

    @Test
    fun `order is alphabetical by default`() = runTest {
        repository().candidateOrder.test { assertEquals(CandidateOrder.Alphabetical, awaitItem()) }
    }

    @Test
    fun `shuffled order keeps its seed and can be reset`() = runTest {
        val repository = repository()
        repository.candidateOrder.test {
            assertEquals(CandidateOrder.Alphabetical, awaitItem())
            repository.setCandidateOrder(CandidateOrder.Shuffled(seed = 7))
            assertEquals(CandidateOrder.Shuffled(seed = 7), awaitItem())
            repository.setCandidateOrder(CandidateOrder.Alphabetical)
            assertEquals(CandidateOrder.Alphabetical, awaitItem())
        }
    }
}
