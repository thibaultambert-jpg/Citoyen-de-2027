import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint(Lint::configure) }

                pluginManager.hasPlugin("com.android.library") ->
                    configure<LibraryExtension> { lint(Lint::configure) }

                else -> {
                    apply(plugin = "com.android.lint")
                    configure<Lint>(Lint::configure)
                }
            }
        }
    }
}

private fun Lint.configure() {
    abortOnError = true
    checkDependencies = true
    xmlReport = true
    sarifReport = true
    // Dependency upgrades are a deliberate, reviewed decision (AGENTS.md §3), not a lint failure.
    disable += "GradleDependency"
    disable += "AndroidGradlePluginVersion"
    // All UI text must go through French string resources (AGENTS.md §3).
    error += "HardcodedText"
    error += "SetTextI18n"
}
