package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.Mandate

/** A mandate: label as built from the official record, period, and its source. */
@Composable
fun MandateItem(mandate: Mandate, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mandate.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = mandate.endDate?.let {
                    stringResource(R.string.core_ui_period, mandate.startDate.toFrenchDate(), it.toFrenchDate())
                } ?: stringResource(R.string.core_ui_period_open, mandate.startDate.toFrenchDate()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SourceButton(mandate.source)
    }
}
