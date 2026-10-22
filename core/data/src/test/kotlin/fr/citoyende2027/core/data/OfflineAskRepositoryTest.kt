package fr.citoyende2027.core.data

import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.AskScope
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.data.repository.OfflineAskRepository
import fr.citoyende2027.core.model.ReservePeriod
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.FictionalLegal
import fr.citoyende2027.rag.RefusalCategory
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class OfflineAskRepositoryTest {
    private val dao = FakeCorePackDao()
    private val repository = OfflineAskRepository(dao)

    private suspend fun install() = CorePackStore(dao).install(
        FictionalCandidates.corePack,
        FictionalCandidates.programsPack,
        FictionalLegal.pack,
    )

    @Test
    fun `no installed data means unavailable`() = runTest {
        assertEquals(AskAnswer.Unavailable, repository.ask("Quelle règle ?", AskScope.Legal))
    }

    @Test
    fun `legal question returns verbatim articles with their text`() = runTest {
        install()
        val answer = assertInstanceOf(
            AskAnswer.Passages::class.java,
            repository.ask("Selon quelle règle a lieu l'élection fictive ?", AskScope.Legal),
        )
        val first = answer.groups.single().legal.first()
        assertEquals("fictif-texte-a-art-2", first.article.id)
        assertEquals(FictionalLegal.textA, first.text)
    }

    @Test
    fun `gates, routing and abstention`() = runTest {
        install()
        assertEquals(AskAnswer.Refused(RefusalCategory.VOTE_ADVICE), repository.ask("Pour qui voter ?", AskScope.Legal))
        assertEquals(
            AskAnswer.Mandates(FictionalCandidates.candidateA, FictionalCandidates.mandates),
            repository.ask("Quels mandats pour Candidat A ?", AskScope.Legal),
        )
        assertEquals(
            AskAnswer.Abstained(FictionalLegal.info.generatedOn),
            repository.ask("Quel est le budget des armées ?", AskScope.Legal),
        )
    }

    @Test
    fun `programmes side by side keep every candidate, alphabetical, same template`() = runTest {
        install()
        val answer = assertInstanceOf(
            AskAnswer.Passages::class.java,
            repository.ask("Une mesure sur le motfictif02 ?", AskScope.AllPrograms),
        )
        assertEquals(FictionalCandidates.all, answer.groups.map { it.candidate })
        assertEquals(listOf(true, false, true), answer.groups.map { it.isEmpty })
        assertEquals("fictif-b-programme-0001", answer.groups[1].programs.single().chunk.id)
    }

    @Test
    fun `during the reserve period programme passages are not shown, official records are`() = runTest {
        CorePackStore(dao).install(
            FictionalCandidates.corePack.copy(reserve = ReservePeriod(true, LocalDate.of(2026, 1, 20))),
            FictionalCandidates.programsPack,
            FictionalLegal.pack,
        )
        val reserved = AskAnswer.Reserved(ReservePeriod(true, LocalDate.of(2026, 1, 20)))
        assertEquals(reserved, repository.ask("Une mesure sur le motfictif02 ?", AskScope.AllPrograms))
        assertEquals(
            reserved,
            repository.ask("Quel est le budget des armées ?", AskScope.Candidate(FictionalCandidates.candidateA.id)),
        )
        assertInstanceOf(
            AskAnswer.Mandates::class.java,
            repository.ask("Quels mandats pour Candidat A ?", AskScope.AllPrograms),
        )
        assertInstanceOf(
            AskAnswer.Passages::class.java,
            repository.ask("Selon quelle règle a lieu l'élection fictive ?", AskScope.Legal),
        )
    }
}
