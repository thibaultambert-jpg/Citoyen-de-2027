import com.android.build.api.dsl.TestExtension
import fr.citoyende2027.buildlogic.Sdk
import fr.citoyende2027.buildlogic.configureAndroidLanguage
import fr.citoyende2027.buildlogic.configureSpotless
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** Device test modules (com.android.test), such as the macrobenchmarks of the app. */
abstract class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.test")

            extensions.configure<TestExtension> {
                configureAndroidLanguage(this)
                defaultConfig.targetSdk = Sdk.TARGET
            }
            configureSpotless()
        }
    }
}
