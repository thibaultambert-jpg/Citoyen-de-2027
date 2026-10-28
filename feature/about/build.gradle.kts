import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.feature)
}

// Error reporting (AGENTS.md §10): opens the repository issues.
val reportUrl = providers.gradleProperty("citoyen.reportUrl")
    .orElse("https://github.com/thibaultambert-jpg/Citoyen-de-2027/issues")

android {
    namespace = "fr.citoyende2027.feature.about"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "REPORT_URL", "\"${reportUrl.get()}\"")
    }
}

dependencies {
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(projects.core.uiTesting)
    implementation(projects.llm)
}

enableRobolectricTests()
