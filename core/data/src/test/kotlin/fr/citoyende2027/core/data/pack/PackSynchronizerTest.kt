package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.data.FakeCorePackDao
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.network.ConnectionFactory
import fr.citoyende2027.core.network.PackHttpClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Serves files from memory: no test reaches the network (AGENTS.md §4). */
private class FakeReleases(private val files: Map<String, ByteArray>, private val status: Int = 200) {
    val requested = mutableListOf<String>()

    val factory = ConnectionFactory { url ->
        object : HttpURLConnection(url) {
            override fun getResponseCode(): Int {
                requested += url.toString()
                return if (files.containsKey(url.path.substringAfterLast('/'))) status else HTTP_NOT_FOUND
            }

            override fun getInputStream(): InputStream =
                ByteArrayInputStream(files.getValue(url.path.substringAfterLast('/')))

            override fun connect() = Unit

            override fun disconnect() = Unit

            override fun usingProxy() = false
        }
    }
}

class PackSynchronizerTest {
    private val base = "https://example.invalid/releases/download/packs-current/"
    private val store = CorePackStore(FakeCorePackDao())

    private fun synchronizer(dir: File, releases: FakeReleases, withKey: Boolean = true) = PackSynchronizer(
        baseUrl = base,
        http = PackHttpClient(releases.factory),
        installer = if (withKey) {
            PackInstaller(PackVerifier(TestPack.publicKey), TestPack.reader(), store, dir)
        } else {
            null
        },
    )

    private val published = mapOf(
        "manifest.json" to TestPack.manifest,
        "manifest.json.sig" to TestPack.signature,
        "core.sqlite" to TestPack.pack,
        "programs.sqlite" to TestPack.programs,
        "legal.sqlite" to TestPack.legal,
    )

    @Test
    fun `installs then stays up to date without downloading the pack again`(@TempDir dir: File) = runTest {
        val releases = FakeReleases(published)
        val sync = synchronizer(dir, releases)
        assertEquals(SyncResult.Installed(TestPack.VERSION), sync.sync())
        assertEquals(SyncResult.UpToDate, sync.sync())
        assertEquals(1, releases.requested.count { it.endsWith("core.sqlite") })
        assertEquals(1, releases.requested.count { it.endsWith("programs.sqlite") })
        assertEquals(1, releases.requested.count { it.endsWith("legal.sqlite") })
    }

    @Test
    fun `without public key nothing is downloaded`(@TempDir dir: File) = runTest {
        val releases = FakeReleases(published)
        assertEquals(SyncResult.Disabled, synchronizer(dir, releases, withKey = false).sync())
        assertEquals(emptyList<String>(), releases.requested)
    }

    @Test
    fun `a forged signature is rejected`(@TempDir dir: File) = runTest {
        val forged = published + ("manifest.json.sig" to TestPack.sign("autre".toByteArray()))
        assertInstanceOf(SyncResult.Rejected::class.java, synchronizer(dir, FakeReleases(forged)).sync())
    }

    @Test
    fun `server errors are retried later`(@TempDir dir: File) = runTest {
        assertInstanceOf(SyncResult.Transient::class.java, synchronizer(dir, FakeReleases(published, 503)).sync())
    }
}
