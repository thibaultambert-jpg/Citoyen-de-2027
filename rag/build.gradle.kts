plugins {
    alias(libs.plugins.citoyen.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Retrieval engine (AGENTS.md §8) in plain Kotlin: the app and the evaluation harness
// (:tools:rag-eval) run exactly the same code.
dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
