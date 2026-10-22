plugins {
    alias(libs.plugins.citoyen.jvm.library)
}

dependencies {
    api(projects.core.model)

    // Test-only helpers: provided by the consuming test classpath, never shipped (debug builds
    // use this module for its fictional fixtures only).
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter.api)
    compileOnly(libs.kotlinx.coroutines.test)
}
