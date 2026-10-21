package fr.citoyende2027.core.data.repository

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.FullTextSearch
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalSearchHit
import fr.citoyende2027.core.model.LegalText
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Legal corpus (AGENTS.md §8, §11 "Institutions"): navigation and lexical search, without AI. */
interface LegalRepository {
    /** Texts in the order set by the pipeline input. */
    fun observeTexts(): Flow<List<LegalText>>

    /** Articles of a text in reading order. */
    fun observeArticles(textId: String): Flow<List<LegalArticle>>

    fun observePackInfo(): Flow<DataPackInfo?>

    /** Articles matching every term of [query] (accent- and case-insensitive), best BM25 first. */
    suspend fun search(query: String, limit: Int = 50): List<LegalSearchHit>
}

internal class OfflineLegalRepository @Inject constructor(private val dao: CorePackDao) : LegalRepository {
    override fun observeTexts(): Flow<List<LegalText>> =
        dao.observeLegalTexts().map { list -> list.map { it.asModel() } }

    override fun observeArticles(textId: String): Flow<List<LegalArticle>> =
        dao.observeLegalArticles(textId).map { list -> list.map { it.asModel() } }

    override fun observePackInfo(): Flow<DataPackInfo?> = dao.observePack(DataPackInfo.LEGAL).map { it?.asModel() }

    override suspend fun search(query: String, limit: Int): List<LegalSearchHit> {
        val match = FullTextSearch.matchExpression(query) ?: return emptyList()
        val scores = dao.searchLegal(match).associate { it.articleId to FullTextSearch.bm25(it.matchInfo.toInts()) }
        if (scores.isEmpty()) return emptyList()
        val texts = dao.getLegalTexts().map { it.asModel() }.associateBy { it.id }
        val terms = FullTextSearch.terms(query)
        return scores.keys.chunked(MAX_SQL_ARGUMENTS)
            .flatMap { ids -> dao.getLegalArticles(ids) }
            .map { it.asModel() }
            .mapNotNull { article ->
                texts[article.textId]?.let { text ->
                    LegalSearchHit(
                        article,
                        text,
                        FullTextSearch.excerpt(article.text, terms),
                        scores.getValue(article.id),
                    )
                }
            }
            // Deterministic order: score, then the corpus order (text position, article order).
            .sortedWith(
                compareByDescending<LegalSearchHit> { it.score }
                    .thenBy { it.text.position }
                    .thenBy { it.article.ordinal },
            )
            .take(limit)
    }

    private companion object {
        /** Below SQLite's default limit of bound parameters on old versions (999). */
        const val MAX_SQL_ARGUMENTS = 900
    }
}

/** matchinfo() returns 32-bit unsigned integers in the machine's byte order. */
private fun ByteArray.toInts(): IntArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder()).asIntBuffer()
    return IntArray(buffer.remaining()).also { buffer.get(it) }
}
