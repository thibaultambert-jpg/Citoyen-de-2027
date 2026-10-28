package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.ThemeTag

/** Programme status, followed by the reminder that missing data is not a missing position. */
@Composable
fun ProgramStatusText(status: ProgramStatus, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(status.labelRes), style = MaterialTheme.typography.bodyLarge)
        if (status != ProgramStatus.COMPLET) {
            Text(stringResource(R.string.core_ui_absence_is_not_position), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A programme document: title, version and date, with its "Source" button. */
@Composable
fun ProgramDocumentRow(document: ProgramDocument, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                R.string.core_ui_program_document,
                document.title,
                document.version,
                document.publishedOn.toFrenchDate(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        SourceButton(document.source)
    }
}

/**
 * A verbatim programme excerpt (AGENTS.md §7.5): headings, exact text, page or web anchor, themes
 * with how they were attached, and the document's source. Same card for every candidate.
 */
@Composable
fun ProgramExcerptCard(
    chunk: ProgramChunk,
    document: ProgramDocument?,
    themeLabels: Map<ThemeId, String>,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (chunk.headingPath.isNotEmpty()) {
                Text(chunk.headingPath.joinToString(" › "), style = MaterialTheme.typography.labelLarge)
            }
            Text(chunk.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = chunk.page?.let { stringResource(R.string.core_ui_excerpt_page, it) }
                    ?: stringResource(R.string.core_ui_excerpt_web),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.core_ui_excerpt_verbatim), style = MaterialTheme.typography.bodySmall)
            ThemesLine(chunk.themes, themeLabels)
            if (document != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { uriHandler.openUri(document.linkTo(chunk)) }) {
                        Text(stringResource(R.string.core_ui_excerpt_open))
                    }
                    SourceButton(document.source)
                }
            }
        }
    }
}

/** Themes of an item and how each was attached (keyword rule or human review, AGENTS.md §7.4). */
@Composable
internal fun ThemesLine(tags: List<ThemeTag>, themeLabels: Map<ThemeId, String>) {
    val labels = tags.mapNotNull { tag ->
        themeLabels[tag.themeId]?.let { label ->
            stringResource(R.string.core_ui_theme_with_method, label, stringResource(tag.method.labelRes))
        }
    }
    if (labels.isNotEmpty()) {
        Text(
            stringResource(R.string.core_ui_themes, labels.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Official link to the excerpt: web anchor, or PDF page fragment (RFC 8118 "page="), when known. */
fun ProgramDocument.linkTo(chunk: ProgramChunk): String = when {
    chunk.anchor != null -> "${source.url}#${chunk.anchor}"
    chunk.page != null -> "${source.url}#page=${chunk.page}"
    else -> source.url
}
