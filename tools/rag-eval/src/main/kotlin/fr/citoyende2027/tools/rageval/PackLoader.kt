package fr.citoyende2027.tools.rageval

import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.rag.CandidateName
import fr.citoyende2027.rag.Passage
import fr.citoyende2027.rag.asPassage
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate

/** What the evaluation needs from the packs: passages, candidate names and the shipped rag_config. */
data class EvalCorpus(val passages: List<Passage>, val candidates: List<CandidateName>, val ragConfig: String?)

/** Reads the packs written by the pipeline (same tables as the app's readers). */
object PackLoader {
    fun load(directory: File): EvalCorpus {
        val core = File(directory, "core.sqlite")
        val legal = File(directory, "legal.sqlite")
        val programs = File(directory, "programs.sqlite")
        val candidates = if (core.exists()) connect(core).use(::candidates) else emptyList()
        val ragConfig = if (core.exists()) connect(core).use { meta(it)["rag_config"] } else null
        val passages = (if (legal.exists()) connect(legal).use(::legalPassages) else emptyList()) +
            (if (programs.exists()) connect(programs).use(::programPassages) else emptyList())
        return EvalCorpus(passages, candidates, ragConfig)
    }

    private fun connect(file: File): Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    private fun <T> Connection.rows(sql: String, row: (ResultSet) -> T): List<T> = createStatement().use { statement ->
        statement.executeQuery(sql).use { result -> buildList { while (result.next()) add(row(result)) } }
    }

    private fun meta(db: Connection): Map<String, String> =
        db.rows("SELECT key, value FROM meta") { it.getString(1) to it.getString(2) }.toMap()

    private fun candidates(db: Connection): List<CandidateName> = db.rows("SELECT id, display_name FROM candidates") {
        CandidateName(CandidateId(it.getString(1)), it.getString(2))
    }

    private fun legalPassages(db: Connection): List<Passage> {
        val version = meta(db).getValue("version")
        val texts = db.rows("SELECT id, title, position, url, source_name, retrieved_on, license FROM legal_texts") {
            LegalText(
                id = it.getString(1),
                title = it.getString(2),
                position = it.getInt(3),
                source = SourceRef(
                    it.getString(5),
                    it.getString(4),
                    LocalDate.parse(it.getString(6)),
                    it.getString(7),
                    version,
                ),
            )
        }.associateBy { it.id }
        return db.rows("SELECT id, text_id, ordinal, number, heading_path, body, url FROM legal_articles") {
            LegalArticle(
                id = it.getString(1),
                textId = it.getString(2),
                ordinal = it.getInt(3),
                number = it.getString(4),
                headingPath = it.getString(5).split("\n").filter(String::isNotEmpty),
                text = it.getString(6),
                inForceSince = null,
                url = it.getString(7),
            )
        }.map { article -> article.asPassage(texts.getValue(article.textId)) }
    }

    private fun programPassages(db: Connection): List<Passage> =
        db.rows("SELECT id, document_id, candidate_id, ordinal, heading_path, text FROM program_chunks") {
            ProgramChunk(
                id = it.getString(1),
                documentId = it.getString(2),
                candidateId = CandidateId(it.getString(3)),
                ordinal = it.getInt(4),
                headingPath = it.getString(5).split("\n").filter(String::isNotEmpty),
                page = null,
                anchor = null,
                text = it.getString(6),
                themes = emptyList(),
            ).asPassage()
        }
}
