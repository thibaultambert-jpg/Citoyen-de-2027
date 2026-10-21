package fr.citoyende2027.core.model

/** Facets for which data coverage is shown per candidate (AGENTS.md §10). */
enum class CoverageFacet {
    PROGRAM,
    MANDATES,
    VOTES,
    SPEECHES,
}

enum class CoverageLevel {
    AVAILABLE,
    PARTIAL,

    /** E.g. votes for a candidate who never sat in a parliament. */
    NOT_APPLICABLE,

    /** Absence of data, never to be read as absence of a position. */
    UNAVAILABLE,
}

/** Coverage badge: one level for every facet, so all candidates share the same template. */
data class DataCoverage(val levels: Map<CoverageFacet, CoverageLevel>) {
    init {
        require(levels.keys == CoverageFacet.entries.toSet()) {
            "Coverage must define every facet, missing: ${CoverageFacet.entries - levels.keys}"
        }
    }

    operator fun get(facet: CoverageFacet): CoverageLevel = levels.getValue(facet)

    companion object {
        val Unavailable = DataCoverage(CoverageFacet.entries.associateWith { CoverageLevel.UNAVAILABLE })
    }
}
