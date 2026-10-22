plugins {
    alias(libs.plugins.citoyen.android.test)
}

// Device benchmarks of the AGENTS.md §4 budgets, run by a human on a real phone (they need a
// device, so CI only compiles them): ./gradlew :benchmarks:connectedBenchmarkAndroidTest
android {
    namespace = "fr.citoyende2027.benchmarks"
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Same name as the app build type it measures (release code, debug key).
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

// Only the benchmark variant is meaningful: the app's debug build is not representative.
androidComponents {
    beforeVariants { variant -> variant.enable = variant.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
