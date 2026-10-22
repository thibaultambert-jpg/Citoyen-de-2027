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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.ui.MandateItem
import fr.citoyende2027.core.ui.R as UiR
import fr.citoyende2027.core.ui.labelRes
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.candidate.R

/** Order of the timeline sections (AGENTS.md §11): AN, Sénat, PE, government, local mandates. */
private val BODY_ORDER = listOf(Body.AN, Body.SENAT, Body.PE, Body.GOVERNMENT, Body.LOCAL)

@Composable
internal fun MandatesTab(mandates: List<Mandate>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.feature_candidate_mandates_scope), style = MaterialTheme.typography.bodyMedium)
        }
        if (mandates.isEmpty()) {
            item { Text(stringResource(DesignR.string.core_designsystem_not_available)) }
        }
        BODY_ORDER.forEach { body ->
            val ofBody = mandates.filter { it.body == body }.sortedByDescending { it.startDate }
            if (ofBody.isNotEmpty()) {
                item(key = "header-$body") {
                    Text(
                        text = stringResource(body.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                items(ofBody, key = { it.id }) { mandate ->
                    MandateItem(mandate)
                    HorizontalDivider()
                }
            }
        }
    }
}
