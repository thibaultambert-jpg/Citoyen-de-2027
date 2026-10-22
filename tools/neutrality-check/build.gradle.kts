plugins {
    alias(libs.plugins.citoyen.jvm.library)
}

// The repository scan reads every UI string resource and LLM prompt of the project.
val repoRoot: String = rootDir.absolutePath
tasks.test {
    systemProperty("neutrality.repoRoot", repoRoot)
    inputs.files(
        fileTree(rootDir) {
            include("**/src/main/res/values*/strings.xml", "llm/prompts/**", "config/neutrality/**")
            exclude("**/build/**")
        },
    ).withPropertyName("scannedTexts")
}
