package fr.citoyende2027.feature.ask

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.llm.benchmark.Benchmark
import fr.citoyende2027.llm.benchmark.BenchmarkReport
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val files: List<String> = emptyList(),
    val running: Boolean = false,
    val reports: List<BenchmarkReport> = emptyList(),
)

/** Debug builds only (AGENTS.md §9): time to first token, tokens/s and peak memory, per model file. */
@HiltViewModel
class BenchmarkViewModel @Inject constructor(private val benchmark: Benchmark) : ViewModel() {
    val isAvailable: Boolean get() = benchmark.isAvailable
    private val state =
        MutableStateFlow(BenchmarkUiState(files = if (benchmark.isAvailable) benchmark.files() else emptyList()))
    val uiState: StateFlow<BenchmarkUiState> = state

    fun run(file: String?) {
        if (state.value.running) return
        state.update { it.copy(running = true) }
        viewModelScope.launch {
            val report = benchmark.run(file)
            state.update { it.copy(running = false, reports = it.reports + report) }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            benchmark.importFile(uri, uri.lastPathSegment ?: "model")
            state.update { it.copy(files = benchmark.files()) }
        }
    }
}

@Composable
internal fun BenchmarkSection(viewModel: BenchmarkViewModel = hiltViewModel()) {
    if (!viewModel.isAvailable) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    OutlinedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feature_ask_bench_title))
            OutlinedButton(onClick = { viewModel.run(null) }, enabled = !state.running) {
                Text(stringResource(R.string.feature_ask_bench_run))
            }
            state.files.forEach { file ->
                OutlinedButton(onClick = { viewModel.run(file) }, enabled = !state.running) {
                    Text(stringResource(R.string.feature_ask_bench_run_file, file))
                }
            }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.feature_ask_bench_import))
            }
            if (state.running) Text(stringResource(R.string.feature_ask_bench_running))
            state.reports.forEach { Text(it.describe()) }
        }
    }
}

/** Technical, debug-only text. */
private fun BenchmarkReport.describe(): String = String.format(
    Locale.FRANCE,
    "%s · %s · prompt %d jetons · 1er jeton %d ms · %d jetons à %.1f jetons/s · pic %d Mo · %d threads · %s",
    modelFile,
    outcome,
    promptTokens,
    timeToFirstTokenMs,
    tokens,
    tokensPerSecond,
    peakResidentMb,
    threads,
    cpuFeatures,
)
