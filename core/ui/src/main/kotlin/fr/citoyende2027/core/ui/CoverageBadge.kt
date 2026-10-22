package fr.citoyende2027.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage

/**
 * Data coverage per facet (AGENTS.md §10). Every level uses the same colours: the level is carried
 * by its text, never by colour, so no facet looks "better" than another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoverageBadge(coverage: DataCoverage, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverageFacet.entries.forEach { facet ->
            CoverageChip(facet, coverage[facet])
        }
    }
}

@Composable
private fun CoverageChip(facet: CoverageFacet, level: CoverageLevel) {
    val facetLabel = stringResource(facet.labelRes)
    val levelLabel = stringResource(level.labelRes)
    val description = stringResource(R.string.core_ui_coverage_item, facetLabel, levelLabel)
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clearAndSetSemantics {},
        )
    }
}

@Preview
@Composable
private fun CoverageBadgePreview() {
    CitoyenTheme {
        CoverageBadge(
            DataCoverage(
                mapOf(
                    CoverageFacet.PROGRAM to CoverageLevel.AVAILABLE,
                    CoverageFacet.MANDATES to CoverageLevel.PARTIAL,
                    CoverageFacet.VOTES to CoverageLevel.NOT_APPLICABLE,
                    CoverageFacet.SPEECHES to CoverageLevel.UNAVAILABLE,
                ),
            ),
        )
    }
}
