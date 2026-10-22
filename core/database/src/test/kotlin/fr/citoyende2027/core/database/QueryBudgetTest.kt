package fr.citoyende2027.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.database.model.themeEntities
import fr.citoyende2027.core.testing.FictionalCandidates
import kotlin.system.measureNanoTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room budget of AGENTS.md §4 (< 50 ms) on the heaviest screen query, a candidate's votes with their
 * themes, for 5 000 fictional votes (a long parliamentary career is well below that).
 */
@RunWith(RobolectricTestRunner::class)
class QueryBudgetTest {
    @Test
    fun candidateVotesLoadWithinBudget() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CitoyenDatabase::class.java,
        ).build()
        val dao = database.corePackDao()
        val template = FictionalCandidates.votes.first { it.candidateId == FictionalCandidates.candidateA.id }
        val votes = List(5_000) { i -> template.copy(id = "fictif-vote-$i", ballotId = "fictif-scrutin-$i") }
        dao.replaceCorePack(
            pack = FictionalCandidates.packInfo.asEntity(),
            candidates = FictionalCandidates.all.map { it.asEntity() },
            coverage = FictionalCandidates.all.flatMap { it.coverageEntities() },
            votes = votes.map { it.asEntity() },
            themes = FictionalCandidates.themes.map { it.asEntity() },
            voteThemes = votes.flatMap { it.themeEntities() },
        )
        val id = FictionalCandidates.candidateA.id.value
        assertEquals(5_000, dao.observeVotes(id).first().size)
        val times = List(9) { measureNanoTime { dao.observeVotes(id).first() } / 1_000_000.0 }.sorted()
        database.close()
        val median = times[times.size / 2]
        println("Room: median %.1f ms to load 5 000 votes with themes".format(median))
        assertTrue("Median $median ms", median < BUDGET_MS)
    }

    private companion object {
        const val BUDGET_MS = 50.0
    }
}
