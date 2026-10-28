package fr.citoyende2027.core.model

import fr.citoyende2027.core.testing.FictionalCandidates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CandidateOrderTest {
    private val candidates = FictionalCandidates.all

    @Test
    fun `default order is alphabetical`() {
        assertEquals(CandidateOrder.Alphabetical, CandidateOrder.Default)
        val ordered = candidates.reversed().orderedBy(CandidateOrder.Default)
        assertEquals(listOf("Candidat A", "Candidat B", "Candidat C"), ordered.map { it.displayName })
    }

    @Test
    fun `alphabetical order ignores accents and case`() {
        val base = candidates.first()
        val names = listOf("Émile", "eric", "Denis", "Zoé")
        val shuffled = names.mapIndexed { i, n -> base.copy(id = CandidateId("x$i"), sortName = n) }
        assertEquals(
            listOf("Denis", "Émile", "eric", "Zoé"),
            shuffled.orderedBy(CandidateOrder.Alphabetical).map {
                it.sortName
            },
        )
    }

    @Test
    fun `shuffled order is a permutation and is stable for a given seed`() {
        val first = candidates.orderedBy(CandidateOrder.Shuffled(seed = 42))
        val second = candidates.reversed().orderedBy(CandidateOrder.Shuffled(seed = 42))
        assertEquals(first, second)
        assertEquals(candidates.toSet(), first.toSet())
    }
}
