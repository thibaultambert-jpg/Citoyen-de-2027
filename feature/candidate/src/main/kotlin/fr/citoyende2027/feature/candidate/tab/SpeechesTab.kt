package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.candidate.R

/** Plenary interventions then official statements: titles, dates and official links only. */
@Composable
internal fun SpeechesTab(speeches: List<Speech>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.feature_candidate_speeches_scope), style = MaterialTheme.typography.bodyMedium)
        }
        SpeechKind.entries.forEach { kind ->
            val ofKind = speeches.filter { it.kind == kind }.sortedByDescending { it.date }
            item(key = "header-$kind") {
                Text(
                    text = stringResource(
                        when (kind) {
                            SpeechKind.PLENARY_SPEECH -> R.string.feature_candidate_speeches_plenary
                            SpeechKind.OFFICIAL_STATEMENT -> R.string.feature_candidate_speeches_statements
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (ofKind.isEmpty()) {
                item(key = "empty-$kind") { Text(stringResource(DesignR.string.core_designsystem_not_available)) }
            }
            items(ofKind, key = { it.id }) { speech ->
                SpeechItem(speech)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SpeechItem(speech: Speech) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(speech.date.toFrenchDate(), style = MaterialTheme.typography.labelLarge)
        Text(speech.title, style = MaterialTheme.typography.bodyLarge)
        speech.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { uriHandler.openUri(speech.url) }) {
                Text(stringResource(R.string.feature_candidate_speech_official_link))
            }
            SourceButton(speech.source)
        }
    }
}
