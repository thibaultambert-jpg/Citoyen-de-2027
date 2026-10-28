package fr.citoyende2027.feature.institutions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.model.LegalSearchHit
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.ui.DataDateBanner

@Composable
internal fun InstitutionsScreen(
    onOpenText: (textId: String, articleId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstitutionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InstitutionsContent(state, viewModel::onQueryChange, onOpenText, modifier)
}

@Composable
internal fun InstitutionsContent(
    state: InstitutionsUiState,
    onQueryChange: (String) -> Unit,
    onOpenText: (textId: String, articleId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        DataDateBanner(state.dataInfo)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.feature_institutions_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (state.texts.isEmpty()) {
                item { Text(stringResource(R.string.feature_institutions_no_data)) }
                return@LazyColumn
            }
            item {
                Text(stringResource(R.string.feature_institutions_intro), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.feature_institutions_search_label)) },
                    leadingIcon = { Icon(painterResource(CitoyenIcons.Search), contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    painterResource(CitoyenIcons.Clear),
                                    stringResource(R.string.feature_institutions_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val results = state.results
            if (results == null) {
                item {
                    Text(
                        text = stringResource(R.string.feature_institutions_texts),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                items(state.texts, key = { it.id }) { text ->
                    TextRow(text, onClick = { onOpenText(text.id, null) })
                    HorizontalDivider()
                }
            } else {
                item {
                    Text(
                        text = if (results.isEmpty()) {
                            stringResource(R.string.feature_institutions_no_result)
                        } else {
                            stringResource(R.string.feature_institutions_results_count, results.size)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(results, key = { it.article.id }) { hit ->
                    ResultRow(hit, onClick = { onOpenText(hit.text.id, hit.article.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TextRow(text: LegalText, onClick: () -> Unit) {
    Text(
        text = text.title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun ResultRow(hit: LegalSearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(hit.text.title, style = MaterialTheme.typography.labelLarge)
        Text(articleTitle(hit.article), style = MaterialTheme.typography.titleSmall)
        Text(hit.excerpt, style = MaterialTheme.typography.bodyMedium)
    }
}
