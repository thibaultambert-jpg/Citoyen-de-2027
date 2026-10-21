package fr.citoyende2027.core.data.repository

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.asEntity
import fr.citoyende2027.core.database.model.coverageEntities
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.DataPackInfo
import javax.inject.Inject

/** Single entry point to install a `core` pack (debug fixtures now, verified packs from J2). */
class CorePackStore @Inject internal constructor(private val dao: CorePackDao) {
    suspend fun hasCorePack(): Boolean = dao.countPacks(DataPackInfo.CORE) > 0

    suspend fun install(content: CorePackContent) = dao.replaceCorePack(
        pack = content.info.asEntity(),
        candidates = content.candidates.map { it.asEntity() },
        coverage = content.candidates.flatMap { it.coverageEntities() },
        mandates = content.mandates.map { it.asEntity() },
        votes = content.votes.map { it.asEntity() },
    )
}
