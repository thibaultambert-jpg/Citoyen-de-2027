package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalPackContent
import fr.citoyende2027.core.model.ProgramsPackContent
import java.io.File

/** Reads verified pack files. Implemented with Android's SQLite in :core:database. */
interface PackFileReader {
    fun readCore(file: File): CorePackContent

    fun readPrograms(file: File): ProgramsPackContent

    fun readLegal(file: File): LegalPackContent
}

/** Supported `schema_version` of manifests and packs; must match the pipeline. */
const val SUPPORTED_PACK_SCHEMA = 3

sealed interface InstallResult {
    data class Installed(val version: String) : InstallResult

    data object UpToDate : InstallResult
}

/** Packs of a verified manifest this app installs: `core` (required), `programs` and `legal`. */
class InstallPlan(val manifest: PackManifest, val entries: List<PackEntry>) {
    fun entry(id: String): PackEntry? = entries.firstOrNull { it.id == id }
}

/**
 * Verifies then activates the packs of one signed manifest. Activation is atomic (one Room
 * transaction for all packs); the previous verified set is kept on disk so that [rollback] can
 * restore it (AGENTS.md §5). Packs of other kinds (e.g. models) are ignored here.
 */
class PackInstaller(
    private val verifier: PackVerifier,
    private val reader: PackFileReader,
    private val store: CorePackStore,
    private val directory: File,
) {
    private val active get() = File(directory, "active")
    private val previous get() = File(directory, "previous")
    private val next get() = File(directory, "next")
    private val staging get() = File(directory, "staging")

    /** Checks the signed manifest; null when every pack it lists is already installed. */
    suspend fun plan(manifest: ByteArray, signature: ByteArray): InstallPlan? {
        val parsed = verifier.verifyManifest(manifest, signature)
        if (parsed.schemaVersion != SUPPORTED_PACK_SCHEMA) {
            throw PackVerificationException("Unsupported manifest schema ${parsed.schemaVersion}")
        }
        val core = parsed.pack(DataPackInfo.CORE) ?: throw PackVerificationException("No core pack in manifest")
        val optional = OPTIONAL_PACKS.mapNotNull(parsed::pack)
        val upToDate = core.version == store.installedVersion(DataPackInfo.CORE) &&
            OPTIONAL_PACKS.all { id -> parsed.pack(id)?.version == store.installedVersion(id) }
        return if (upToDate) null else InstallPlan(parsed, listOf(core) + optional)
    }

    /** Staging file where a pack of [plan] is written (downloads resume from it). */
    fun stagingFile(entry: PackEntry): File = File(staging.apply { mkdirs() }, "${entry.sha256}-${entry.file}")

    /** The active file for [entry] when it is unchanged, so that only changed packs are downloaded. */
    fun reusableFile(entry: PackEntry): File? = File(active, entry.file).takeIf { file ->
        file.exists() && runCatching { verifier.verifyFile(file, entry) }.isSuccess
    }

    /** Installs the packs of [manifest]; [files] gives the file of each pack id not reusable as is. */
    suspend fun install(manifest: ByteArray, signature: ByteArray, files: Map<String, File>): InstallResult {
        val plan = plan(manifest, signature) ?: return InstallResult.UpToDate
        val resolved = plan.entries.associateWith { entry ->
            files[entry.id] ?: reusableFile(entry)
                ?: throw PackVerificationException("No file for pack ${entry.id}")
        }
        resolved.forEach { (entry, file) -> verifier.verifyFile(file, entry) }
        activate(plan, resolved)
        keep(manifest, signature, resolved)
        return InstallResult.Installed(plan.entries.first { it.id == DataPackInfo.CORE }.version)
    }

    /** Re-installs the previously active packs after re-verifying them. False when there are none. */
    suspend fun rollback(): Boolean {
        val manifest = File(previous, MANIFEST)
        if (!manifest.exists()) return false
        val parsed = verifier.verifyManifest(manifest.readBytes(), File(previous, SIGNATURE).readBytes())
        val entries = listOf(parsed.pack(DataPackInfo.CORE) ?: return false) + OPTIONAL_PACKS.mapNotNull(parsed::pack)
        val resolved = entries.associateWith { File(previous, it.file) }
        resolved.forEach { (entry, file) -> verifier.verifyFile(file, entry) }
        activate(InstallPlan(parsed, entries), resolved)
        active.deleteRecursively()
        previous.renameTo(active)
        return true
    }

    private suspend fun activate(plan: InstallPlan, files: Map<PackEntry, File>) {
        val core = reader.readCore(files.getValue(plan.entry(DataPackInfo.CORE)!!))
        checkVersion(core.info, plan.entry(DataPackInfo.CORE)!!)
        val programs = plan.entry(DataPackInfo.PROGRAMS)?.let { entry ->
            reader.readPrograms(files.getValue(entry)).also { checkVersion(it.info, entry) }
        }
        val legal = plan.entry(DataPackInfo.LEGAL)?.let { entry ->
            reader.readLegal(files.getValue(entry)).also { checkVersion(it.info, entry) }
        }
        try {
            store.install(core, programs, legal)
        } catch (error: IllegalArgumentException) {
            throw PackVerificationException("Packs of the manifest do not match each other", error)
        }
    }

    private fun checkVersion(info: DataPackInfo, entry: PackEntry) {
        if (info.version != entry.version) {
            throw PackVerificationException("Pack ${entry.id} version ${info.version} differs from manifest")
        }
    }

    /** Builds the new active set beside the old one, then swaps: a crash leaves one complete set. */
    private fun keep(manifest: ByteArray, signature: ByteArray, files: Map<PackEntry, File>) {
        next.deleteRecursively()
        next.mkdirs()
        files.forEach { (entry, file) ->
            val target = File(next, entry.file)
            val fromActive = file.parentFile?.canonicalPath == active.canonicalPath
            if (fromActive || !file.renameTo(target)) file.copyTo(target, overwrite = true)
        }
        File(next, MANIFEST).writeBytes(manifest)
        File(next, SIGNATURE).writeBytes(signature)
        previous.deleteRecursively()
        if (active.exists()) active.renameTo(previous)
        next.renameTo(active)
        staging.deleteRecursively()
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "manifest.json.sig"
        val OPTIONAL_PACKS = listOf(DataPackInfo.PROGRAMS, DataPackInfo.LEGAL)
    }
}
