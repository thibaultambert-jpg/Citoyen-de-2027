package fr.citoyende2027.core.data.repository

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.dao.ProgramsRows
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.database.model.programStatusEntity
import fr.citoyende2027.core.database.model.themeEntities
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.ProgramsPackContent
import javax.inject.Inject

/** Single entry point to install packs (debug fixtures, embedded pack, verified downloads). */
class CorePackStore @Inject internal constructor(private val dao: CorePackDao) {
    suspend fun hasCorePack(): Boolean = dao.countPacks(DataPackInfo.CORE) > 0

    /** Version of an installed pack, or null (fictional demo data counts as "none"). */
    suspend fun installedVersion(packId: String = DataPackInfo.CORE): String? =
        dao.getPack(packId)?.takeUnless { it.isFictional }?.version

    /** Installs [core] and, from the same manifest, [programs], in one transaction. */
    suspend fun install(core: CorePackContent, programs: ProgramsPackContent? = null) {
        programs?.let(core::requireConsistent)
        dao.replaceCorePack(
            pack = core.info.asEntity(),
            candidates = core.candidates.map { it.asEntity() },
            coverage = core.candidates.flatMap { it.coverageEntities() },
            mandates = core.mandates.map { it.asEntity() },
            votes = core.votes.map { it.asEntity() },
            speeches = core.speeches.map { it.asEntity() },
            themes = core.themes.map { it.asEntity() },
            voteThemes = core.votes.flatMap { it.themeEntities() },
            programs = programs?.let { content ->
                ProgramsRows(
                    pack = content.info.asEntity(),
                    statuses = content.statuses.map { (id, status) -> programStatusEntity(id, status) },
                    documents = content.documents.map { it.asEntity() },
                    chunks = content.chunks.map { it.asEntity() },
                    chunkThemes = content.chunks.flatMap { it.themeEntities() },
                )
            },
        )
    }
}
