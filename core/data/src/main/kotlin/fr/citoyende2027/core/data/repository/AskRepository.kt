package fr.citoyende2027.core.data.repository

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ReservePeriod
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.orderedBy
import fr.citoyende2027.rag.CandidateName
import fr.citoyende2027.rag.IntentRouter
import fr.citoyende2027.rag.LexicalIndex
import fr.citoyende2027.rag.RagConfig
import fr.citoyende2027.rag.RagEngine
import fr.citoyende2027.rag.RagResult
import fr.citoyende2027.rag.RagScope
import fr.citoyende2027.rag.RefusalCategory
import fr.citoyende2027.rag.RefusalGate
import fr.citoyende2027.rag.StructuredIntent
import fr.citoyende2027.rag.asPassage
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a question is asked about (AGENTS.md §11, "Poser une question"). */
sealed interface AskScope {
    data object Legal : AskScope

    data class Candidate(val id: CandidateId) : AskScope

    /** Programmes of every candidate, side by side, alphabetical, same number of passages each. */
    data object AllPrograms : AskScope
}

/** A legal article found for a question, with its text. */
data class LegalPassage(val article: LegalArticle, val text: LegalText)

/** A programme excerpt found for a question, with its document. */
data class ProgramPassage(val chunk: ProgramChunk, val document: ProgramDocument?)

/** Passages of one group: the legal corpus, or one candidate's programme (empty: nothing relevant). */
data class AnswerGroup(val candidate: Candidate?, val legal: List<LegalPassage>, val programs: List<ProgramPassage>) {
    val isEmpty: Boolean get() = legal.isEmpty() && programs.isEmpty()
}

/** Answers without LLM (J6): only facts and verbatim passages from the installed packs. */
sealed interface AskAnswer {
    /** No retrieval settings in the installed data (no pack yet). */
    data object Unavailable : AskAnswer

    data class Refused(val category: RefusalCategory) : AskAnswer

    /** Nothing relevant in the app's documents; [dataDate] is the date of the data searched. */
    data class Abstained(val dataDate: LocalDate?) : AskAnswer

    data class Mandates(val candidate: Candidate, val mandates: List<Mandate>) : AskAnswer

    data class Votes(val candidate: Candidate, val votes: List<Vote>) : AskAnswer

    data class Passages(val groups: List<AnswerGroup>) : AskAnswer

    /** Programme questions during the electoral reserve period (AGENTS.md §10): nothing is searched. */
    data class Reserved(val reserve: ReservePeriod) : AskAnswer
}

interface AskRepository {
    /** The question never leaves the device and is not logged (AGENTS.md §2.4). */
    suspend fun ask(question: String, scope: AskScope): AskAnswer
}

internal class OfflineAskRepository @Inject constructor(private val dao: CorePackDao) : AskRepository {
    private val mutex = Mutex()
    private var cached: Pair<List<String?>, Index?>? = null

    /** Engine and the records behind each passage id, rebuilt when a pack version changes. */
    private class Index(
        val engine: RagEngine,
        val candidates: List<Candidate>,
        val legal: Map<String, LegalPassage>,
        val programs: Map<String, ProgramPassage>,
    )

    override suspend fun ask(question: String, scope: AskScope): AskAnswer = withContext(Dispatchers.Default) {
        val index = index() ?: return@withContext AskAnswer.Unavailable
        val ragScope = when (scope) {
            AskScope.Legal -> RagScope.Legal
            is AskScope.Candidate -> RagScope.Candidate(scope.id)
            AskScope.AllPrograms -> RagScope.Programs(index.candidates.map { it.id })
        }
        val result = index.engine.answer(question, ragScope)
        // Official records stay available; programme excerpts are campaign content (AGENTS.md §10).
        if ((result is RagResult.Passages || result == RagResult.Abstained) && scope != AskScope.Legal) {
            reserve()?.takeIf { it.hideCampaignContent }?.let { return@withContext AskAnswer.Reserved(it) }
        }
        when (result) {
            is RagResult.Refused -> AskAnswer.Refused(result.category)

            RagResult.Abstained -> AskAnswer.Abstained(dataDate(scope))

            is RagResult.Structured -> structured(result.intent, index)

            is RagResult.Passages -> AskAnswer.Passages(
                result.groups.map { group ->
                    AnswerGroup(
                        candidate = index.candidates.firstOrNull { it.id == group.candidateId },
                        legal = group.passages.mapNotNull { index.legal[it.passage.id] },
                        programs = group.passages.mapNotNull { index.programs[it.passage.id] },
                    )
                },
            )
        }
    }

    private suspend fun reserve(): ReservePeriod? =
        dao.getPack(DataPackInfo.CORE)?.let { ReservePeriod(it.hideCampaignContent, it.reserveSince) }

    private suspend fun structured(intent: StructuredIntent, index: Index): AskAnswer {
        val candidate = index.candidates.first { it.id == intent.candidateId }
        return when (intent) {
            is StructuredIntent.Mandates ->
                AskAnswer.Mandates(candidate, dao.observeMandates(candidate.id.value).first().map { it.asModel() })

            is StructuredIntent.Votes ->
                AskAnswer.Votes(candidate, dao.observeVotes(candidate.id.value).first().map { it.asModel() })
        }
    }

    private suspend fun dataDate(scope: AskScope): LocalDate? {
        val pack = if (scope == AskScope.Legal) DataPackInfo.LEGAL else DataPackInfo.PROGRAMS
        return (dao.getPack(pack) ?: dao.getPack(DataPackInfo.CORE))?.generatedOn
    }

    private suspend fun index(): Index? = mutex.withLock {
        val versions = listOf(DataPackInfo.CORE, DataPackInfo.PROGRAMS, DataPackInfo.LEGAL).map {
            dao.getPack(it)?.version
        }
        cached?.takeIf { it.first == versions }?.let { return it.second }
        val built = build()
        cached = versions to built
        built
    }

    private suspend fun build(): Index? {
        val config = dao.getRagConfig(DataPackInfo.CORE)?.let(RagConfig::parse) ?: return null
        val candidates = dao.getCandidates().map { it.asModel() }.orderedBy(CandidateOrder.Alphabetical)
        val texts = dao.getLegalTexts().map { it.asModel() }.associateBy { it.id }
        val legal = dao.getAllLegalArticles().map { it.asModel() }
            .mapNotNull { article -> texts[article.textId]?.let { LegalPassage(article, it) } }
        val documents = dao.getAllProgramDocuments().map { it.asModel() }.associateBy { it.id }
        val programs = dao.getAllProgramChunks().map {
            it.asModel()
        }.map { ProgramPassage(it, documents[it.documentId]) }
        val passages = legal.map { it.article.asPassage(it.text) } + programs.map { it.chunk.asPassage() }
        return Index(
            engine = RagEngine(
                config = config,
                lexical = LexicalIndex(passages),
                refusal = RefusalGate.bundled(),
                router = IntentRouter(candidates.map { CandidateName(it.id, it.displayName) }),
            ),
            candidates = candidates,
            legal = legal.associateBy { it.article.id },
            programs = programs.associateBy { it.chunk.id },
        )
    }
}
