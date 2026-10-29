package fr.citoyende2027.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme

/**
 * Tile for one facet of a candidate (programme, mandates, votes, speeches): same layout for every
 * candidate, whether the facet is filled or not (template parity, AGENTS.md §10).
 */
@Composable
fun FacetTile(
    title: String,
    stateLabel: String,
    glyph: GlyphShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    description: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            description?.let { contentDescription = it }
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(CitoyenIcons.ChevronRight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelGlyph(glyph)
                Text(stateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview
@Composable
private fun FacetTilePreview() {
    CitoyenTheme { FacetTile("Programme", "Complet", GlyphShape.Full, onClick = {}, detail = "3 documents") }
}
