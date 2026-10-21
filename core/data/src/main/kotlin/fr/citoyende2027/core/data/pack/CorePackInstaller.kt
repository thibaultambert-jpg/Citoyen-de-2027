package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.DataPackInfo
import java.io.File

/** Reads a verified pack file. Implemented with Android's SQLite by CorePackReader. */
fun interface CorePackFileReader {
    fun read(file: File): CorePackContent
}

/** Supported `schema_version` of manifests and packs; must match the pipeline. */
const val SUPPORTED_PACK_SCHEMA = 1

sealed interface InstallResult {
    data class Installed(val version: String) : InstallResult

    data object UpToDate : InstallResult
}

/**
 * Verifies then activates a `core` pack. Activation is atomic (one Room transaction); the previous
 * verified pack is kept on disk so that [rollback] can restore it (AGENTS.md §5).
 */
class CorePackInstaller(
    private val verifier: PackVerifier,
    private val reader: CorePackFileReader,
    private val store: CorePackStore,
    private val directory: File,
) {
    private val active get() = File(directory, "active")
    private val previous get() = File(directory, "previous")
    private val staging get() = File(directory, "staging")

    /** Checks the signed manifest and tells which pack file to fetch, or null when already installed. */
    suspend fun plan(manifest: ByteArray, signature: ByteArray): Pair<PackManifest, PackEntry>? {
        val parsed = verifier.verifyManifest(manifest, signature)
        if (parsed.schemaVersion != SUPPORTED_PACK_SCHEMA) {
            throw PackVerificationException("Unsupported manifest schema ${parsed.schemaVersion}")
        }
        val entry = parsed.pack(DataPackInfo.CORE) ?: throw PackVerificationException("No core pack in manifest")
        return if (entry.version == store.installedVersion()) null else parsed to entry
    }

    /** Staging file where [plan]'s pack must be written (downloads resume from it). */
    fun stagingFile(entry: PackEntry): File = File(staging.apply { mkdirs() }, "${entry.sha256}.sqlite")

    suspend fun install(manifest: ByteArray, signature: ByteArray, packFile: File): InstallResult {
        val (_, entry) = plan(manifest, signature) ?: return InstallResult.UpToDate
        verifier.verifyFile(packFile, entry)
        val content = reader.read(packFile)
        if (content.info.version != entry.version) {
            throw PackVerificationException("Pack version ${content.info.version} differs from manifest")
        }
        store.install(content)
        keep(manifest, signature, packFile)
        return InstallResult.Installed(entry.version)
    }

    /** Re-installs the previously active pack after re-verifying it. False when there is none. */
    suspend fun rollback(): Boolean {
        val manifest = File(previous, MANIFEST)
        if (!manifest.exists()) return false
        val signature = File(previous, SIGNATURE).readBytes()
        val parsed = verifier.verifyManifest(manifest.readBytes(), signature)
        val entry = parsed.pack(DataPackInfo.CORE) ?: return false
        val file = File(previous, PACK)
        verifier.verifyFile(file, entry)
        store.install(reader.read(file))
        active.deleteRecursively()
        previous.renameTo(active)
        return true
    }

    private fun keep(manifest: ByteArray, signature: ByteArray, packFile: File) {
        previous.deleteRecursively()
        if (active.exists()) active.renameTo(previous)
        active.mkdirs()
        File(active, MANIFEST).writeBytes(manifest)
        File(active, SIGNATURE).writeBytes(signature)
        val target = File(active, PACK)
        if (!packFile.renameTo(target)) packFile.copyTo(target, overwrite = true)
        staging.deleteRecursively()
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "manifest.json.sig"
        const val PACK = "core.sqlite"
    }
}
