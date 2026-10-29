package fr.citoyende2027.core.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons

/** Top bar action that opens "Infos" (packs, text size, sources), the same on every top-level screen. */
@Composable
fun InfoActionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(CitoyenIcons.About), stringResource(R.string.core_ui_info_action))
    }
}
