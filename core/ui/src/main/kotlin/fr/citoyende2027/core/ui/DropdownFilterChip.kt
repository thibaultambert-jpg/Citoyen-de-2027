package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * A filter chip opening a menu of [options]. [allLabel] adds a "no filter" entry (null); without it
 * a value must be chosen.
 */
@Composable
fun <T : Any> DropdownFilterChip(
    title: String,
    selected: T?,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = stringResource(R.string.core_ui_filter_all),
) {
    var expanded by remember { mutableStateOf(false) }
    val current = selected?.let { label(it) } ?: allLabel.orEmpty()
    Box(modifier = modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text("$title : $current") },
            modifier = Modifier.semantics { stateDescription = current },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allLabel != null) {
                DropdownMenuItem(text = { Text(allLabel) }, onClick = {
                    expanded = false
                    onSelect(null)
                })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}
