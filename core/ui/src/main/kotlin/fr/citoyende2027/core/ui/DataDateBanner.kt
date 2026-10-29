package fr.citoyende2027.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.DataPackInfo

/** "Données du JJ/MM/AAAA", visible on every data screen (AGENTS.md §5). */
@Composable
fun DataDateBanner(info: DataPackInfo?, modifier: Modifier = Modifier) {
    val text = when {
        info == null -> stringResource(R.string.core_ui_data_none)
        info.isFictional -> stringResource(R.string.core_ui_data_date_fictional, info.generatedOn.toFrenchDate())
        else -> stringResource(R.string.core_ui_data_date, info.generatedOn.toFrenchDate())
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            color = Color.Transparent,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
