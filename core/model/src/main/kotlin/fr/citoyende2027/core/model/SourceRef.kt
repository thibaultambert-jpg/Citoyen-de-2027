package fr.citoyende2027.core.model

import java.time.LocalDate

/** Provenance attached to every displayed fact, behind the "Source" button (AGENTS.md §2.3). */
data class SourceRef(
    val name: String,
    val url: String,
    val retrievedOn: LocalDate,
    val license: String,
    val packVersion: String,
) {
    init {
        require(url.startsWith("https://")) { "Sources must use HTTPS: $url" }
    }
}
