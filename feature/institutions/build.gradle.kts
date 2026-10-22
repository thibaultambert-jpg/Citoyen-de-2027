import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.feature)
}

android {
    namespace = "fr.citoyende2027.feature.institutions"
}

enableRobolectricTests()

dependencies {
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(projects.core.uiTesting)
}
