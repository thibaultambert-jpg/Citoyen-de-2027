plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.android.library.compose)
}

// Test helpers for Compose screens (accessibility checks, AGENTS.md §11); test dependency only.
android {
    namespace = "fr.citoyende2027.core.uitesting"
}

dependencies {
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.compose.ui)
    api(libs.junit4)
}
