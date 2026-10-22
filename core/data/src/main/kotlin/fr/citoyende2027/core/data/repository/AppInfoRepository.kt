package fr.citoyende2027.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.core.data.pack.PackSyncWorker
import fr.citoyende2027.core.database.dao.CorePackDao
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An installed data pack, as listed in "Infos > Données" (AGENTS.md §5: data date visible). */
data class InstalledPack(
    val id: String,
    val version: String,
    val dataDate: LocalDate,
    val isFictional: Boolean,
    /** `core` only: version date of the candidate inclusion rule (AGENTS.md §10). */
    val inclusionPolicyDate: LocalDate?,
)

/** One line of "Historique des mises à jour": a pack version activated on this device. */
@Serializable
data class PackUpdate(val packId: String, val version: String, val dataDate: String, val installedOn: String)

/** Text size chosen in the app, applied on top of the system font size (AGENTS.md §11). */
enum class TextScale(val factor: Float) {
    SYSTEM(1f),
    LARGE(1.15f),
    LARGEST(1.3f),
}

interface AppInfoRepository {
    fun observeInstalledPacks(): Flow<List<InstalledPack>>

    /** Most recent first. Kept in DataStore: it survives a reset of the data cache. */
    fun observeHistory(): Flow<List<PackUpdate>>

    val textScale: Flow<TextScale>

    suspend fun setTextScale(scale: TextScale)

    /** "Rechercher une mise à jour": one sync now, on any network, at the user's request. */
    fun requestSync()

    /** Appends to the history the pack versions not recorded yet; [today] is the installation date. */
    suspend fun recordHistory(packs: List<InstalledPack>, today: LocalDate = LocalDate.now())
}

internal class DefaultAppInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CorePackDao,
    private val dataStore: DataStore<Preferences>,
) : AppInfoRepository {
    override fun observeInstalledPacks(): Flow<List<InstalledPack>> = dao.observePacks().map { packs ->
        packs.map { InstalledPack(it.id, it.version, it.generatedOn, it.isFictional, it.inclusionPolicyDate) }
    }.distinctUntilChanged()

    override fun observeHistory(): Flow<List<PackUpdate>> = dataStore.data.map { decode(it[HISTORY]) }

    override val textScale: Flow<TextScale> = dataStore.data.map { preferences ->
        preferences[TEXT_SCALE]?.let { name -> TextScale.entries.firstOrNull { it.name == name } } ?: TextScale.SYSTEM
    }

    override suspend fun setTextScale(scale: TextScale) {
        dataStore.edit { it[TEXT_SCALE] = scale.name }
    }

    override fun requestSync() = PackSyncWorker.syncNow(WorkManager.getInstance(context))

    override suspend fun recordHistory(packs: List<InstalledPack>, today: LocalDate) {
        dataStore.edit { preferences ->
            val history = decode(preferences[HISTORY])
            val known = history.map { it.packId to it.version }.toSet()
            val new = packs.filter { (it.id to it.version) !in known }.map {
                PackUpdate(it.id, it.version, it.dataDate.toString(), today.toString())
            }
            if (new.isNotEmpty()) {
                preferences[HISTORY] = json.encodeToString(serializer, (new + history).take(MAX_HISTORY))
            }
        }
    }

    private fun decode(text: String?): List<PackUpdate> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        val HISTORY = stringPreferencesKey("pack_history")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        const val MAX_HISTORY = 100
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(PackUpdate.serializer())
    }
}
