package fr.citoyende2027.core.database.model

import fr.citoyende2027.core.model.BallotSubject
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.DataCoverage
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.ProgramChunk
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.SpeechKind
import fr.citoyende2027.core.model.TagConfidence
import fr.citoyende2027.core.model.TagMethod
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.ThemeTag
import fr.citoyende2027.core.model.Vote

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

fun Mandate.asEntity() = MandateEntity(
    id = id,
    candidateId = candidateId.value,
    body = body.name,
    label = label,
    startDate = startDate,
    endDate = endDate,
    source = source.asEmbedded(),
)

fun MandateEntity.asModel() = Mandate(
    id = id,
    candidateId = CandidateId(candidateId),
    body = Body.valueOf(body),
    label = label,
    startDate = startDate,
    endDate = endDate,
    source = source.asModel(),
)

fun Vote.asEntity() = VoteEntity(
    id = id,
    candidateId = candidateId.value,
    body = body.name,
    ballotId = ballotId,
    ballotDate = ballotDate,
    ballotTitle = ballotTitle,
    ballotType = ballotType,
    ballotSubject = ballotSubject?.name,
    position = position,
    ballotUrl = ballotUrl,
    source = source.asEmbedded(),
)

fun VoteEntity.asModel() = Vote(
    id = id,
    candidateId = CandidateId(candidateId),
    body = Body.valueOf(body),
    ballotId = ballotId,
    ballotDate = ballotDate,
    ballotTitle = ballotTitle,
    ballotType = ballotType,
    ballotSubject = ballotSubject?.let { name -> BallotSubject.entries.firstOrNull { it.name == name } },
    position = position,
    ballotUrl = ballotUrl,
    source = source.asModel(),
)

fun Speech.asEntity() = SpeechEntity(
    id = id,
    candidateId = candidateId.value,
    kind = kind.name,
    body = body?.name,
    date = date,
    title = title,
    detail = detail,
    url = url,
    source = source.asEmbedded(),
)

/** Null for a kind this version of the app does not know: such rows are not shown. */
fun SpeechEntity.asModel(): Speech? {
    val kind = SpeechKind.entries.firstOrNull { it.name == kind } ?: return null
    return Speech(
        id = id,
        candidateId = CandidateId(candidateId),
        kind = kind,
        body = body?.let { name -> Body.entries.firstOrNull { it.name == name } },
        date = date,
        title = title,
        detail = detail,
        url = url,
        source = source.asModel(),
    )
}

fun VoteWithThemes.asModel() = vote.asModel().copy(
    themes = themes.mapNotNull {
        tagOf(it.themeId, it.method, it.confidence)
    },
)

fun Vote.themeEntities() = themes.map {
    VoteThemeEntity(voteId = id, themeId = it.themeId.value, method = it.method.name, confidence = it.confidence.name)
}

fun Theme.asEntity() = ThemeEntity(id = id.value, label = label)

fun ThemeEntity.asModel() = Theme(ThemeId(id), label)

/** Null for a method or confidence this version of the app does not know: the tag is not shown. */
internal fun tagOf(themeId: String, method: String, confidence: String): ThemeTag? {
    val knownMethod = TagMethod.entries.firstOrNull { it.name == method } ?: return null
    val knownConfidence = TagConfidence.entries.firstOrNull { it.name == confidence } ?: return null
    return ThemeTag(ThemeId(themeId), knownMethod, knownConfidence)
}

const val HEADING_SEPARATOR = "\n"

fun ProgramDocument.asEntity() = ProgramDocumentEntity(
    id = id,
    candidateId = candidateId.value,
    title = title,
    publishedOn = publishedOn,
    version = version,
    source = source.asEmbedded(),
)

fun ProgramDocumentEntity.asModel() = ProgramDocument(
    id = id,
    candidateId = CandidateId(candidateId),
    title = title,
    publishedOn = publishedOn,
    version = version,
    source = source.asModel(),
)

fun ProgramChunk.asEntity() = ProgramChunkEntity(
    id = id,
    documentId = documentId,
    candidateId = candidateId.value,
    ordinal = ordinal,
    headingPath = headingPath.joinToString(HEADING_SEPARATOR),
    page = page,
    anchor = anchor,
    text = text,
)

fun ProgramChunk.themeEntities() = themes.map {
    ChunkThemeEntity(chunkId = id, themeId = it.themeId.value, method = it.method.name, confidence = it.confidence.name)
}

fun ChunkWithThemes.asModel() = ProgramChunk(
    id = chunk.id,
    documentId = chunk.documentId,
    candidateId = CandidateId(chunk.candidateId),
    ordinal = chunk.ordinal,
    headingPath = chunk.headingPath.split(HEADING_SEPARATOR).filter { it.isNotEmpty() },
    page = chunk.page,
    anchor = chunk.anchor,
    text = chunk.text,
    themes = themes.mapNotNull { tagOf(it.themeId, it.method, it.confidence) },
)

/** Unknown status values are shown as "not published": absence of data is never turned into a value. */
fun programStatusOf(value: String?): ProgramStatus =
    ProgramStatus.entries.firstOrNull { it.name == value } ?: ProgramStatus.NON_PUBLIE

fun programStatusEntity(candidateId: CandidateId, status: ProgramStatus) =
    ProgramStatusEntity(candidateId = candidateId.value, status = status.name)
