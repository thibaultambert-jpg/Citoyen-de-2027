package fr.citoyende2027.feature.ask

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.data.repository.AnswerGroup
import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.LegalPassage
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.ui.DropdownFilterChip
import fr.citoyende2027.core.ui.MandateItem
import fr.citoyende2027.core.ui.ProgramExcerptCard
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.VoteItem
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.rag.RefusalCategory
import fr.citoyende2027.rag.answer.GroundedAnswer

@Composable
internal fun AskScreen(modifier: Modifier = Modifier, viewModel: AskViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The model file can also be copied from the device (Storage Access Framework), then verified.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importModel)
    }
    AskContent(
        state = state,
        onScopeChange = viewModel::onScopeChange,
        onCandidateChange = viewModel::onCandidateChange,
        onQuestionChange = viewModel::onQuestionChange,
        onSubmit = viewModel::submit,
        actions = AssistantActions(
            onDownload = viewModel::downloadModel,
            onCancelDownload = viewModel::cancelDownload,
            onImport = { modelPicker.launch(arrayOf("*/*")) },
            onRemove = viewModel::removeModel,
            onEnabledChange = viewModel::setAssistantEnabled,
            onStop = viewModel::stopGeneration,
            onExtractsOnly = viewModel::showExtractsOnly,
        ),
        modifier = modifier,
        footer = { BenchmarkSection() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskContent(
    state: AskUiState,
    onScopeChange: (ScopeKind) -> Unit,
    onCandidateChange: (Candidate) -> Unit,
    onQuestionChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    actions: AssistantActions,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_ask_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        AssistantCard(state.assistant, state.importAccepted, actions)
        Text(stringResource(R.string.feature_ask_scope), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopeKind.entries.forEach { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { onScopeChange(scope) },
                    label = { Text(stringResource(scope.label)) },
                )
            }
        }
        if (state.scope == ScopeKind.CANDIDATE) {
            DropdownFilterChip(
                title = stringResource(R.string.feature_ask_candidate),
                selected = state.candidate,
                options = state.candidates,
                label = { it.displayName },
                onSelect = { it?.let(onCandidateChange) },
                allLabel = null,
            )
        }
        OutlinedTextField(
            value = state.question,
            onValueChange = onQuestionChange,
            label = { Text(stringResource(R.string.feature_ask_question)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (state.canSubmit) onSubmit(state.question) }),
            // Found by the device benchmarks (benchmarks/, resource id "ask_question").
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ask_question"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSubmit(state.question) }, enabled = state.canSubmit) {
                Text(stringResource(R.string.feature_ask_submit))
            }
            Text(stringResource(R.string.feature_ask_privacy), style = MaterialTheme.typography.bodySmall)
        }
        if (state.answer == null && !state.searching) Suggestions(state, onQuestionChange, onSubmit)
        if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.answer?.let { answer ->
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.extractsOnly) AiAnswerSection(state.ai, actions)
                Answer(answer, sourceLabels = state.sourceLabels())
            }
        }
        footer()
    }
}

/** Passage id → label (S1…) of the sources given to the assistant, shown on the passage cards. */
private fun AskUiState.sourceLabels(): Map<String, String> {
    if (extractsOnly) return emptyMap()
    val answered = (ai as? AiAnswer.Done)?.answer as? GroundedAnswer.Answered ?: return emptyMap()
    return answered.sources.associate { it.passageId to it.label }
}

private val ScopeKind.label: Int
    get() = when (this) {
        ScopeKind.LEGAL -> R.string.feature_ask_scope_legal
        ScopeKind.CANDIDATE -> R.string.feature_ask_scope_candidate
        ScopeKind.PROGRAMS -> R.string.feature_ask_scope_programs
    }

