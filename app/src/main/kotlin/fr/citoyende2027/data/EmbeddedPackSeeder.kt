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
 * Installs the `core` pack embedded in the APK (assets/packs/: manifest.json, manifest.json.sig,
 * core.sqlite) on first launch, with the same signature and hash checks as a download (AGENTS.md §5).
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
        val files = context.assets.list(DIRECTORY).orEmpty()
        if (MANIFEST !in files || SIGNATURE !in files || PACK !in files) return false
        return try {
            val target = File(context.cacheDir, "embedded-core.sqlite")
            context.assets.open("$DIRECTORY/$PACK").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            installer.install(read(MANIFEST), read(SIGNATURE), target)
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
        const val PACK = "core.sqlite"
    }
}
