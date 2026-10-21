package fr.citoyende2027.core.model

import java.time.LocalDate

/** A programme document listed by hand in the pipeline, with its validated reuse terms. */
data class ProgramDocument(
    val id: String,
    val candidateId: CandidateId,
    val title: String,
    /** Date shown by the document or its official page. */
    val publishedOn: LocalDate,
    val version: String,
    /** Official URL, retrieval date, reuse terms and pack version. */
    val source: SourceRef,
)

/** A verbatim excerpt of a programme document (AGENTS.md §7.5): never rephrased. */
data class ProgramChunk(
    val id: String,
    val documentId: String,
    val candidateId: CandidateId,
    val ordinal: Int,
    /** Headings above the excerpt, outermost first. */
    val headingPath: List<String>,
    /** Page of the PDF file (1-based), null for a web page. */
    val page: Int?,
    /** Anchor in the web page, if any. */
    val anchor: String?,
    val text: String,
    val themes: List<ThemeTag>,
) {
    fun hasTheme(theme: ThemeId): Boolean = themes.any { it.themeId == theme }
}

/** A candidate's programme as published in the `programs` pack. */
data class CandidateProgram(
    val candidateId: CandidateId,
    val status: ProgramStatus,
    val documents: List<ProgramDocument>,
    /** Excerpts in document order. */
    val chunks: List<ProgramChunk>,
) {
    fun document(id: String): ProgramDocument? = documents.firstOrNull { it.id == id }

    companion object {
        fun notPublished(candidateId: CandidateId) =
            CandidateProgram(candidateId, ProgramStatus.NON_PUBLIE, emptyList(), emptyList())
    }
}

/** Content of the `programs` pack. */
data class ProgramsPackContent(
    val info: DataPackInfo,
    val statuses: Map<CandidateId, ProgramStatus>,
    val documents: List<ProgramDocument>,
    val chunks: List<ProgramChunk>,
) {
    init {
        require(info.id == DataPackInfo.PROGRAMS) { "Not a programs pack: ${info.id}" }
        val documentIds = documents.map { it.id }.toSet()
        require(documentIds.size == documents.size) { "Duplicate document ids" }
        require(chunks.all { it.documentId in documentIds }) { "Excerpt of an unknown document" }
    }

    fun program(candidateId: CandidateId): CandidateProgram = CandidateProgram(
        candidateId = candidateId,
        status = statuses[candidateId] ?: ProgramStatus.NON_PUBLIE,
        documents = documents.filter { it.candidateId == candidateId },
        chunks = chunks.filter { it.candidateId == candidateId }.sortedWith(
            compareBy<ProgramChunk> { it.documentId }.thenBy { it.ordinal },
        ),
    )
}
