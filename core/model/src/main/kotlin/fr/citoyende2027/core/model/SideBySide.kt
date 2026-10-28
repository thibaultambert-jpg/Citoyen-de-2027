package fr.citoyende2027.core.model

/**
 * Programme excerpts and recorded votes of one candidate on one theme, placed side by side
 * (AGENTS.md §10). Facts only: nothing here compares, scores or qualifies them.
 */
data class ThemeFacts(
    val candidateId: CandidateId,
    val theme: Theme,
    val programStatus: ProgramStatus,
    /** Excerpts in document order. */
    val excerpts: List<ProgramChunk>,
    /** Votes, most recent first. */
    val votes: List<Vote>,
) {
    companion object {
        fun of(theme: Theme, program: CandidateProgram, votes: List<Vote>) = ThemeFacts(
            candidateId = program.candidateId,
            theme = theme,
            programStatus = program.status,
            excerpts = program.chunks.filter { it.hasTheme(theme.id) },
            votes = votes.filter { it.hasTheme(theme.id) }.sortedByDescending { it.ballotDate },
        )
    }
}

/** Themes with at least one excerpt or vote among [programs] and [votes], alphabetical. */
fun themesWithFacts(themes: List<Theme>, programs: List<CandidateProgram>, votes: List<Vote>): List<Theme> {
    val used = programs.flatMap { program -> program.chunks.flatMap { chunk -> chunk.themes.map { it.themeId } } }
        .plus(votes.flatMap { vote -> vote.themes.map { it.themeId } })
        .toSet()
    return themes.filter { it.id in used }.alphabetical()
}
