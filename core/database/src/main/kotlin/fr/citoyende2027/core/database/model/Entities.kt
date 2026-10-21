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
