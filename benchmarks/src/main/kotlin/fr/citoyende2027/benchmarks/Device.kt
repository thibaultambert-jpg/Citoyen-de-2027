package fr.citoyende2027.benchmarks

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue

/** Package of the measured app (applicationId of :app). */
const val PACKAGE_NAME = "fr.citoyende2027"

/** Iterations per measure: enough for a stable median on a phone, short enough to run by hand. */
const val ITERATIONS = 10

private const val TIMEOUT_MS = 10_000L

// Test tags of the app, exposed as resource ids (CitoyenApp: testTagsAsResourceId).
val CANDIDATE_ROW: BySelector = By.res("candidate_row")
val ASK_QUESTION: BySelector = By.res("ask_question")

fun MacrobenchmarkScope.waitFor(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), TIMEOUT_MS)

/** Skips the measure when the installed data has no candidate (empty pack): nothing to measure. */
fun MacrobenchmarkScope.firstCandidate(): UiObject2 {
    val row = waitFor(CANDIDATE_ROW)
    assumeTrue("No candidate in the installed data pack", row != null)
    return row!!
}

/** Opens a top-level destination by its visible label (navigation bar or rail). */
fun MacrobenchmarkScope.openTab(label: String) {
    checkNotNull(waitFor(By.text(label))) { "Navigation item '$label' not found" }.click()
    device.waitForIdle()
}