/** Neutral example questions, the same whatever the candidate (AGENTS.md §11). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(state: AskUiState, onQuestionChange: (String) -> Unit, onSubmit: (String) -> Unit) {
    val suggestions = if (state.scope == ScopeKind.LEGAL) {
        listOf(
            R.string.feature_ask_suggestion_legal_1,
            R.string.feature_ask_suggestion_legal_2,
            R.string.feature_ask_suggestion_legal_3,
        )
    } else {
        listOf(
            R.string.feature_ask_suggestion_programs_1,
            R.string.feature_ask_suggestion_programs_2,
            R.string.feature_ask_suggestion_programs_3,
        )
    }.map { stringResource(it) }
    Text(stringResource(R.string.feature_ask_suggestions), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = {
                    onQuestionChange(suggestion)
                    if (state.scope != ScopeKind.CANDIDATE || state.candidate != null) onSubmit(suggestion)
                },
                label = { Text(suggestion) },
            )
        }
    }
}

@Composable
private fun Answer(answer: AskAnswer, sourceLabels: Map<String, String>) {
    when (answer) {
        AskAnswer.Unavailable -> Text(stringResource(R.string.feature_ask_unavailable))

        is AskAnswer.Refused -> Card {
            Text(
                text = stringResource(
                    if (answer.category == RefusalCategory.INSTRUCTION) {
                        R.string.feature_ask_refused_instruction
                    } else {
                        R.string.feature_ask_refused
                    },
                ),
                modifier = Modifier.padding(12.dp),
            )
        }

        is AskAnswer.Abstained -> Text(
            answer.dataDate?.let { stringResource(R.string.feature_ask_abstained, it.toFrenchDate()) }
                ?: stringResource(R.string.feature_ask_abstained_no_date),
        )

        is AskAnswer.Mandates -> {
            Heading(stringResource(R.string.feature_ask_mandates, answer.candidate.displayName))
            if (answer.mandates.isEmpty()) Text(stringResource(R.string.feature_ask_none_recorded))
            answer.mandates.forEach { MandateItem(it) }
        }

        is AskAnswer.Votes -> {
            Heading(stringResource(R.string.feature_ask_votes, answer.candidate.displayName))
            if (answer.votes.isEmpty()) Text(stringResource(R.string.feature_ask_none_recorded))
            answer.votes.forEach { vote ->
                VoteItem(vote)
                HorizontalDivider()
            }
        }

        is AskAnswer.Passages -> {
            Text(stringResource(R.string.feature_ask_passages_notice), style = MaterialTheme.typography.bodyMedium)
            answer.groups.forEach { Group(it, sourceLabels) }
        }
    }
}

/** Same template for every group; a candidate without a relevant passage keeps its place. */
@Composable
private fun Group(group: AnswerGroup, sourceLabels: Map<String, String>) {
    group.candidate?.let { Heading(it.displayName) }
    if (group.isEmpty) Text(stringResource(R.string.feature_ask_group_empty))
    group.legal.forEach { LegalPassageCard(it, sourceLabels[it.article.id]) }
    group.programs.forEach { passage ->
        sourceLabels[passage.chunk.id]?.let { SourceLabel(it) }
        ProgramExcerptCard(passage.chunk, passage.document, themeLabels = emptyMap())
    }
}

/** "Source S1": links a citation of the generated answer to the passage card below. */
@Composable
private fun SourceLabel(label: String) {
    Text(stringResource(R.string.feature_ask_ai_source, label), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun LegalPassageCard(passage: LegalPassage, sourceLabel: String?) {
    val uriHandler = LocalUriHandler.current
    val article = passage.article
    OutlinedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourceLabel?.let { SourceLabel(it) }
            Text(passage.text.title, style = MaterialTheme.typography.labelLarge)
            if (article.headingPath.isNotEmpty()) {
                Text(article.headingPath.joinToString(" › "), style = MaterialTheme.typography.labelMedium)
            }
            if (article.number.any(Char::isDigit)) {
                Text(
                    stringResource(R.string.feature_ask_article, article.number),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else if (article.number.isNotBlank()) {
                Text(article.number, style = MaterialTheme.typography.titleSmall)
            }
            Text(article.text, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    uriHandler.openUri(article.url)
                }) { Text(stringResource(R.string.feature_ask_official)) }
                SourceButton(passage.text.source)
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
