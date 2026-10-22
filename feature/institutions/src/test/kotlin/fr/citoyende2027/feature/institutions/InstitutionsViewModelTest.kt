package fr.citoyende2027.feature.institutions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.LegalRepository
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalSearchHit
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.testing.FictionalLegal
import fr.citoyende2027.core.testing.MainDispatcherExtension
import fr.citoyende2027.feature.institutions.navigation.LegalTextRoute
import fr.citoyende2027.feature.institutions.text.LegalTextUiState
import fr.citoyende2027.feature.institutions.text.LegalTextViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private class FixtureLegalRepository : LegalRepository {
    val queries = mutableListOf<String>()

    override fun observeTexts(): Flow<List<LegalText>> = flowOf(FictionalLegal.pack.texts)

    override fun observeArticles(textId: String): Flow<List<LegalArticle>> =
        flowOf(FictionalLegal.articles.filter { it.textId == textId })

    override fun observePackInfo(): Flow<DataPackInfo?> = flowOf(FictionalLegal.info)

    override suspend fun search(query: String, limit: Int): List<LegalSearchHit> {
        queries += query
        val article = FictionalLegal.articles.last()
        return listOf(LegalSearchHit(article, FictionalLegal.textB, article.text, 1.0))
    }
}

class InstitutionsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `texts are listed and a typed query is searched`() = runTest {
        val repository = FixtureLegalRepository()
        val viewModel = InstitutionsViewModel(repository)
        viewModel.uiState.test {
            val initial = awaitUntil { it.texts.isNotEmpty() }
            assertEquals(listOf(FictionalLegal.textA, FictionalLegal.textB), initial.texts)
            assertNull(initial.results)

            viewModel.onQueryChange("élection")
            val found = awaitUntil { it.results != null }
            assertEquals(listOf("fictif-texte-b-art-1"), found.results?.map { it.article.id })
            assertEquals(listOf("élection"), repository.queries)

            viewModel.onQueryChange(" ")
            assertNull(awaitUntil { it.query == " " && it.results == null }.results)
        }
    }

    @Test
    fun `a text opens at the requested article`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                LegalTextRoute.TEXT_ARG to FictionalLegal.textA.id,
                LegalTextRoute.ARTICLE_ARG to "fictif-texte-a-art-3",
            ),
        )
        LegalTextViewModel(handle, FixtureLegalRepository()).uiState.test {
            val state = awaitItemOfType<LegalTextUiState.Ready>()
            assertEquals(FictionalLegal.textA, state.text)
            assertEquals(2, state.initialIndex)
        }
    }

    @Test
    fun `an unknown text is reported`() = runTest {
        val handle = SavedStateHandle(mapOf(LegalTextRoute.TEXT_ARG to "fictif-absent"))
        LegalTextViewModel(handle, FixtureLegalRepository()).uiState.test {
            awaitItemOfType<LegalTextUiState.NotFound>()
        }
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend inline fun <reified S> ReceiveTurbine<LegalTextUiState>.awaitItemOfType(): S =
        awaitUntil { it is S } as S
}
