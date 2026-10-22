package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.network.HttpStatusException
import fr.citoyende2027.core.network.PackHttpClient
import java.io.IOException

sealed interface SyncResult {
    data class Installed(val version: String) : SyncResult

    data object UpToDate : SyncResult

    /** Sync is off, e.g. no public key configured: nothing unsigned is ever installed. */
    data object Disabled : SyncResult

    /** Network problem: worth retrying later. */
    data class Transient(val cause: Throwable) : SyncResult

    /** Signature, hash or format problem: the pack is refused and the current data is kept. */
    data class Rejected(val cause: Throwable) : SyncResult
}

/** Downloads the signed manifest and, when newer, the packs that changed from [baseUrl]. */
class PackSynchronizer(
    private val baseUrl: String,
    private val http: PackHttpClient,
    /** Null when no public key is configured: sync is then disabled. */
    private val installer: PackInstaller?,
) {
    suspend fun sync(): SyncResult {
        val installer = installer ?: return SyncResult.Disabled
        return try {
            val manifest = http.fetchBytes(baseUrl + MANIFEST)
            val signature = http.fetchBytes(baseUrl + SIGNATURE)
            val plan = installer.plan(manifest, signature) ?: return SyncResult.UpToDate
            val files = plan.entries.filter { installer.reusableFile(it) == null }.associate { entry ->
                val file = installer.stagingFile(entry)
                http.download(baseUrl + entry.file, file, entry.size)
                entry.id to file
            }
            when (val result = installer.install(manifest, signature, files)) {
                is InstallResult.Installed -> SyncResult.Installed(result.version)
                InstallResult.UpToDate -> SyncResult.UpToDate
            }
        } catch (error: PackVerificationException) {
            SyncResult.Rejected(error)
        } catch (error: HttpStatusException) {
            if (error.status in 500..599 ||
                error.status == 429
            ) {
                SyncResult.Transient(error)
            } else {
                SyncResult.Rejected(error)
            }
        } catch (error: IOException) {
            SyncResult.Transient(error)
        } catch (error: IllegalArgumentException) {
            SyncResult.Rejected(error)
        }
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "manifest.json.sig"
    }
}
