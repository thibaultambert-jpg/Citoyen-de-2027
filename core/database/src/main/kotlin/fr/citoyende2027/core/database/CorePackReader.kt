package fr.citoyende2027.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import fr.citoyende2027.core.database.model.HEADING_SEPARATOR
import fr.citoyende2027.core.database.model.tagOf
import fr.citoyende2027.core.model.BallotSubject
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalPackContent
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.ProgramsPackContent
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.ThemeTag
import fr.citoyende2027.core.model.Vote
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

class UnsupportedPackException(message: String) : Exception(message)

/**
 * Reads a `core` pack SQLite file written by the pipeline (pipeline/citoyen_pipeline/build.py).
 * Only files whose signature and hash were verified are given to this reader.
 */
class CorePackReader @Inject constructor() {
    fun read(file: File): CorePackContent {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val meta = db.meta(DataPackInfo.CORE)
            val version = meta.getValue("version")
            val sources = db.query("SELECT id, name, url, retrieved_on, license FROM sources") {
                it.getString(0) to SourceRef(
                    name = it.getString(1),
                    url = it.getString(2),
                    retrievedOn = LocalDate.parse(it.getString(3)),
                    license = it.getString(4),
                    packVersion = version,
                )
            }.toMap()
            val coverage = db.query("SELECT candidate_id, facet, level FROM candidate_coverage") {
                Triple(it.getString(0), it.getString(1), it.getString(2))
            }.groupBy({ it.first }, { it.second to it.third })
            val candidates = db.query(
                "SELECT id, display_name, sort_name, status, status_date, status_source_id FROM candidates",
            ) {
                val id = it.getString(0)
                Candidate(
                    id = CandidateId(id),
                    displayName = it.getString(1),
                    sortName = it.getString(2),
                    status = CandidacyStatus.valueOf(it.getString(3)),
                    statusDate = LocalDate.parse(it.getString(4)),
                    statusSource = sources.getValue(it.getString(5)),
                    coverage = coverageOf(coverage[id].orEmpty().toMap()),
                )
            }
            val mandates = db.query(
                "SELECT id, candidate_id, body, label, start_date, end_date, source_id FROM mandates",
            ) {
                Mandate(
                    id = it.getString(0),
                    candidateId = CandidateId(it.getString(1)),
                    body = Body.valueOf(it.getString(2)),
                    label = it.getString(3),
                    startDate = LocalDate.parse(it.getString(4)),
                    endDate = it.getStringOrNull(5)?.let(LocalDate::parse),
                    source = sources.getValue(it.getString(6)),
                )
            }
            val themes = db.query("SELECT id, label FROM themes") { Theme(ThemeId(it.getString(0)), it.getString(1)) }
            val voteThemes = db.tags("SELECT vote_id, theme_id, method, confidence FROM vote_themes")
            val votes = db.query(
                "SELECT id, candidate_id, body, ballot_id, ballot_date, ballot_title, ballot_type, ballot_subject, " +
                    "position, ballot_url, source_id FROM votes",
            ) {
                Vote(
                    id = it.getString(0),
                    candidateId = CandidateId(it.getString(1)),
                    body = Body.valueOf(it.getString(2)),
                    ballotId = it.getString(3),
                    ballotDate = LocalDate.parse(it.getString(4)),
                    ballotTitle = it.getString(5),
                    ballotType = it.getStringOrNull(6),
                    ballotSubject = it.getStringOrNull(7)?.let { name ->
                        BallotSubject.entries.firstOrNull { subject -> subject.name == name } ?: BallotSubject.OTHER
                    },
                    position = it.getString(8),
                    ballotUrl = it.getString(9),
                    source = sources.getValue(it.getString(10)),
                    themes = voteThemes[it.getString(0)].orEmpty(),
                )
            }
            val speeches = db.query(
                "SELECT id, candidate_id, kind, body, speech_date, title, detail, url, source_id FROM speeches",
            ) { row ->
                SpeechKind.entries.firstOrNull { it.name == row.getString(2) }?.let { kind ->
                    Speech(
                        id = row.getString(0),
                        candidateId = CandidateId(row.getString(1)),
                        kind = kind,
                        body = row.getStringOrNull(3)?.let(Body::valueOf),
                        date = LocalDate.parse(row.getString(4)),
                        title = row.getString(5),
                        detail = row.getStringOrNull(6),
                        url = row.getString(7),
                        source = sources.getValue(row.getString(8)),
                    )
                }
            }.filterNotNull()
            val info = DataPackInfo(
                id = DataPackInfo.CORE,
                version = version,
                generatedOn = LocalDate.parse(meta.getValue("generated_on")),
                isFictional = false,
            )
            return CorePackContent(info, candidates, mandates, votes, speeches, themes)
        } finally {
            db.close()
        }
    }

    private fun coverageOf(stored: Map<String, String>) = DataCoverage(
        CoverageFacet.entries.associateWith { facet ->
            CoverageLevel.entries.firstOrNull { it.name == stored[facet.name] } ?: CoverageLevel.UNAVAILABLE
        },
    )

    companion object {
        /** Must match PACK_SCHEMA_VERSION in pipeline/citoyen_pipeline/__init__.py. */
        const val SUPPORTED_SCHEMA_VERSION = 3
    }
}

