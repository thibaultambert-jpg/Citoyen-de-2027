plugins {
    alias(libs.plugins.citoyen.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Evaluation harness of the retrieval chain (AGENTS.md §7.7): runs :rag on the signed packs and
// pipeline/eval/golden.jsonl. Build and test tool only, never shipped.
dependencies {
    implementation(projects.rag)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)
}

/**
 * ./gradlew :tools:rag-eval:evaluate -Ppacks=<dir> [-Pgolden=<file>] [-Pthresholds=<file>]
 *   [-PragConfig=<file>] [-Pcalibrate=true] [-Preport=<file>]
 */
tasks.register<JavaExec>("evaluate") {
    group = "verification"
    description = "Evaluates the retrieval chain on the golden set (fails below the thresholds)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("fr.citoyende2027.tools.rageval.MainKt")
    val root = rootDir.absolutePath
    val options = listOf("packs", "golden", "thresholds", "ragConfig", "calibrate", "report")
    args = options.mapNotNull { name -> providers.gradleProperty(name).orNull?.let { "--$name=$it" } } +
        listOf("--root=$root")
}

tasks.test {
    // The harness test reads the fictional packs signed by the pipeline (cross-language contract).
    val packs = rootDir.resolve("core/data/src/test/resources/packs")
    systemProperty("ragEval.packs", packs.absolutePath)
    inputs.dir(packs).withPropertyName("packs")
}
