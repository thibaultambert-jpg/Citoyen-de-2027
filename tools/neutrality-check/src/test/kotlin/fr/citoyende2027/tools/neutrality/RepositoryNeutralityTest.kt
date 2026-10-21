package fr.citoyende2027.tools.neutrality

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fails the build when any user-facing text of the app or any LLM prompt contains a term from
 * config/neutrality/evaluative-terms-fr.txt (AGENTS.md §10).
 */
class RepositoryNeutralityTest {
    private val root = File(checkNotNull(System.getProperty("neutrality.repoRoot")) { "neutrality.repoRoot not set" })
    private val scanner = NeutralityScanner(
        NeutralityScanner.parseTerms(File(root, "config/neutrality/evaluative-terms-fr.txt").readText()),
    )

    @Test
    fun `string resources contain no evaluative term`() {
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.name == "strings.xml" && it.parentFile.name.startsWith("values") }
            .filter { it.invariantSeparatorsPath.contains("/src/main/res/") }
            .toList()
        assertTrue(files.isNotEmpty(), "No strings.xml found under $root")
        val findings = files.flatMap { scanner.scanStringsXml(it, it.relativeTo(root).invariantSeparatorsPath) }
        assertTrue(findings.isEmpty(), report(findings))
    }

    @Test
    fun `llm prompts contain no evaluative term`() {
        val prompts = File(root, "llm/prompts").listFiles { f -> f.extension == "txt" }.orEmpty()
        assertTrue(prompts.isNotEmpty(), "No prompt found under llm/prompts")
        val findings = prompts.flatMap { file ->
            file.readLines().flatMapIndexed { i, line ->
                scanner.scanText(file.relativeTo(root).invariantSeparatorsPath, "line ${i + 1}", line)
            }
        }
        assertTrue(findings.isEmpty(), report(findings))
    }

    private fun report(findings: List<Finding>) = findings.joinToString(
        prefix = "Evaluative terms found:\n",
        separator = "\n",
    ) { "${it.file} [${it.textId}] «${it.term}» in: ${it.text}" }
}
