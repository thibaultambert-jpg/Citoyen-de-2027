package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.ui.DropdownFilterChip
import fr.citoyende2027.core.ui.ProgramDocumentRow
import fr.citoyende2027.core.ui.ProgramExcerptCard
import fr.citoyende2027.core.ui.ProgramStatusText
import fr.citoyende2027.feature.candidate.R

/** Status, dated source documents, then verbatim excerpts filterable by theme (AGENTS.md §11). */
@Composable
internal fun ProgramTab(program: CandidateProgram, themes: List<Theme>, modifier: Modifier = Modifier) {
    var themeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = themes.firstOrNull { it.id.value == themeId }
    val present = themes.filter { theme -> program.chunks.any { it.hasTheme(theme.id) } }
    val shown = program.chunks.filter { selected == null || it.hasTheme(selected.id) }
    val themeLabels = themes.associate { it.id to it.label }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ProgramStatusText(program.status) }
        if (program.documents.isEmpty()) return@LazyColumn
        item { SectionHeading(stringResource(R.string.feature_candidate_program_documents)) }
        items(program.documents, key = { "doc-${it.id}" }) { ProgramDocumentRow(it) }
        item { SectionHeading(stringResource(R.string.feature_candidate_program_excerpts)) }
        item {
            Text(stringResource(R.string.feature_candidate_program_scope), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            DropdownFilterChip(
                title = stringResource(R.string.feature_candidate_filter_theme),
                selected = selected,
                options = present,
                label = { it.label },
                onSelect = { themeId = it?.id?.value },
            )
        }
        item {
            Text(
                text = stringResource(R.string.feature_candidate_program_count, shown.size, program.chunks.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(shown, key = { it.id }) { chunk ->
            ProgramExcerptCard(chunk, program.document(chunk.documentId), themeLabels)
        }
    }
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Themes that can be placed side by side: the undetermined theme groups nothing comparable. */
internal fun List<Theme>.comparable(): List<Theme> = filterNot { it.id.isUnknown }
