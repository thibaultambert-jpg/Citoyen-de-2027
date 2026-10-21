package fr.citoyende2027.core.model

import fr.citoyende2027.core.testing.FictionalCandidates
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoteFilterTest {
    private val base = FictionalCandidates.votes.first()
    private val an = base.copy(id = "1", position = "pour")
    private val pe = base.copy(
        id = "2",
        body = Body.PE,
        position = "against",
        ballotDate = LocalDate.of(2024, 5, 1),
        ballotSubject = null,
    )

    @Test
    fun `published positions of each assembly map to common categories`() {
        assertEquals(VotePosition.FOR, an.positionCategory)
        assertEquals(VotePosition.AGAINST, pe.positionCategory)
        assertEquals(VotePosition.NON_VOTING, an.copy(position = "nonVotants").positionCategory)
        assertEquals(VotePosition.OTHER, an.copy(position = "inconnu").positionCategory)
        // The same word in another assembly is not assumed to mean the same thing.
        assertEquals(VotePosition.OTHER, pe.copy(position = "pour").positionCategory)
    }

    @Test
    fun `filters combine`() {
        val votes = listOf(an, pe)
        assertEquals(votes, votes.filter(VoteFilter()::matches))
        assertEquals(listOf(pe), votes.filter(VoteFilter(body = Body.PE)::matches))
        assertEquals(listOf(an), votes.filter(VoteFilter(year = 2025, position = VotePosition.FOR)::matches))
        assertEquals(listOf(an), votes.filter(VoteFilter(subject = BallotSubject.AMENDMENT)::matches))
        assertFalse(VoteFilter().isActive)
        assertTrue(VoteFilter(year = 2024).isActive)
    }

    @Test
    fun `options list only values present`() {
        val options = VoteFilterOptions.of(listOf(an, pe))
        assertEquals(listOf(Body.AN, Body.PE), options.bodies)
        assertEquals(listOf(2025, 2024), options.years)
        assertEquals(listOf(VotePosition.FOR, VotePosition.AGAINST), options.positions)
        assertEquals(listOf(BallotSubject.AMENDMENT), options.subjects)
    }
}
