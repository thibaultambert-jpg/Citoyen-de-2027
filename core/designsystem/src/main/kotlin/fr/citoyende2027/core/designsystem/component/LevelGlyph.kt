package fr.citoyende2027.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme

/** Shape of a [LevelGlyph]. The meaning is always written next to it: shape alone is not enough. */
enum class GlyphShape { Full, Half, Slashed, Dashed }

/**
 * Pictogram that tells states apart by shape, never by colour (AGENTS.md §2.1, §11). Hidden from
 * accessibility services, since the state label is always displayed beside it.
 */
@Composable
fun LevelGlyph(
    shape: GlyphShape,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val stroke = 1.6.dp.toPx()
        val dash = stroke * 1.5f
        val inset = stroke / 2
        val diameter = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        when (shape) {
            GlyphShape.Full -> drawCircle(color)
            GlyphShape.Half -> {
                drawCircle(color, radius = diameter.width / 2, style = Stroke(stroke))
                drawArc(
                    color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = diameter,
                )
            }
            GlyphShape.Slashed -> {
                drawCircle(color, radius = diameter.width / 2, style = Stroke(stroke))
                val y = this.size.height / 2
                drawLine(color, Offset(this.size.width * 0.25f, y), Offset(this.size.width * 0.75f, y), stroke)
            }
            GlyphShape.Dashed -> drawCircle(
                color,
                radius = diameter.width / 2,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
    }
}

@Preview
@Composable
private fun LevelGlyphPreview() {
    CitoyenTheme { GlyphShape.entries.forEach { LevelGlyph(it, size = 24.dp) } }
}
