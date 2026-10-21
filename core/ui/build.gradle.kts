plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.android.library.compose)
}

android {
    namespace = "fr.citoyende2027.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)

    testImplementation(projects.core.testing)
}
