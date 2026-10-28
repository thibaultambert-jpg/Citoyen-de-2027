package fr.citoyende2027.core.model

/**
 * Grouping of published positions for display and filtering. The published value stays stored and
 * traceable ([Vote.position]); this only maps the vocabulary of each assembly to common labels.
 */
enum class VotePosition {
    FOR,
    AGAINST,
    ABSTENTION,
    NON_VOTING,

    /** A published value this version of the app does not know: shown as published. */
    OTHER,
}

val Vote.positionCategory: VotePosition
    get() = when (body to position) {
        Body.AN to "pour", Body.PE to "favor" -> VotePosition.FOR
        Body.AN to "contre", Body.PE to "against" -> VotePosition.AGAINST
        Body.AN to "abstentions", Body.PE to "abstention" -> VotePosition.ABSTENTION
        Body.AN to "nonVotants" -> VotePosition.NON_VOTING
        else -> VotePosition.OTHER
    }
