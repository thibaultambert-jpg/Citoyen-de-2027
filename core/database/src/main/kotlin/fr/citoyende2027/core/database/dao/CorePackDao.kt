package fr.citoyende2027.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.citoyende2027.core.database.model.CandidateCoverageEntity
import fr.citoyende2027.core.database.model.CandidateEntity
import fr.citoyende2027.core.database.model.CandidateWithCoverage
import fr.citoyende2027.core.database.model.ChunkThemeEntity
import fr.citoyende2027.core.database.model.ChunkWithThemes
import fr.citoyende2027.core.database.model.DataPackEntity
import fr.citoyende2027.core.database.model.LegalArticleEntity
import fr.citoyende2027.core.database.model.LegalFtsEntity
import fr.citoyende2027.core.database.model.LegalFtsMatch
import fr.citoyende2027.core.database.model.LegalTextEntity
import fr.citoyende2027.core.database.model.MandateEntity
import fr.citoyende2027.core.database.model.ProgramChunkEntity
import fr.citoyende2027.core.database.model.ProgramDocumentEntity
import fr.citoyende2027.core.database.model.ProgramStatusEntity
import fr.citoyende2027.core.database.model.SpeechEntity
import fr.citoyende2027.core.database.model.ThemeEntity
import fr.citoyende2027.core.database.model.VoteEntity
import fr.citoyende2027.core.database.model.VoteThemeEntity
import fr.citoyende2027.core.database.model.VoteWithThemes
import fr.citoyende2027.core.model.DataPackInfo
import kotlinx.coroutines.flow.Flow

/** Rows of a `programs` pack, installed together with the `core` pack of the same manifest. */
data class ProgramsRows(
    val pack: DataPackEntity,
    val statuses: List<ProgramStatusEntity>,
    val documents: List<ProgramDocumentEntity>,
    val chunks: List<ProgramChunkEntity>,
    val chunkThemes: List<ChunkThemeEntity>,
)

/** Rows of a `legal` pack; [fts] indexes [articles]. */
data class LegalRows(
    val pack: DataPackEntity,
    val texts: List<LegalTextEntity>,
    val articles: List<LegalArticleEntity>,
    val fts: List<LegalFtsEntity>,
)

