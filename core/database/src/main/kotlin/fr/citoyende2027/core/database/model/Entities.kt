package fr.citoyende2027.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

/** Enum-like columns are stored as their published names (TEXT), never as ordinals. */
@Entity(tableName = "candidates")
data class CandidateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val status: String,
    @ColumnInfo(name = "status_date") val statusDate: LocalDate,
    @Embedded(prefix = "status_source_") val statusSource: SourceEmbedded,
)

data class SourceEmbedded(
    val name: String,
    val url: String,
    @ColumnInfo(name = "retrieved_on") val retrievedOn: LocalDate,
    val license: String,
    @ColumnInfo(name = "pack_version") val packVersion: String,
)

@Entity(
    tableName = "candidate_coverage",
    primaryKeys = ["candidate_id", "facet"],
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidate_id")],
)
data class CandidateCoverageEntity(
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val facet: String,
    val level: String,
)

data class CandidateWithCoverage(
    @Embedded val candidate: CandidateEntity,
    @Relation(parentColumn = "id", entityColumn = "candidate_id")
    val coverage: List<CandidateCoverageEntity>,
)

@Entity(tableName = "data_packs")
data class DataPackEntity(
    @PrimaryKey val id: String,
    val version: String,
    @ColumnInfo(name = "generated_on") val generatedOn: LocalDate,
    @ColumnInfo(name = "is_fictional") val isFictional: Boolean,
)

@Entity(
    tableName = "mandates",
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidate_id")],
)
data class MandateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val body: String,
    val label: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @Embedded(prefix = "source_") val source: SourceEmbedded,
)

@Entity(
    tableName = "votes",
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidate_id"), Index("ballot_date")],
)
data class VoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val body: String,
    @ColumnInfo(name = "ballot_id") val ballotId: String,
    @ColumnInfo(name = "ballot_date") val ballotDate: LocalDate,
    @ColumnInfo(name = "ballot_title") val ballotTitle: String,
    @ColumnInfo(name = "ballot_type") val ballotType: String?,
    @ColumnInfo(name = "ballot_subject") val ballotSubject: String?,
    val position: String,
    @ColumnInfo(name = "ballot_url") val ballotUrl: String,
    @Embedded(prefix = "source_") val source: SourceEmbedded,
)

@Entity(
    tableName = "speeches",
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidate_id")],
)
data class SpeechEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val kind: String,
    val body: String?,
    @ColumnInfo(name = "speech_date") val date: LocalDate,
    val title: String,
    val detail: String?,
    val url: String,
    @Embedded(prefix = "source_") val source: SourceEmbedded,
)

@Entity(tableName = "themes")
data class ThemeEntity(@PrimaryKey val id: String, val label: String)

/** Theme tag of a vote; removed with its vote. */
@Entity(
    tableName = "vote_themes",
    primaryKeys = ["vote_id", "theme_id"],
    foreignKeys = [
        ForeignKey(
            entity = VoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["vote_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vote_id"), Index("theme_id")],
)
data class VoteThemeEntity(
    @ColumnInfo(name = "vote_id") val voteId: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    val method: String,
    val confidence: String,
)

data class VoteWithThemes(
    @Embedded val vote: VoteEntity,
    @Relation(parentColumn = "id", entityColumn = "vote_id")
    val themes: List<VoteThemeEntity>,
)

@Entity(
    tableName = "program_status",
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgramStatusEntity(
    @PrimaryKey @ColumnInfo(name = "candidate_id") val candidateId: String,
    val status: String,
)

@Entity(
    tableName = "program_documents",
    foreignKeys = [
        ForeignKey(
            entity = CandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidate_id")],
)
data class ProgramDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val title: String,
    @ColumnInfo(name = "published_on") val publishedOn: LocalDate,
    val version: String,
    @Embedded(prefix = "source_") val source: SourceEmbedded,
)

@Entity(
    tableName = "program_chunks",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("document_id"), Index("candidate_id")],
)
data class ProgramChunkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    val ordinal: Int,
    /** Headings separated by a line feed, as in the published pack. */
    @ColumnInfo(name = "heading_path") val headingPath: String,
    val page: Int?,
    val anchor: String?,
    val text: String,
)

@Entity(
    tableName = "chunk_themes",
    primaryKeys = ["chunk_id", "theme_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunk_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chunk_id"), Index("theme_id")],
)
data class ChunkThemeEntity(
    @ColumnInfo(name = "chunk_id") val chunkId: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    val method: String,
    val confidence: String,
)

data class ChunkWithThemes(
    @Embedded val chunk: ProgramChunkEntity,
    @Relation(parentColumn = "id", entityColumn = "chunk_id")
    val themes: List<ChunkThemeEntity>,
)
