plugins {
    alias(libs.plugins.citoyen.jvm.library)
}

dependencies {
    testImplementation(projects.core.testing)
}
