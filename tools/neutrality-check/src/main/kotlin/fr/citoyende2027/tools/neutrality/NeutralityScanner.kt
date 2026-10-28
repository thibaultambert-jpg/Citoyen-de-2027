package fr.citoyende2027.tools.neutrality

import java.io.File
import java.text.Normalizer
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** A forbidden evaluative term found in a user-facing text. */
data class Finding(val file: String, val textId: String, val term: String, val text: String)

/**
 * Scans user-facing texts (Android string resources, LLM prompts) for evaluative wording
 * (AGENTS.md §10). Matching is case- and accent-insensitive, on whole words or phrases.
 */
class NeutralityScanner(terms: List<String>) {
    private val patterns: Map<String, Regex> = terms.associateWith { term ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(normalize(term)) + "(?![\\p{L}\\p{N}])")
    }

    fun scanText(file: String, textId: String, text: String): List<Finding> {
        val normalized = normalize(text)
        return patterns.filterValues { it.containsMatchIn(normalized) }
            .keys
            .map { Finding(file, textId, it, text) }
    }

    fun scanStringsXml(file: File, displayPath: String = file.path): List<Finding> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val findings = mutableListOf<Finding>()
        root.childElements().forEach { element ->
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> findings += scanText(displayPath, name, element.textContent)

                "plurals", "string-array" -> element.childElements().forEachIndexed { i, item ->
                    findings += scanText(displayPath, "$name[$i]", item.textContent)
                }
            }
        }
        return findings
    }

    companion object {
        /** Reads a term list: one term per line, '#' starts a comment. */
        fun parseTerms(content: String): List<String> = content.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()

        internal fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .lowercase()

        private fun Element.childElements(): List<Element> {
            val nodes = childNodes
            return (0 until nodes.length).map(nodes::item).filterIsInstance<Element>()
        }
    }
}
