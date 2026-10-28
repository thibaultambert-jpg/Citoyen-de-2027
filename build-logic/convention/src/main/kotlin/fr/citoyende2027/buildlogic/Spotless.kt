package fr.citoyende2027.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** ktlint formatting/lint for Kotlin sources and build scripts of a module. */
internal fun Project.configureSpotless() {
    apply(plugin = "com.diffplug.spotless")
    val ktlintVersion = libs.findVersion("ktlint").get().toString()
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            // Third-party native sources (llama.cpp submodule) are not ours to format.
            targetExclude("src/main/cpp/**")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.kts")
            ktlint(ktlintVersion)
        }
    }
}
