package fr.citoyende2027.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.CandidateCoverageEntity
import fr.citoyende2027.core.database.model.CandidateEntity
import fr.citoyende2027.core.database.model.ChunkThemeEntity
import fr.citoyende2027.core.database.model.DataPackEntity
import fr.citoyende2027.core.database.model.MandateEntity
import fr.citoyende2027.core.database.model.ProgramChunkEntity
import fr.citoyende2027.core.database.model.ProgramDocumentEntity
import fr.citoyende2027.core.database.model.ProgramStatusEntity
import fr.citoyende2027.core.database.model.SpeechEntity
import fr.citoyende2027.core.database.model.ThemeEntity
import fr.citoyende2027.core.database.model.VoteEntity
import fr.citoyende2027.core.database.model.VoteThemeEntity
import java.time.LocalDate

@Database(
    entities = [
        CandidateEntity::class,
        CandidateCoverageEntity::class,
        DataPackEntity::class,
        MandateEntity::class,
        VoteEntity::class,
        SpeechEntity::class,
        ThemeEntity::class,
        VoteThemeEntity::class,
        ProgramStatusEntity::class,
        ProgramDocumentEntity::class,
        ProgramChunkEntity::class,
        ChunkThemeEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(LocalDateConverter::class)
abstract class CitoyenDatabase : RoomDatabase() {
    abstract fun corePackDao(): CorePackDao
}

/** Dates are stored as ISO-8601 text (yyyy-MM-dd): readable in the published SQLite packs. */
internal class LocalDateConverter {
    @TypeConverter
    fun fromText(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun toText(date: LocalDate?): String? = date?.toString()
}