/** Reads a `programs` pack written by the pipeline (build.py, write_programs_pack). */
class ProgramsPackReader @Inject constructor() {
    fun read(file: File): ProgramsPackContent {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val meta = db.meta(DataPackInfo.PROGRAMS)
            val version = meta.getValue("version")
            val statuses = db.query("SELECT candidate_id, status FROM program_status") { row ->
                ProgramStatus.entries.firstOrNull { it.name == row.getString(1) }?.let {
                    CandidateId(row.getString(0)) to
                        it
                }
            }.filterNotNull().toMap()
            val documents = db.query(
                "SELECT id, candidate_id, title, url, published_on, version, retrieved_on, license FROM program_documents",
            ) {
                ProgramDocument(
                    id = it.getString(0),
                    candidateId = CandidateId(it.getString(1)),
                    title = it.getString(2),
                    publishedOn = LocalDate.parse(it.getString(4)),
                    version = it.getString(5),
                    source = SourceRef(
                        name = it.getString(2),
                        url = it.getString(3),
                        retrievedOn = LocalDate.parse(it.getString(6)),
                        license = it.getString(7),
                        packVersion = version,
                    ),
                )
            }
            val chunkThemes = db.tags("SELECT chunk_id, theme_id, method, confidence FROM chunk_themes")
            val chunks = db.query(
                "SELECT id, document_id, candidate_id, ordinal, heading_path, page, anchor, text FROM program_chunks",
            ) {
                ProgramChunk(
                    id = it.getString(0),
                    documentId = it.getString(1),
                    candidateId = CandidateId(it.getString(2)),
                    ordinal = it.getInt(3),
                    headingPath = it.getString(4).split(HEADING_SEPARATOR).filter(String::isNotEmpty),
                    page = if (it.isNull(5)) null else it.getInt(5),
                    anchor = it.getStringOrNull(6),
                    text = it.getString(7),
                    themes = chunkThemes[it.getString(0)].orEmpty(),
                )
            }
            val info = DataPackInfo(
                id = DataPackInfo.PROGRAMS,
                version = version,
                generatedOn = LocalDate.parse(meta.getValue("generated_on")),
                isFictional = false,
            )
            return ProgramsPackContent(info, statuses, documents, chunks)
        } finally {
            db.close()
        }
    }
}

/** Reads a `legal` pack written by the pipeline (build.py, write_legal_pack). */
class LegalPackReader @Inject constructor() {
    fun read(file: File): LegalPackContent {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val meta = db.meta(DataPackInfo.LEGAL)
            val version = meta.getValue("version")
            val texts = db.query(
                "SELECT id, title, position, url, source_name, retrieved_on, license FROM legal_texts",
            ) {
                LegalText(
                    id = it.getString(0),
                    title = it.getString(1),
                    position = it.getInt(2),
                    source = SourceRef(
                        name = it.getString(4),
                        url = it.getString(3),
                        retrievedOn = LocalDate.parse(it.getString(5)),
                        license = it.getString(6),
                        packVersion = version,
                    ),
                )
            }
            val articles = db.query(
                "SELECT id, text_id, ordinal, number, heading_path, body, in_force_since, url FROM legal_articles",
            ) {
                LegalArticle(
                    id = it.getString(0),
                    textId = it.getString(1),
                    ordinal = it.getInt(2),
                    number = it.getString(3),
                    headingPath = it.getString(4).split(HEADING_SEPARATOR).filter(String::isNotEmpty),
                    text = it.getString(5),
                    inForceSince = it.getStringOrNull(6)?.let(LocalDate::parse),
                    url = it.getString(7),
                )
            }
            val info = DataPackInfo(
                id = DataPackInfo.LEGAL,
                version = version,
                generatedOn = LocalDate.parse(meta.getValue("generated_on")),
                isFictional = false,
            )
            return LegalPackContent(info, texts, articles)
        } finally {
            db.close()
        }
    }
}

private fun SQLiteDatabase.meta(packId: String): Map<String, String> {
    val meta = query("SELECT key, value FROM meta") { it.getString(0) to it.getString(1) }.toMap()
    val schema = meta["schema_version"]?.toIntOrNull()
    if (schema != CorePackReader.SUPPORTED_SCHEMA_VERSION) {
        throw UnsupportedPackException(
            "Unsupported pack schema $schema (expected ${CorePackReader.SUPPORTED_SCHEMA_VERSION})",
        )
    }
    if (meta["pack_id"] != packId) throw UnsupportedPackException("Not a $packId pack")
    return meta
}

/** Theme tags per item id; tags with an unknown method or confidence are left out. */
private fun SQLiteDatabase.tags(sql: String): Map<String, List<ThemeTag>> =
    query(sql) { row -> tagOf(row.getString(1), row.getString(2), row.getString(3))?.let { row.getString(0) to it } }
        .filterNotNull()
        .groupBy({ it.first }, { it.second })

private fun <T> SQLiteDatabase.query(sql: String, row: (Cursor) -> T): List<T> =
    rawQuery(sql, null).use { cursor -> buildList { while (cursor.moveToNext()) add(row(cursor)) } }

private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
