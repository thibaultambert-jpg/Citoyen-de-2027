package fr.citoyende2027.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Budgets of AGENTS.md §4 measured in the app itself, through its trace sections (PerfTrace in
 * :core:data): a candidate's votes from Room (< 50 ms) and a question's routing and retrieval
 * (< 300 ms). Frame timings show whether the screens stay fluid.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun candidateSheetVotes() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            TraceSectionMetric(VOTES, TraceSectionMetric.Mode.First),
            FrameTimingMetric(),
        ),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Full(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        firstCandidate().click()
        checkNotNull(waitFor(By.text(VOTES_TAB))) { "Tab '$VOTES_TAB' not found" }.click()
        device.waitForIdle()
    }

    @Test
    fun searchInSources() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(TraceSectionMetric(ASK, TraceSectionMetric.Mode.First)),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Full(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openTab(ASK_TAB)
        },
    ) {
        val field = checkNotNull(waitFor(ASK_QUESTION)) { "Question field not found" }
        // A neutral, generic question: the measure is the retrieval, not its content.
        field.text = QUESTION
        checkNotNull(waitFor(By.text(SUBMIT))) { "Button '$SUBMIT' not found" }.click()
        device.waitForIdle()
    }

    private companion object {
        // Trace section names (PerfTrace in :core:data).
        const val VOTES = "citoyen.votes"
        const val ASK = "citoyen.ask"

        // Visible French labels of the app (strings.xml of :app, :feature:candidate, :feature:ask).
        const val VOTES_TAB = "Votes"
        const val ASK_TAB = "Question"
        const val SUBMIT = "Rechercher"
        const val QUESTION = "Comment le Président de la République est-il élu ?"
    }
}
