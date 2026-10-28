plugins {
    alias(libs.plugins.citoyen.android.application)
    alias(libs.plugins.citoyen.android.application.compose)
    alias(libs.plugins.citoyen.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.citoyende2027"
    // Same NDK as :llm: the app strips the debug symbols of every native library it packages.
    ndkVersion = "29.0.13113456"

    defaultConfig {
        applicationId = "fr.citoyende2027"
        versionCode = 1
        versionName = "0.0.1"
    }

    // Personal use (28/10/2026): the test APK is the one installed. A fixed, non-secret debug key
    // (standard Android debug password) lets each CI build install over the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        // Release code and settings, signed with the local debug key so the device benchmarks
        // (benchmarks/) can install it; never distributed.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // The benchmark build uses the release data seeding (signed embedded pack, no fixtures).
    sourceSets.getByName("benchmark").kotlin.srcDir("src/release/kotlin")
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
