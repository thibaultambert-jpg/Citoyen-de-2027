plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.hilt)
}

android {
    namespace = "fr.citoyende2027.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(libs.androidx.dataStore.preferences)
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
