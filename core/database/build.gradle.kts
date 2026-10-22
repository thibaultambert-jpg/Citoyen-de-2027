import fr.citoyende2027.buildlogic.enableRobolectricTests

plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.android.room)
    alias(libs.plugins.citoyen.hilt)
}

android {
    namespace = "fr.citoyende2027.core.database"
}

enableRobolectricTests()

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
