package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.component.GlyphShape
import fr.citoyende2027.core.designsystem.component.LevelGlyph
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage

/** Shape that stands for a coverage level; the level is always also written out. */
val CoverageLevel.glyphShape: GlyphShape
    get() = when (this) {
        CoverageLevel.AVAILABLE -> GlyphShape.Full
        CoverageLevel.PARTIAL -> GlyphShape.Half
        CoverageLevel.NOT_APPLICABLE -> GlyphShape.Slashed
        CoverageLevel.UNAVAILABLE -> GlyphShape.Dashed
    }

/**
 * Compact coverage for list rows (AGENTS.md §10): one shape pictogram and the facet name per facet,
 * same colour for every level. Screen readers get "facet : level".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoverageGlyphRow(
    coverage: DataCoverage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CoverageFacet.entries.forEach { facet ->
            val level = coverage[facet]
            val facetLabel = stringResource(facet.labelRes)
            val description = stringResource(R.string.core_ui_coverage_item, facetLabel, stringResource(level.labelRes))
            Row(
                modifier = Modifier.semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelGlyph(level.glyphShape, color = color)
                Text(
                    text = facetLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun CoverageGlyphRowPreview() {
    CitoyenTheme {
        CoverageGlyphRow(
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
