package fr.citoyende2027.core.database.model

import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.SourceRef

fun CandidateWithCoverage.asModel(): Candidate {
    val stored = coverage.associate { it.facet to it.level }
    return Candidate(
        id = CandidateId(candidate.id),
        displayName = candidate.displayName,
        sortName = candidate.sortName,
        status = CandidacyStatus.valueOf(candidate.status),
        statusDate = candidate.statusDate,
        statusSource = candidate.statusSource.asModel(),
        // A facet missing from the pack, or with an unknown level, is shown as unavailable:
        // absence of data is never turned into a value (AGENTS.md §2.2).
        coverage = DataCoverage(
            CoverageFacet.entries.associateWith { facet ->
                CoverageLevel.entries.firstOrNull { it.name == stored[facet.name] } ?: CoverageLevel.UNAVAILABLE
            },
        ),
    )
}

fun Candidate.asEntity() = CandidateEntity(
    id = id.value,
    displayName = displayName,
    sortName = sortName,
    status = status.name,
    statusDate = statusDate,
    statusSource = statusSource.asEmbedded(),
)

fun Candidate.coverageEntities(): List<CandidateCoverageEntity> = coverage.levels.map { (facet, level) ->
    CandidateCoverageEntity(candidateId = id.value, facet = facet.name, level = level.name)
}

fun SourceEmbedded.asModel() = SourceRef(
    name = name,
    url = url,
    retrievedOn = retrievedOn,
    license = license,
    packVersion = packVersion,
)

fun SourceRef.asEmbedded() = SourceEmbedded(
    name = name,
    url = url,
    retrievedOn = retrievedOn,
    license = license,
    packVersion = packVersion,
)

fun DataPackEntity.asModel() = DataPackInfo(
    id = id,
    version = version,
    generatedOn = generatedOn,
    isFictional = isFictional,
)

fun DataPackInfo.asEntity() = DataPackEntity(
    id = id,
    version = version,
    generatedOn = generatedOn,
    isFictional = isFictional,
)
