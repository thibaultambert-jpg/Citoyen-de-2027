package fr.citoyende2027.core.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeTest {
    private val source = SourceRef("Source fictive", "https://example.invalid/s", LocalDate.of(2026, 1, 1), "L", "v")
    private val candidate = CandidateId("fictif-a")
    private val zeta = Theme(ThemeId("fictif-z"), "Zêta fictif")
    private val eco = Theme(ThemeId("fictif-e"), "Économie fictive")
    private val alpha = Theme(ThemeId("fictif-a"), "alpha fictif")
    private val unknown = Theme(ThemeId(ThemeId.UNKNOWN), "Thème non déterminé")

    private fun tag(theme: Theme) = ThemeTag(theme.id, TagMethod.RULE, TagConfidence.LOW)

    private fun vote(id: String, date: LocalDate, vararg themes: Theme) = Vote(
        id = id,
        candidateId = candidate,
        body = Body.AN,
        ballotId = id,
        ballotDate = date,
        ballotTitle = "Scrutin fictif",
        ballotType = null,
        ballotSubject = null,
        position = "pour",
        ballotUrl = "https://example.invalid/$id",
        source = source,
        themes = themes.map(::tag),
    )

    private fun chunk(ordinal: Int, vararg themes: Theme) = ProgramChunk(
        id = "doc-$ordinal",
        documentId = "doc",
        candidateId = candidate,
        ordinal = ordinal,
        headingPath = emptyList(),
        page = ordinal,
        anchor = null,
        text = "Texte fictif $ordinal",
        themes = themes.map(::tag),
    )

    @Test
    fun `themes are alphabetical with accents ignored and the undetermined theme last`() {
        assertEquals(listOf(alpha, eco, zeta, unknown), listOf(unknown, zeta, eco, alpha).alphabetical())
    }

    @Test
    fun `side by side keeps only the facts of the theme, votes most recent first`() {
        val program =
            CandidateProgram(candidate, ProgramStatus.COMPLET, emptyList(), listOf(chunk(1, eco), chunk(2, zeta)))
        val votes = listOf(
            vote("v1", LocalDate.of(2024, 1, 1), eco),
            vote("v2", LocalDate.of(2025, 1, 1), eco, zeta),
            vote("v3", LocalDate.of(2025, 6, 1), alpha),
        )
        val facts = ThemeFacts.of(eco, program, votes)
        assertEquals(listOf("doc-1"), facts.excerpts.map { it.id })
        assertEquals(listOf("v2", "v1"), facts.votes.map { it.id })
        assertEquals(
            listOf(alpha, eco, zeta),
            themesWithFacts(listOf(zeta, unknown, eco, alpha), listOf(program), votes),
        )
    }

    @Test
    fun `votes can be filtered by theme`() {
        val votes = listOf(vote("v1", LocalDate.of(2024, 1, 1), eco), vote("v2", LocalDate.of(2025, 1, 1), zeta))
        assertEquals(listOf("v2"), votes.filter(VoteFilter(theme = zeta.id)::matches).map { it.id })
        assertEquals(listOf(eco.id, zeta.id), VoteFilterOptions.of(votes).themes)
    }
}
