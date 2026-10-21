package fr.citoyende2027.core.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Guards AGENTS.md §2.6: fixtures must stay obviously fictional. */
class FictionalCandidatesTest {
    @Test
    fun `fixture names are fictional placeholders`() {
        FictionalCandidates.all.forEach {
            assertTrue(Regex("Candidat [A-Z]").matches(it.displayName), it.displayName)
            assertTrue(it.id.value.startsWith("fictif-"), it.id.value)
        }
    }

    @Test
    fun `fixture urls can never resolve`() {
        val urls = listOf(FictionalCandidates.source.url) + FictionalCandidates.votes.map { it.ballotUrl } +
            FictionalCandidates.programDocuments.map { it.source.url }
        urls.forEach { assertTrue(it.startsWith("https://example.invalid/"), it) }
    }

    @Test
    fun `fixture ids are unique`() {
        assertEquals(FictionalCandidates.all.size, FictionalCandidates.all.map { it.id }.toSet().size)
    }

    @Test
    fun `programme fixtures are fictional`() {
        FictionalCandidates.programChunks.forEach {
            assertTrue(it.text.contains("fictif") || it.text.contains("fictive"), it.text)
        }
        FictionalCandidates.themes.forEach { assertTrue(it.label.contains("fictif") || it.id.isUnknown, it.label) }
    }
}
