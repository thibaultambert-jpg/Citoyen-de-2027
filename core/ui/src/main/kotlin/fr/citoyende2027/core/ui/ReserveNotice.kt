package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.ReservePeriod

/**
 * Shown in place of campaign content (programmes, speeches) during the electoral reserve period
 * (AGENTS.md §10), the same for every candidate.
 */
@Composable
fun ReserveNotice(reserve: ReservePeriod, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.core_ui_reserve), style = MaterialTheme.typography.bodyLarge)
            reserve.since?.let {
                Text(
                    stringResource(R.string.core_ui_reserve_since, it.toFrenchDate()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
