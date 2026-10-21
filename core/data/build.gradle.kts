import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Where packs are published (GitHub Releases, tag "packs-current") and the Ed25519 public key that
// signs them. An empty key disables network sync: unsigned data is never installed.
val packsBaseUrl = providers.gradleProperty("citoyen.packs.baseUrl")
    .orElse("https://github.com/thibaultambert-jpg/Citoyen-de-2027/releases/download/packs-current/")
val packsPublicKey = providers.gradleProperty("citoyen.packs.publicKey").orElse("")

android {
    namespace = "fr.citoyende2027.core.data"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "PACKS_BASE_URL", "\"${packsBaseUrl.get()}\"")
        buildConfigField("String", "PACKS_PUBLIC_KEY", "\"${packsPublicKey.get()}\"")
    }
}

enableRobolectricTests()

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
    ksp(libs.hilt.ext.compiler)
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