/** Read and atomic replacement of the `core`, `programs` and `legal` packs of one manifest. */
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

    @Query("SELECT * FROM data_packs ORDER BY id")
    fun observePacks(): Flow<List<DataPackEntity>>

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

    @Transaction
    @Query("SELECT * FROM votes WHERE candidate_id = :candidateId ORDER BY ballot_date DESC, id")
    fun observeVotes(candidateId: String): Flow<List<VoteWithThemes>>

    @Query("SELECT * FROM themes")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT status FROM program_status WHERE candidate_id = :candidateId")
    fun observeProgramStatus(candidateId: String): Flow<String?>

    @Query("SELECT * FROM program_documents WHERE candidate_id = :candidateId ORDER BY id")
    fun observeProgramDocuments(candidateId: String): Flow<List<ProgramDocumentEntity>>

    @Transaction
    @Query("SELECT * FROM program_chunks WHERE candidate_id = :candidateId ORDER BY document_id, ordinal")
    fun observeProgramChunks(candidateId: String): Flow<List<ChunkWithThemes>>

    @Query("DELETE FROM themes")
    suspend fun deleteThemes()

    @Query("DELETE FROM data_packs WHERE id = :id")
    suspend fun deletePack(id: String)

    @Insert
    suspend fun insertThemes(themes: List<ThemeEntity>)

    @Insert
    suspend fun insertVoteThemes(tags: List<VoteThemeEntity>)

    @Insert
    suspend fun insertProgramStatuses(statuses: List<ProgramStatusEntity>)

    @Insert
    suspend fun insertProgramDocuments(documents: List<ProgramDocumentEntity>)

    @Insert
    suspend fun insertProgramChunks(chunks: List<ProgramChunkEntity>)

    @Insert
    suspend fun insertChunkThemes(tags: List<ChunkThemeEntity>)

    @Upsert
    suspend fun upsertPack(pack: DataPackEntity)

    @Query("SELECT rag_config FROM data_packs WHERE id = :id")
    suspend fun getRagConfig(id: String): String?

    @Transaction
    @Query("SELECT * FROM candidates")
    suspend fun getCandidates(): List<CandidateWithCoverage>

    @Query("SELECT * FROM legal_articles ORDER BY text_id, ordinal")
    suspend fun getAllLegalArticles(): List<LegalArticleEntity>

    @Transaction
    @Query("SELECT * FROM program_chunks ORDER BY candidate_id, document_id, ordinal")
    suspend fun getAllProgramChunks(): List<ChunkWithThemes>

    @Query("SELECT * FROM program_documents")
    suspend fun getAllProgramDocuments(): List<ProgramDocumentEntity>

    @Query("SELECT * FROM legal_texts ORDER BY position, id")
    fun observeLegalTexts(): Flow<List<LegalTextEntity>>

    @Query("SELECT * FROM legal_articles WHERE text_id = :textId ORDER BY ordinal")
    fun observeLegalArticles(textId: String): Flow<List<LegalArticleEntity>>

    @Query("SELECT * FROM legal_texts")
    suspend fun getLegalTexts(): List<LegalTextEntity>

    @Query("SELECT * FROM legal_articles WHERE id IN (:ids)")
    suspend fun getLegalArticles(ids: List<String>): List<LegalArticleEntity>

    /** [match] is an FTS expression built by FullTextSearch.matchExpression (no user syntax). */
    @Query(
        "SELECT article_id, matchinfo(legal_fts, 'pcnalx') AS match_info FROM legal_fts WHERE legal_fts MATCH :match",
    )
    suspend fun searchLegal(match: String): List<LegalFtsMatch>

    @Query("DELETE FROM legal_texts")
    suspend fun deleteLegalTexts()

    @Query("DELETE FROM legal_fts")
    suspend fun deleteLegalFts()

    @Insert
    suspend fun insertLegalTexts(texts: List<LegalTextEntity>)

    @Insert
    suspend fun insertLegalArticles(articles: List<LegalArticleEntity>)

    @Insert
    suspend fun insertLegalFts(rows: List<LegalFtsEntity>)

    /**
     * Replaces the `core` pack and the `programs` and `legal` packs of the same manifest in one
     * transaction: readers never see a half-written pack, nor programmes of another candidate list.
     * A null [programs] or [legal] removes what was installed.
     */
    @Transaction
    suspend fun replaceCorePack(
        pack: DataPackEntity,
        candidates: List<CandidateEntity>,
        coverage: List<CandidateCoverageEntity>,
        mandates: List<MandateEntity> = emptyList(),
        votes: List<VoteEntity> = emptyList(),
        speeches: List<SpeechEntity> = emptyList(),
        themes: List<ThemeEntity> = emptyList(),
        voteThemes: List<VoteThemeEntity> = emptyList(),
        programs: ProgramsRows? = null,
        legal: LegalRows? = null,
    ) {
        // Everything attached to a candidate (coverage, mandates, votes and their themes, speeches,
        // programmes and their excerpts) is removed with it (ON DELETE CASCADE).
        deleteCandidates()
        deleteThemes()
        deletePack(DataPackInfo.PROGRAMS)
        // Legal articles go with their text (ON DELETE CASCADE); the FTS table has no foreign key.
        deleteLegalTexts()
        deleteLegalFts()
        deletePack(DataPackInfo.LEGAL)
        insertCandidates(candidates)
        insertCoverage(coverage)
        insertMandates(mandates)
        insertVotes(votes)
        insertSpeeches(speeches)
        insertThemes(themes)
        insertVoteThemes(voteThemes)
        upsertPack(pack)
        if (programs != null) {
            insertProgramStatuses(programs.statuses)
            insertProgramDocuments(programs.documents)
            insertProgramChunks(programs.chunks)
            insertChunkThemes(programs.chunkThemes)
            upsertPack(programs.pack)
        }
        if (legal != null) {
            insertLegalTexts(legal.texts)
            insertLegalArticles(legal.articles)
            insertLegalFts(legal.fts)
            upsertPack(legal.pack)
        }
    }
}
