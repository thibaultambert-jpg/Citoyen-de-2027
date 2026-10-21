package fr.citoyende2027.feature.institutions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.model.LegalArticle

/** "Article 7", or the innermost heading for an unnumbered article (e.g. a preamble paragraph). */
@Composable
internal fun articleTitle(article: LegalArticle): String = when {
    article.number.isNotBlank() -> stringResource(R.string.feature_institutions_article, article.number)
    else -> article.headingPath.lastOrNull().orEmpty()
}
