import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.feature)
}

android {
    namespace = "fr.citoyende2027.feature.candidate"
}

enableRobolectricTests()

dependencies {
    testImplementation(projects.core.uiTesting)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
