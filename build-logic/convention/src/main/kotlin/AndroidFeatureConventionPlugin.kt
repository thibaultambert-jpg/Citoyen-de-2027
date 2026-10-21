import fr.citoyende2027.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Compose UI + Hilt + type-safe Navigation Compose routes. */
abstract class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "citoyen.android.library")
            apply(plugin = "citoyen.android.library.compose")
            apply(plugin = "citoyen.hilt")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:model"))
                "implementation"(project(":core:designsystem"))

                "implementation"(libs.findLibrary("androidx.compose.material3").get())
                "implementation"(libs.findLibrary("androidx.hilt.navigation.compose").get())
                "implementation"(libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
                "implementation"(libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx.navigation.compose").get())
                "implementation"(libs.findLibrary("kotlinx.serialization.json").get())

                "testImplementation"(libs.findLibrary("kotlinx.coroutines.test").get())
                "testImplementation"(libs.findLibrary("turbine").get())
            }
        }
    }
}
