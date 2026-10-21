package fr.citoyende2027.core.model

/** Filters of the Votes tab (AGENTS.md §11). Null means "all". */
data class VoteFilter(
    val body: Body? = null,
    val year: Int? = null,
    val subject: BallotSubject? = null,
    val position: VotePosition? = null,
) {
    fun matches(vote: Vote): Boolean = (body == null || vote.body == body) &&
        (year == null || vote.ballotDate.year == year) &&
        (subject == null || vote.ballotSubject == subject) &&
        (position == null || vote.positionCategory == position)

    val isActive: Boolean get() = this != VoteFilter()
}

/** Values actually present in a candidate's votes, so that no filter leads to an empty choice. */
data class VoteFilterOptions(
    val bodies: List<Body>,
    val years: List<Int>,
    val subjects: List<BallotSubject>,
    val positions: List<VotePosition>,
) {
    companion object {
        fun of(votes: List<Vote>) = VoteFilterOptions(
            bodies = votes.map { it.body }.distinct().sorted(),
            years = votes.map { it.ballotDate.year }.distinct().sortedDescending(),
            subjects = votes.mapNotNull { it.ballotSubject }.distinct().sorted(),
            positions = votes.map { it.positionCategory }.distinct().sorted(),
        )
    }
}
