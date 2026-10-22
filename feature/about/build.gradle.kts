import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.feature)
}

// Legal notice and error reporting (AGENTS.md §10, §11). To be filled by the publisher before any
// release (docs/publication-checklist.md); empty values are shown as "Non renseigné".
val legal = listOf("publisher", "director", "contact", "hosting").associateWith { key ->
    providers.gradleProperty("citoyen.legal.$key").orElse("")
}
val reportUrl = providers.gradleProperty("citoyen.reportUrl")
    .orElse("https://github.com/thibaultambert-jpg/Citoyen-de-2027/issues")

android {
    namespace = "fr.citoyende2027.feature.about"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        legal.forEach { (key, value) ->
            buildConfigField("String", "LEGAL_${key.uppercase()}", "\"${value.get().replace("\"", "\\\"")}\"")
        }
        buildConfigField("String", "REPORT_URL", "\"${reportUrl.get()}\"")
    }
}

dependencies {
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(projects.core.uiTesting)
    implementation(projects.llm)
}

enableRobolectricTests()
