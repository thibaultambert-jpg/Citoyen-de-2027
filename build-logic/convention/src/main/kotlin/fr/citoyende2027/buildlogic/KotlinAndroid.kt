package fr.citoyende2027.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Shared SDK levels. targetSdk = latest stable platform (API 36); compileSdk follows nowinandroid. */
internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 29
}

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    configureAndroidLanguage(commonExtension)
    configureJUnit5()
}

/** SDK levels and Kotlin, without unit tests: device test modules (com.android.test) have none. */
internal fun Project.configureAndroidLanguage(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = Sdk.COMPILE
        defaultConfig.apply {
            minSdk = Sdk.MIN
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlin<KotlinAndroidProjectExtension>()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin<KotlinJvmProjectExtension>()
    configureJUnit5()
}

/** Unit tests run on the JUnit Platform (JUnit 5) in every module, Android or JVM. */
private fun Project.configureJUnit5() {
    dependencies {
        "testImplementation"(platform(libs.findLibrary("junit.bom").get()))
        "testImplementation"(libs.findLibrary("junit.jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit.platform.launcher").get())
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Full stack traces in the console: CI reports are not always retrievable.
        testLogging {
            events(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true
        }
    }
}

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map {
        it.toBoolean()
    }.orElse(false)
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> TODO("Unsupported project extension $this ${T::class}")
    }.apply {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = warningsAsErrors
        // Annotations on constructor properties (@ApplicationContext, @StringRes…) also go to the
        // field: the future Kotlin default (KT-73255), adopted now to silence its warning.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
