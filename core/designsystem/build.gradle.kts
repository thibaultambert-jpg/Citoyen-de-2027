plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.android.library.compose)
}

android {
    namespace = "fr.citoyende2027.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
}
