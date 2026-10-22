package fr.citoyende2027.core.data

import fr.citoyende2027.core.database.dao.CorePackDao
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory CorePackDao: the Room implementation itself is covered in :core:database. */
internal class FakeCorePackDao : CorePackDao {
    private val candidates = MutableStateFlow<List<CandidateEntity>>(emptyList())
    private val coverage = MutableStateFlow<List<CandidateCoverageEntity>>(emptyList())
    private val packs = MutableStateFlow<Map<String, DataPackEntity>>(emptyMap())

    override fun observeCandidates(): Flow<List<CandidateWithCoverage>> = candidates.map { list ->
        list.map { c -> CandidateWithCoverage(c, coverage.value.filter { it.candidateId == c.id }) }
    }

    override fun observeCandidate(id: String): Flow<CandidateWithCoverage?> =
        observeCandidates().map { list -> list.firstOrNull { it.candidate.id == id } }

    override fun observePack(id: String): Flow<DataPackEntity?> = packs.map { it[id] }

    override suspend fun getPack(id: String): DataPackEntity? = packs.value[id]

    override suspend fun countPacks(id: String): Int = if (packs.value.containsKey(id)) 1 else 0

    override suspend fun deleteCandidates() {
        voteThemes.value = emptyList()
        programStatuses.value = emptyList()
        programDocuments.value = emptyList()
        programChunks.value = emptyList()
        chunkThemes.value = emptyList()
        mandates.value = emptyList()
        votes.value = emptyList()
        speeches.value = emptyList()
        coverage.value = emptyList()
        candidates.value = emptyList()
    }

    override suspend fun insertCandidates(candidates: List<CandidateEntity>) {
        this.candidates.value += candidates
    }

    override suspend fun insertCoverage(coverage: List<CandidateCoverageEntity>) {
        this.coverage.value += coverage
    }

    val mandates = MutableStateFlow<List<MandateEntity>>(emptyList())
    val votes = MutableStateFlow<List<VoteEntity>>(emptyList())
    val speeches = MutableStateFlow<List<SpeechEntity>>(emptyList())

    override suspend fun insertSpeeches(speeches: List<SpeechEntity>) {
        this.speeches.value += speeches
    }

    override fun observeSpeeches(candidateId: String): Flow<List<SpeechEntity>> =
        speeches.map { list -> list.filter { it.candidateId == candidateId } }

    override suspend fun insertMandates(mandates: List<MandateEntity>) {
        this.mandates.value += mandates
    }

    override suspend fun insertVotes(votes: List<VoteEntity>) {
        this.votes.value += votes
    }

    override fun observeMandates(candidateId: String): Flow<List<MandateEntity>> =
        mandates.map { list -> list.filter { it.candidateId == candidateId } }

    override fun observeVotes(candidateId: String): Flow<List<VoteWithThemes>> = votes.map { list ->
        list.filter { it.candidateId == candidateId }
            .map { vote -> VoteWithThemes(vote, voteThemes.value.filter { it.voteId == vote.id }) }
    }

    private val themes = MutableStateFlow<List<ThemeEntity>>(emptyList())
    private val voteThemes = MutableStateFlow<List<VoteThemeEntity>>(emptyList())
    private val programStatuses = MutableStateFlow<List<ProgramStatusEntity>>(emptyList())
    private val programDocuments = MutableStateFlow<List<ProgramDocumentEntity>>(emptyList())
    private val programChunks = MutableStateFlow<List<ProgramChunkEntity>>(emptyList())
    private val chunkThemes = MutableStateFlow<List<ChunkThemeEntity>>(emptyList())

    override fun observeThemes(): Flow<List<ThemeEntity>> = themes

    override fun observeProgramStatus(candidateId: String): Flow<String?> =
        programStatuses.map { list -> list.firstOrNull { it.candidateId == candidateId }?.status }

    override fun observeProgramDocuments(candidateId: String): Flow<List<ProgramDocumentEntity>> =
        programDocuments.map { list -> list.filter { it.candidateId == candidateId } }

