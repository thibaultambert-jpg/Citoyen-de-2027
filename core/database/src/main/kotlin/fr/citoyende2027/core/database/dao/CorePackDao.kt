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
import fr.citoyende2027.core.database.model.MandateEntity
import fr.citoyende2027.core.database.model.SpeechEntity
import fr.citoyende2027.core.database.model.VoteEntity
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

    @Query("SELECT * FROM data_packs WHERE id = :id")
    suspend fun getPack(id: String): DataPackEntity?

    @Query("SELECT COUNT(*) FROM data_packs WHERE id = :id")
    suspend fun countPacks(id: String): Int

    @Query("DELETE FROM candidates")
    suspend fun deleteCandidates()

    @Insert
    suspend fun insertCandidates(candidates: List<CandidateEntity>)

    @Insert
    suspend fun insertCoverage(coverage: List<CandidateCoverageEntity>)

    @Insert
    suspend fun insertMandates(mandates: List<MandateEntity>)

    @Insert
    suspend fun insertVotes(votes: List<VoteEntity>)

    @Insert
    suspend fun insertSpeeches(speeches: List<SpeechEntity>)

    @Query("SELECT * FROM speeches WHERE candidate_id = :candidateId ORDER BY speech_date DESC")
    fun observeSpeeches(candidateId: String): Flow<List<SpeechEntity>>

    @Query("SELECT * FROM mandates WHERE candidate_id = :candidateId ORDER BY start_date DESC")
    fun observeMandates(candidateId: String): Flow<List<MandateEntity>>

    @Query("SELECT * FROM votes WHERE candidate_id = :candidateId ORDER BY ballot_date DESC")
    fun observeVotes(candidateId: String): Flow<List<VoteEntity>>

    @Upsert
    suspend fun upsertPack(pack: DataPackEntity)

    /** Replaces the whole pack in one transaction: readers never see a half-written pack. */
    @Transaction
    suspend fun replaceCorePack(
        pack: DataPackEntity,
        candidates: List<CandidateEntity>,
        coverage: List<CandidateCoverageEntity>,
        mandates: List<MandateEntity> = emptyList(),
        votes: List<VoteEntity> = emptyList(),
        speeches: List<SpeechEntity> = emptyList(),
    ) {
        // Coverage, mandates and votes are removed with their candidate (ON DELETE CASCADE).
        deleteCandidates()
        insertCandidates(candidates)
        insertCoverage(coverage)
        insertMandates(mandates)
        insertVotes(votes)
        insertSpeeches(speeches)
        upsertPack(pack)
    }
}
