package fr.citoyende2027.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * JVM tests needing the Android framework (Room, Compose UI) run on Robolectric. Robolectric only
 * provides a JUnit 4 runner, executed on the JUnit Platform through the vintage engine.
 */
fun Project.enableRobolectricTests() {
    extensions.configure<LibraryExtension> {
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    tasks.withType<Test>().configureEach {
        // Robolectric on SDK 36 reaches jdk.internal.access (ApplicationSharedMemory), which JDK 17+
        // does not export by default.
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    }
    dependencies {
        "testImplementation"(libs.findLibrary("robolectric").get())
        "testImplementation"(libs.findLibrary("junit4").get())
        "testImplementation"(libs.findLibrary("androidx.test.core").get())
        "testRuntimeOnly"(libs.findLibrary("junit.vintage.engine").get())
    }
}