    override fun observeProgramChunks(candidateId: String): Flow<List<ChunkWithThemes>> = programChunks.map { list ->
        list.filter { it.candidateId == candidateId }.sortedWith(compareBy({ it.documentId }, { it.ordinal }))
            .map { chunk -> ChunkWithThemes(chunk, chunkThemes.value.filter { it.chunkId == chunk.id }) }
    }

    override suspend fun deleteThemes() {
        themes.value = emptyList()
    }

    override suspend fun deletePack(id: String) {
        packs.value -= id
    }

    override suspend fun insertThemes(themes: List<ThemeEntity>) {
        this.themes.value += themes
    }

    override suspend fun insertVoteThemes(tags: List<VoteThemeEntity>) {
        voteThemes.value += tags
    }

    override suspend fun insertProgramStatuses(statuses: List<ProgramStatusEntity>) {
        programStatuses.value += statuses
    }

    override suspend fun insertProgramDocuments(documents: List<ProgramDocumentEntity>) {
        programDocuments.value += documents
    }

    override suspend fun insertProgramChunks(chunks: List<ProgramChunkEntity>) {
        programChunks.value += chunks
    }

    override suspend fun insertChunkThemes(tags: List<ChunkThemeEntity>) {
        chunkThemes.value += tags
    }

    private val legalTexts = MutableStateFlow<List<LegalTextEntity>>(emptyList())
    private val legalArticles = MutableStateFlow<List<LegalArticleEntity>>(emptyList())
    val legalFts = MutableStateFlow<List<LegalFtsEntity>>(emptyList())

    /** FTS is SQLite's job (tested in :core:database): matches are given by the test. */
    var legalMatches: (String) -> List<LegalFtsMatch> = { emptyList() }

    override fun observeLegalTexts(): Flow<List<LegalTextEntity>> =
        legalTexts.map { list -> list.sortedWith(compareBy({ it.position }, { it.id })) }

    override fun observeLegalArticles(textId: String): Flow<List<LegalArticleEntity>> =
        legalArticles.map { list -> list.filter { it.textId == textId }.sortedBy { it.ordinal } }

    override suspend fun getLegalTexts(): List<LegalTextEntity> = legalTexts.value

    override suspend fun getLegalArticles(ids: List<String>): List<LegalArticleEntity> =
        legalArticles.value.filter { it.id in ids }

    override suspend fun searchLegal(match: String): List<LegalFtsMatch> = legalMatches(match)

    override suspend fun getRagConfig(id: String): String? = packs.value[id]?.ragConfig

    override suspend fun getCandidates(): List<CandidateWithCoverage> = observeCandidatesNow()

    override suspend fun getAllLegalArticles(): List<LegalArticleEntity> =
        legalArticles.value.sortedWith(compareBy({ it.textId }, { it.ordinal }))

    override suspend fun getAllProgramChunks(): List<ChunkWithThemes> =
        programChunks.value.sortedWith(compareBy({ it.candidateId }, { it.documentId }, { it.ordinal }))
            .map { chunk -> ChunkWithThemes(chunk, chunkThemes.value.filter { it.chunkId == chunk.id }) }

    override suspend fun getAllProgramDocuments(): List<ProgramDocumentEntity> = programDocuments.value

    private fun observeCandidatesNow() = candidates.value.map { c ->
        CandidateWithCoverage(c, coverage.value.filter { it.candidateId == c.id })
    }

    override suspend fun deleteLegalTexts() {
        legalArticles.value = emptyList()
        legalTexts.value = emptyList()
    }

    override suspend fun deleteLegalFts() {
        legalFts.value = emptyList()
    }

    override suspend fun insertLegalTexts(texts: List<LegalTextEntity>) {
        legalTexts.value += texts
    }

    override suspend fun insertLegalArticles(articles: List<LegalArticleEntity>) {
        legalArticles.value += articles
    }

    override suspend fun insertLegalFts(rows: List<LegalFtsEntity>) {
        legalFts.value += rows
    }

    override suspend fun upsertPack(pack: DataPackEntity) {
        packs.value += pack.id to pack
    }
}
