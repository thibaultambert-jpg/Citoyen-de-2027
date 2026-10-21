package fr.citoyende2027.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.core.data.pack.PackRuntime
import fr.citoyende2027.core.data.repository.CorePackStore
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Installs the packs embedded in the APK (assets/packs/: manifest.json, manifest.json.sig and the
 * pack files it lists) on first launch, with the same signature and hash checks as a download
 * (AGENTS.md §5).
 */
class EmbeddedPackSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: PackRuntime,
    private val store: CorePackStore,
) : StartupDataSeeder {
    /** True when an official pack is installed after the call. */
    suspend fun install(): Boolean {
        if (store.installedVersion() != null) return true
        val installer = runtime.installer ?: return false
        val assets = context.assets.list(DIRECTORY).orEmpty()
        if (MANIFEST !in assets || SIGNATURE !in assets) return false
        return try {
            val manifest = read(MANIFEST)
            val signature = read(SIGNATURE)
            val plan = installer.plan(manifest, signature) ?: return true
            val files = plan.entries.filter { it.file in assets }.associate { entry ->
                val target = File(context.cacheDir, "embedded-${entry.file}")
                context.assets.open("$DIRECTORY/${entry.file}").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                entry.id to target
            }
            installer.install(manifest, signature, files)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A refused embedded pack leaves the app without data rather than with unverified data.
            Log.w(TAG, "Embedded pack refused: ${error.javaClass.simpleName}")
            false
        }
    }

    override suspend fun seedIfNeeded() {
        install()
    }

    private fun read(name: String) = context.assets.open("$DIRECTORY/$name").use { it.readBytes() }

    private companion object {
        const val TAG = "EmbeddedPack"
        const val DIRECTORY = "packs"
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "manifest.json.sig"
    }
}
