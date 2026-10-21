package fr.citoyende2027.feature.institutions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.LegalRepository
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalSearchHit
import fr.citoyende2027.core.model.LegalText
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class InstitutionsUiState(
    val texts: List<LegalText> = emptyList(),
    val dataInfo: DataPackInfo? = null,
    val query: String = "",
    /** Null while no search is typed. */
    val results: List<LegalSearchHit>? = null,
)

/** Legal corpus home: texts in the pipeline's order and offline lexical search (no AI). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InstitutionsViewModel @Inject constructor(repository: LegalRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    private val results = query.mapLatest { text ->
        if (text.isBlank()) null else repository.search(text)
    }

    val uiState: StateFlow<InstitutionsUiState> = combine(
        repository.observeTexts(),
        repository.observePackInfo(),
        query,
        results,
    ) { texts, info, query, results -> InstitutionsUiState(texts, info, query, results) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstitutionsUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }
}
