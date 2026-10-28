package fr.citoyende2027.feature.institutions.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.LegalRepository
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.feature.institutions.navigation.LegalTextRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface LegalTextUiState {
    data object Loading : LegalTextUiState

    data object NotFound : LegalTextUiState

    data class Ready(
        val text: LegalText,
        val articles: List<LegalArticle>,
        val dataInfo: DataPackInfo?,
        /** Index in [articles] to show first (search result), or 0. */
        val initialIndex: Int,
    ) : LegalTextUiState
}

@HiltViewModel
class LegalTextViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: LegalRepository) :
    ViewModel() {
    private val textId = checkNotNull(savedStateHandle.get<String>(LegalTextRoute.TEXT_ARG))
    private val articleId = savedStateHandle.get<String>(LegalTextRoute.ARTICLE_ARG)

    val uiState: StateFlow<LegalTextUiState> = combine(
        repository.observeTexts(),
        repository.observeArticles(textId),
        repository.observePackInfo(),
    ) { texts, articles, info ->
        val text = texts.firstOrNull { it.id == textId }
        if (text == null) {
            LegalTextUiState.NotFound
        } else {
            LegalTextUiState.Ready(
                text = text,
                articles = articles,
                dataInfo = info,
                initialIndex = articles.indexOfFirst { it.id == articleId }.coerceAtLeast(0),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LegalTextUiState.Loading)
}
