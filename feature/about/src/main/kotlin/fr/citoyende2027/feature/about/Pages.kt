package fr.citoyende2027.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.data.repository.TextScale
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.llm.assistant.AssistantStatus
import java.time.LocalDate
import java.util.Locale

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Section(title: Int, text: Int) {
    Heading(stringResource(title))
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun packName(id: String): String = stringResource(
    when (id) {
        DataPackInfo.CORE -> R.string.feature_about_pack_core
        DataPackInfo.PROGRAMS -> R.string.feature_about_pack_programs
        DataPackInfo.LEGAL -> R.string.feature_about_pack_legal
        else -> R.string.feature_about_page_data
    },
)

/** Whole rows are the touch targets of radio buttons and switches (48 dp, AGENTS.md §11). */
private val MIN_TOUCH_TARGET = 48.dp

private fun megabytes(bytes: Long) = String.format(Locale.FRANCE, "%.1f", bytes / 1_000_000.0)

@Composable
internal fun DataPage(state: AboutUiState, onCheckForUpdates: () -> Unit) {
    if (state.reserve.hideCampaignContent) ReserveCard()
    Heading(stringResource(R.string.feature_about_data_installed))
    if (state.packs.isEmpty()) Text(stringResource(R.string.feature_about_data_none))
    state.packs.forEach { pack ->
        Column {
            Text(packName(pack.id), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.feature_about_pack_line, pack.dataDate.toFrenchDate(), pack.version))
            if (pack.isFictional) Text(stringResource(R.string.feature_about_pack_fictional))
        }
    }
    Text(stringResource(R.string.feature_about_updates_explain), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = onCheckForUpdates) { Text(stringResource(R.string.feature_about_check_updates)) }
    if (state.syncRequested) Text(stringResource(R.string.feature_about_check_requested))
    Heading(stringResource(R.string.feature_about_history))
    if (state.history.isEmpty()) Text(stringResource(R.string.feature_about_history_empty))
    state.history.forEach { update ->
        Text(
            stringResource(
                R.string.feature_about_history_line,
                packName(update.packId),
                LocalDate.parse(update.dataDate).toFrenchDate(),
                LocalDate.parse(update.installedOn).toFrenchDate(),
            ),
        )
    }
}

@Composable
internal fun SettingsPage(
    state: AboutUiState,
    onTextScale: (TextScale) -> Unit,
    onAssistantEnabled: (Boolean) -> Unit,
    onRemoveModel: () -> Unit,
) {
    Heading(stringResource(R.string.feature_about_text_size))
    Text(stringResource(R.string.feature_about_text_size_explain), style = MaterialTheme.typography.bodySmall)
    Column(Modifier.selectableGroup()) {
        TextScale.entries.forEach { scale ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .selectable(
                        selected = state.textScale == scale,
                        onClick = { onTextScale(scale) },
                        role = Role.RadioButton,
                    ),
            ) {
                RadioButton(selected = state.textScale == scale, onClick = null)
                Text(
                    stringResource(
                        when (scale) {
                            TextScale.SYSTEM -> R.string.feature_about_text_size_system
                            TextScale.LARGE -> R.string.feature_about_text_size_large
                            TextScale.LARGEST -> R.string.feature_about_text_size_largest
                        },
                    ),
                )
            }
        }
    }

    Heading(stringResource(R.string.feature_about_assistant))
    when (val assistant = state.assistant) {
        null -> Unit

        AssistantStatus.NotConfigured -> Text(stringResource(R.string.feature_about_assistant_not_configured))

        is AssistantStatus.Unsupported -> Text(stringResource(R.string.feature_about_assistant_unsupported))

        is AssistantStatus.NotInstalled -> Text(stringResource(R.string.feature_about_assistant_not_installed))

        is AssistantStatus.Downloading, AssistantStatus.Verifying ->
            Text(stringResource(R.string.feature_about_assistant_downloading))

        is AssistantStatus.Failed -> Text(stringResource(R.string.feature_about_assistant_failed))

        is AssistantStatus.Ready -> {
            Text(stringResource(R.string.feature_about_assistant_ready))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .toggleable(
                        value = assistant.enabled,
                        onValueChange = onAssistantEnabled,
                        role = Role.Switch,
                    ),
            ) {
                Switch(checked = assistant.enabled, onCheckedChange = null)
                Text(stringResource(R.string.feature_about_assistant_use))
            }
            TextButton(onClick = onRemoveModel) { Text(stringResource(R.string.feature_about_assistant_remove)) }
        }
    }

    Heading(stringResource(R.string.feature_about_storage))
    Text(
        stringResource(
            R.string.feature_about_storage_data,
            stringResource(R.string.feature_about_size_mb, megabytes(state.storage.dataBytes)),
        ),
    )
    if (state.storage.modelBytes > 0) {
        Text(
            stringResource(
                R.string.feature_about_storage_model,
                stringResource(R.string.feature_about_size_mb, megabytes(state.storage.modelBytes)),
            ),
        )
    }
}

@Composable
internal fun MethodologyPage() {
    Section(R.string.feature_about_m_principles, R.string.feature_about_m_principles_text)
    Section(R.string.feature_about_m_facts, R.string.feature_about_m_facts_text)
    Section(R.string.feature_about_m_coverage, R.string.feature_about_m_coverage_text)
    Section(R.string.feature_about_m_votes, R.string.feature_about_m_votes_text)
    Section(R.string.feature_about_m_programs, R.string.feature_about_m_programs_text)
    Section(R.string.feature_about_m_search, R.string.feature_about_m_search_text)
    Section(R.string.feature_about_m_updates, R.string.feature_about_m_updates_text)
}

@Composable
internal fun SourcesPage() {
    Heading(stringResource(R.string.feature_about_s_data))
    listOf(
        R.string.feature_about_s_constitutional_council,
        R.string.feature_about_s_rne,
        R.string.feature_about_s_an,
        R.string.feature_about_s_senat,
        R.string.feature_about_s_pe,
        R.string.feature_about_s_legi,
        R.string.feature_about_s_vie_publique,
        R.string.feature_about_s_programs,
    ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    Text(stringResource(R.string.feature_about_s_each), style = MaterialTheme.typography.bodyMedium)
    Section(R.string.feature_about_s_software, R.string.feature_about_s_software_text)
}
