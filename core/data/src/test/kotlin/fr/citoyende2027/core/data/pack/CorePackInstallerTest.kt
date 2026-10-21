package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.data.FakeCorePackDao
import fr.citoyende2027.core.data.repository.CorePackStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class CorePackInstallerTest {
    private val dao = FakeCorePackDao()
    private val store = CorePackStore(dao)

    private fun installer(dir: File, versionRead: (File) -> String = { TestPack.VERSION }) = CorePackInstaller(
        verifier = PackVerifier(TestPack.publicKey),
        reader = { file -> TestPack.content(versionRead(file)) },
        store = store,
        directory = File(dir, "packs"),
    )

    @Test
    fun `installs a verified pack then reports it up to date`(@TempDir dir: File) = runTest {
        val installer = installer(dir)
        val result = installer.install(TestPack.manifest, TestPack.signature, TestPack.packFile(dir))
        assertEquals(InstallResult.Installed(TestPack.VERSION), result)
        assertEquals(TestPack.VERSION, store.installedVersion())
        assertNull(installer.plan(TestPack.manifest, TestPack.signature))
    }

    @Test
    fun `a corrupted pack is refused and installed data is untouched`(@TempDir dir: File) = runTest {
        val corrupted = TestPack.pack.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertThrows<PackVerificationException> {
            installer(dir).install(TestPack.manifest, TestPack.signature, TestPack.packFile(dir, corrupted))
        }
        assertNull(store.installedVersion())
        assertEquals(emptyList<Any>(), dao.observeCandidates().first())
    }

    @Test
    fun `rollback restores the previous verified pack`(@TempDir dir: File) = runTest {
        val newer = TestPack.manifest.decodeToString().replace(TestPack.VERSION, "2026-02-01+fixture").toByteArray()
        var nextVersion = TestPack.VERSION
        val installer = installer(dir) { file ->
            if (file.parentFile?.name == "previous") TestPack.VERSION else nextVersion
        }
        assertFalse(installer.rollback())
        installer.install(TestPack.manifest, TestPack.signature, TestPack.packFile(dir))
        nextVersion = "2026-02-01+fixture"
        installer.install(newer, TestPack.sign(newer), TestPack.packFile(dir))
        assertEquals("2026-02-01+fixture", store.installedVersion())

        assertTrue(installer.rollback())
        assertEquals(TestPack.VERSION, store.installedVersion())
    }
}
