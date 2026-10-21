import com.android.build.api.dsl.LibraryExtension
import fr.citoyende2027.buildlogic.Sdk
import fr.citoyende2027.buildlogic.configureKotlinAndroid
import fr.citoyende2027.buildlogic.configureSpotless
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "citoyen.android.lint")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = Sdk.TARGET
                lint.targetSdk = Sdk.TARGET
                testOptions.animationsDisabled = true
                // Resources of ":core:designsystem" must be prefixed with "core_designsystem_".
                resourcePrefix =
                    path.split("""\W""".toRegex()).drop(1).distinct().joinToString(separator = "_")
                        .lowercase() + "_"
            }
            configureSpotless()
        }
    }
}
