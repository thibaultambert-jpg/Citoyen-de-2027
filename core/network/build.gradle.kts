plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.hilt)
}

android {
    namespace = "fr.citoyende2027.core.network"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
