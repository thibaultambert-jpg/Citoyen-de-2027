package fr.citoyende2027.feature.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.llm.assistant.AssistantStatus
import fr.citoyende2027.llm.device.Limitation
import fr.citoyende2027.rag.answer.AnswerSource
import fr.citoyende2027.rag.answer.GroundedAnswer
import java.util.Locale

/** Actions on the assistant, from the "Question" screen. */
internal class AssistantActions(
    val onDownload: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onImport: () -> Unit,
    val onRemove: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onStop: () -> Unit,
    val onExtractsOnly: () -> Unit,
)

/** "≈ 2,1 Go" (decimal gigabytes, French format). */
@Composable
private fun size(bytes: Long): String =
    stringResource(R.string.feature_ask_ai_size, String.format(Locale.FRANCE, "%.1f", bytes / 1e9))

/** State of the assistant; without it the screen says it works in "Mode sans IA" (AGENTS.md §9). */
@Composable
internal fun AssistantCard(status: AssistantStatus?, importAccepted: Boolean?, actions: AssistantActions) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ready = status is AssistantStatus.Ready && status.enabled
            if (!ready) {
                Text(
                    stringResource(R.string.feature_ask_no_ai_mode),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (status) {
                null -> Unit

                AssistantStatus.NotConfigured -> Text(stringResource(R.string.feature_ask_ai_not_configured))

                is AssistantStatus.Unsupported -> status.limitations.sorted().forEach {
                    Text(
                        stringResource(
                            when (it) {
                                Limitation.MEMORY -> R.string.feature_ask_ai_unsupported_memory
                                Limitation.STORAGE -> R.string.feature_ask_ai_unsupported_storage
                            },
                        ),
                    )
                }

                is AssistantStatus.NotInstalled -> {
                    Text(
                        stringResource(R.string.feature_ask_ai_activate_explain),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = actions.onDownload) {
                        Text(stringResource(R.string.feature_ask_ai_activate, size(status.sizeBytes)))
                    }
                    TextButton(onClick = actions.onImport) { Text(stringResource(R.string.feature_ask_ai_import)) }
                }

                is AssistantStatus.Downloading -> {
                    if (status.waitingForWifi && status.downloadedBytes == 0L) {
                        Text(stringResource(R.string.feature_ask_ai_waiting_wifi))
                    } else {
                        val percent = (status.downloadedBytes * 100 / status.totalBytes.coerceAtLeast(1)).toInt()
                        Text(
                            stringResource(
                                R.string.feature_ask_ai_downloading,
                                percent,
                                size(status.downloadedBytes),
                                size(status.totalBytes),
                            ),
                        )
                        if (status.waitingForWifi) Text(stringResource(R.string.feature_ask_ai_waiting_wifi))
                    }
                    LinearProgressIndicator(
                        progress = { status.downloadedBytes.toFloat() / status.totalBytes.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = actions.onCancelDownload) {
                        Text(stringResource(R.string.feature_ask_ai_cancel))
                    }
                }

                AssistantStatus.Verifying -> {
                    Text(stringResource(R.string.feature_ask_ai_verifying))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is AssistantStatus.Failed -> {
                    Text(stringResource(R.string.feature_ask_ai_failed))
                    Button(onClick = actions.onDownload) { Text(stringResource(R.string.feature_ask_ai_retry)) }
                    TextButton(onClick = actions.onImport) { Text(stringResource(R.string.feature_ask_ai_import)) }
                }

                is AssistantStatus.Ready -> {
                    Text(stringResource(R.string.feature_ask_ai_ready))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = status.enabled, onCheckedChange = actions.onEnabledChange)
                        Text(stringResource(R.string.feature_ask_ai_use))
                    }
                    TextButton(onClick = actions.onRemove) { Text(stringResource(R.string.feature_ask_ai_remove)) }
                }
            }
            if (importAccepted == false) Text(stringResource(R.string.feature_ask_ai_import_refused))
        }
    }
}

/**
 * The generated part of an answer: rebuilt from verified claims only, each with tappable citations,
 * the fixed AI notice, and the way back to the passages alone (AGENTS.md §8.9).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiAnswerSection(ai: AiAnswer, actions: AssistantActions) {
    when (ai) {
        AiAnswer.None -> Unit

        AiAnswer.NotForSideBySide -> Text(
            stringResource(R.string.feature_ask_ai_side_by_side),
            style = MaterialTheme.typography.bodySmall,
        )

        is AiAnswer.Generating -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feature_ask_ai_generating))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onStop) { Text(stringResource(R.string.feature_ask_ai_stop)) }
                TextButton(onClick = actions.onExtractsOnly) {
                    Text(stringResource(R.string.feature_ask_ai_extracts_only))
                }
            }
        }

        is AiAnswer.Done -> when (val answer = ai.answer) {
            is GroundedAnswer.Answered -> OutlinedCard {
                var opened by remember { mutableStateOf<AnswerSource?>(null) }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feature_ask_ai_label), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.feature_ask_ai_banner), style = MaterialTheme.typography.bodySmall)
                    answer.claims.forEach { claim ->
                        Text(claim.text, style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            claim.sources.forEach { source ->
                                val description = stringResource(R.string.feature_ask_ai_source, source.label)
                                AssistChip(
                                    onClick = { opened = source },
                                    label = { Text(source.label) },
                                    modifier = Modifier.semantics { contentDescription = description },
                                )
                            }
                        }
                    }
                    TextButton(onClick = actions.onExtractsOnly) {
                        Text(stringResource(R.string.feature_ask_ai_extracts_only))
                    }
                }
                opened?.let { SourceDialog(it, onDismiss = { opened = null }) }
            }

            is GroundedAnswer.Abstained -> Text(stringResource(R.string.feature_ask_ai_abstained))

            GroundedAnswer.Stopped -> Text(stringResource(R.string.feature_ask_ai_stopped))

            is GroundedAnswer.Failed -> Text(
                stringResource(
                    if (answer.reason == THERMAL) R.string.feature_ask_ai_thermal else R.string.feature_ask_ai_error,
                ),
            )
        }
    }
}

/** The passage exactly as the model saw it, with its document. */
@Composable
private fun SourceDialog(source: AnswerSource, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_ask_ai_close)) } },
        title = { Text("${source.label} · ${source.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.text)
                if (source.truncated) {
                    Text(stringResource(R.string.feature_ask_ai_source_cut), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

/** Failure reason sent by the model process when the device is too hot (LlmProtocol.ERROR_THERMAL). */
private const val THERMAL = "thermal"
