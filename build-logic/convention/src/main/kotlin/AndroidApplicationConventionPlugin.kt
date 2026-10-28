import com.android.build.api.dsl.ApplicationExtension
import fr.citoyende2027.buildlogic.Sdk
import fr.citoyende2027.buildlogic.configureKotlinAndroid
import fr.citoyende2027.buildlogic.configureSpotless
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "citoyen.android.lint")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = Sdk.TARGET
                // AGENTS.md §4: arm64-v8a only.
                defaultConfig.ndk.abiFilters += "arm64-v8a"
                testOptions.animationsDisabled = true
            }
            configureSpotless()
        }
    }
}
