package fr.citoyende2027.core.data

import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.data.repository.OfflineLegalRepository
import fr.citoyende2027.core.database.model.LegalFtsMatch
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.FictionalLegal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineLegalRepositoryTest {
    private val dao = FakeCorePackDao()
    private val repository = OfflineLegalRepository(dao)
    private val store = CorePackStore(dao)

    /** matchinfo 'pcnalx' for one phrase over (article_id, body): [hitsInRow] hits in this row. */
    private fun match(articleId: String, hitsInRow: Int) = LegalFtsMatch(
        articleId,
        ByteBuffer.allocate(13 * 4).order(ByteOrder.nativeOrder()).apply {
            intArrayOf(1, 2, 4, 0, 10, 0, 10, 0, 0, 0, hitsInRow, 5, 2).forEach { putInt(it) }
        }.array(),
    )

    @Test
    fun `texts and articles are exposed in corpus order and indexed normalised`() = runTest {
        store.install(FictionalCandidates.corePack, legal = FictionalLegal.pack)
        repository.observeTexts().test {
            assertEquals(listOf(FictionalLegal.textA, FictionalLegal.textB), awaitItem())
        }
        repository.observeArticles(FictionalLegal.textA.id).test {
            assertEquals(FictionalLegal.articles.filter { it.textId == FictionalLegal.textA.id }, awaitItem())
        }
        val indexed = dao.legalFts.value.first { it.articleId == "fictif-texte-b-art-1" }.body
        assertTrue(indexed.startsWith("article l. 1 livre fictif election fictive"), indexed)
    }

    @Test
    fun `search ranks by BM25 and gives verbatim excerpts`() = runTest {
        store.install(FictionalCandidates.corePack, legal = FictionalLegal.pack)
        var expression = ""
        dao.legalMatches = { match ->
            expression = match
            listOf(match("fictif-texte-a-art-2", hitsInRow = 1), match("fictif-texte-b-art-1", hitsInRow = 3))
        }
        val hits = repository.search("Élection")
        assertEquals("election*", expression)
        assertEquals(listOf("fictif-texte-b-art-1", "fictif-texte-a-art-2"), hits.map { it.article.id })
        assertEquals(FictionalLegal.textB, hits.first().text)
        assertEquals(hits.first().article.text, hits.first().excerpt)
        assertEquals(emptyList<Any>(), repository.search("  ** "))
    }
}
