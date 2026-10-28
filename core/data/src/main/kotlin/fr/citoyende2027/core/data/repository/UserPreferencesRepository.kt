package fr.citoyende2027.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import fr.citoyende2027.core.model.CandidateOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local, on-device preferences only (AGENTS.md §2.4). */
interface UserPreferencesRepository {
    val candidateOrder: Flow<CandidateOrder>

    suspend fun setCandidateOrder(order: CandidateOrder)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    UserPreferencesRepository {
    override val candidateOrder: Flow<CandidateOrder> = dataStore.data.map { preferences ->
        preferences[SHUFFLE_SEED]?.let(CandidateOrder::Shuffled) ?: CandidateOrder.Default
    }

    override suspend fun setCandidateOrder(order: CandidateOrder) {
        dataStore.edit { preferences ->
            when (order) {
                CandidateOrder.Alphabetical -> preferences.remove(SHUFFLE_SEED)
                is CandidateOrder.Shuffled -> preferences[SHUFFLE_SEED] = order.seed
            }
        }
    }

    private companion object {
        /** Present only when the random order is chosen. */
        val SHUFFLE_SEED = longPreferencesKey("candidate_order_shuffle_seed")
    }
}
