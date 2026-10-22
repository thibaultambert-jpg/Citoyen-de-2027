plugins {
    alias(libs.plugins.citoyen.android.application)
    alias(libs.plugins.citoyen.android.application.compose)
    alias(libs.plugins.citoyen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.citoyende2027"

    defaultConfig {
        // TODO(verify): final applicationId to be chosen by the publisher before any release.
        applicationId = "fr.citoyende2027"
        versionCode = 1
        versionName = "0.0.1"
    }

    packaging {
        jniLibs {
            // ggml loads its CPU variant (dotprod, i8mm…) from nativeLibraryDir: libraries must be
            // extracted at install time, as in the Android example of llama.cpp.
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.feature.about)
    implementation(projects.feature.ask)
    implementation(projects.feature.candidate)
    implementation(projects.feature.candidates)
    implementation(projects.feature.compare)
    implementation(projects.feature.institutions)
    implementation(projects.llm)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Fictional demo data, debug builds only (AGENTS.md §2.6): never shipped in a release APK.
    debugImplementation(projects.core.testing)
}
