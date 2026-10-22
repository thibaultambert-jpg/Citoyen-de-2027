plugins {
    alias(libs.plugins.citoyen.android.feature)
}

android {
    namespace = "fr.citoyende2027.feature.ask"
}

dependencies {
    implementation(projects.llm)
    implementation(libs.androidx.activity.compose)
}
