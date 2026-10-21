package fr.citoyende2027.core.model

import java.time.LocalDate

/** Metadata of an activated data pack, shown as "Données du JJ/MM/AAAA" (AGENTS.md §5). */
data class DataPackInfo(
    val id: String,
    val version: String,
    val generatedOn: LocalDate,
    /** True only for the fictional demo data of debug builds; never in a published pack. */
    val isFictional: Boolean,
) {
    companion object {
        const val CORE = "core"
    }
}

/** Content of the `core` pack handled in J1: candidates only (mandates, votes… come with J2–J3). */
data class CorePackContent(val info: DataPackInfo, val candidates: List<Candidate>) {
    init {
        require(info.id == DataPackInfo.CORE) { "Not a core pack: ${info.id}" }
        require(candidates.map { it.id }.toSet().size == candidates.size) { "Duplicate candidate ids" }
    }
}
