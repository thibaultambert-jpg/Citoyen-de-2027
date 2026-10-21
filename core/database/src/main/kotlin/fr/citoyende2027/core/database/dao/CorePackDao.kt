package fr.citoyende2027.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.citoyende2027.core.database.model.CandidateCoverageEntity
import fr.citoyende2027.core.database.model.CandidateEntity
import fr.citoyende2027.core.database.model.CandidateWithCoverage
import fr.citoyende2027.core.database.model.DataPackEntity
import kotlinx.coroutines.flow.Flow

/** Read and atomic replacement of the `core` pack content. */
@Dao
interface CorePackDao {
    @Transaction
    @Query("SELECT * FROM candidates")
    fun observeCandidates(): Flow<List<CandidateWithCoverage>>

    @Transaction
    @Query("SELECT * FROM candidates WHERE id = :id")
    fun observeCandidate(id: String): Flow<CandidateWithCoverage?>

    @Query("SELECT * FROM data_packs WHERE id = :id")
    fun observePack(id: String): Flow<DataPackEntity?>

    @Query("SELECT COUNT(*) FROM data_packs WHERE id = :id")
    suspend fun countPacks(id: String): Int

    @Query("DELETE FROM candidates")
    suspend fun deleteCandidates()

    @Insert
    suspend fun insertCandidates(candidates: List<CandidateEntity>)

    @Insert
    suspend fun insertCoverage(coverage: List<CandidateCoverageEntity>)

    @Upsert
    suspend fun upsertPack(pack: DataPackEntity)

    /** Replaces the whole pack in one transaction: readers never see a half-written pack. */
    @Transaction
    suspend fun replaceCorePack(
        pack: DataPackEntity,
        candidates: List<CandidateEntity>,
        coverage: List<CandidateCoverageEntity>,
    ) {
        deleteCandidates()
        insertCandidates(candidates)
        insertCoverage(coverage)
        upsertPack(pack)
    }
}
