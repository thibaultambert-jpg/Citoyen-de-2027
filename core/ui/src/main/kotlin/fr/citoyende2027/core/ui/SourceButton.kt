package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.SourceRef

/** "Source" button showing name, official link, retrieval date, licence and pack version (§2.3). */
@Composable
fun SourceButton(source: SourceRef, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Text(stringResource(fr.citoyende2027.core.designsystem.R.string.core_designsystem_source))
    }
    if (open) {
        SourceDialog(source = source, onDismiss = { open = false })
    }
}

@Composable
private fun SourceDialog(source: SourceRef, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(fr.citoyende2027.core.designsystem.R.string.core_designsystem_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.core_ui_source_retrieved_on, source.retrievedOn.toFrenchDate()))
                Text(stringResource(R.string.core_ui_source_license, source.license))
                Text(stringResource(R.string.core_ui_source_pack_version, source.packVersion))
                TextButton(onClick = { uriHandler.openUri(source.url) }) {
                    Text(source.url)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_close)) }
        },
    )
}
