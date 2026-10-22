package fr.citoyende2027.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.data.repository.DefaultAppInfoRepository
import fr.citoyende2027.core.data.repository.InstalledPack
import fr.citoyende2027.core.data.repository.PackUpdate
import fr.citoyende2027.core.data.repository.TextScale
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppInfoRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun repository() = DefaultAppInfoRepository(
        context = ApplicationProvider.getApplicationContext(),
        dao = FakeCorePackDao(),
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            folder.newFile("prefs.preferences_pb").also { it.delete() }
        },
    )

    private fun pack(id: String, version: String, date: String) =
        InstalledPack(id, version, LocalDate.parse(date), isFictional = false, inclusionPolicyDate = null)

    @Test
    fun historyKeepsEachVersionOnceMostRecentFirst() = runBlocking {
        val repository = repository()
        val first = listOf(pack("core", "core-1", "2026-01-10"), pack("legal", "legal-1", "2026-01-09"))
        repository.recordHistory(first, LocalDate.of(2026, 1, 11))
        repository.recordHistory(first, LocalDate.of(2026, 1, 12))
        repository.recordHistory(
            listOf(pack("core", "core-2", "2026-01-20"), pack("legal", "legal-1", "2026-01-09")),
            LocalDate.of(2026, 1, 21),
        )
        assertEquals(
            listOf(
                PackUpdate("core", "core-2", "2026-01-20", "2026-01-21"),
                PackUpdate("core", "core-1", "2026-01-10", "2026-01-11"),
                PackUpdate("legal", "legal-1", "2026-01-09", "2026-01-11"),
            ),
            repository.observeHistory().first(),
        )
    }

    @Test
    fun textScaleIsRemembered() = runBlocking {
        val repository = repository()
        assertEquals(TextScale.SYSTEM, repository.textScale.first())
        repository.setTextScale(TextScale.LARGEST)
        assertEquals(TextScale.LARGEST, repository.textScale.first())
    }
}
