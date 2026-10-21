package fr.citoyende2027.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme

/**
 * Uniform avatar: same shape, size and colours for every candidate (AGENTS.md §2.1).
 * Hidden from accessibility services, since the name is always displayed next to it.
 */
@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun InitialsAvatarPreview() {
    CitoyenTheme { InitialsAvatar("CA") }
}
