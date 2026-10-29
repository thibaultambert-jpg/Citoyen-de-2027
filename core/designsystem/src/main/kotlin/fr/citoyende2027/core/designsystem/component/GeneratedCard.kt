package fr.citoyende2027.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.theme.citoyenColors

/**
 * Card for text written by the local LLM, on the sand surface so it is never mistaken for an
 * official excerpt (AGENTS.md §10). Official excerpts stay on neutral cards.
 */
@Composable
fun GeneratedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.citoyenColors
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = colors.generatedContainer,
            contentColor = colors.onGeneratedContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
