package fr.citoyende2027.feature.institutions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.model.LegalArticle

/**
 * "Article 7" for a numbered article; a number without digit (e.g. "PREAMBULE" in the source) is
 * shown as published; an article without number takes its innermost heading.
 */
@Composable
internal fun articleTitle(article: LegalArticle): String = when {
    article.number.any(Char::isDigit) -> stringResource(R.string.feature_institutions_article, article.number)
    article.number.isNotBlank() -> article.number
    else -> article.headingPath.lastOrNull().orEmpty()
}
