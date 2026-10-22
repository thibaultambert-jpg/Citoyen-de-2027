package fr.citoyende2027.feature.institutions.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.ui.DataDateBanner
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.institutions.R
import fr.citoyende2027.feature.institutions.articleTitle

@Composable
internal fun LegalTextScreen(onBack: () -> Unit, viewModel: LegalTextViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LegalTextContent(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalTextContent(state: LegalTextUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    (state as? LegalTextUiState.Ready)?.text?.title ?: stringResource(
                        R.string.feature_institutions_title,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(CitoyenIcons.Back), stringResource(R.string.feature_institutions_back))
                }
            },
        )
        when (state) {
            LegalTextUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            LegalTextUiState.NotFound -> Text(
                text = stringResource(R.string.feature_institutions_no_data),
                modifier = Modifier.padding(16.dp),
            )

            is LegalTextUiState.Ready -> {
                DataDateBanner(state.dataInfo)
                Articles(state)
            }
        }
    }
}

/** The whole text in reading order; a heading line appears where the headings change. */
@Composable
private fun Articles(state: LegalTextUiState.Ready) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialIndex)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(state.articles, key = { _, article -> article.id }) { index, article ->
            val previous = state.articles.getOrNull(index - 1)?.headingPath.orEmpty()
            val newHeadings = article.headingPath.filterIndexed { level, heading ->
                previous.getOrNull(level) != heading
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                newHeadings.forEach { heading ->
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            heading()
                        },
                    )
                }
                ArticleItem(article, state.text)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ArticleItem(article: LegalArticle, text: LegalText) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (article.number.isNotBlank()) {
            Text(articleTitle(article), style = MaterialTheme.typography.titleSmall)
        }
        Text(article.text, style = MaterialTheme.typography.bodyLarge)
        article.inForceSince?.let {
            Text(
                text = stringResource(R.string.feature_institutions_in_force, it.toFrenchDate()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        article.inForceUntil?.let {
            Text(
                text = stringResource(R.string.feature_institutions_in_force_until, it.toFrenchDate()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { uriHandler.openUri(article.url) }) {
                Text(stringResource(R.string.feature_institutions_official))
            }
            SourceButton(text.source)
        }
    }
}
