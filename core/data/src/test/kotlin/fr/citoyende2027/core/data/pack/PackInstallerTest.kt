package fr.citoyende2027.core.data.pack

import fr.citoyende2027.core.data.FakeCorePackDao
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.model.DataPackInfo
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

class PackInstallerTest {
    private val dao = FakeCorePackDao()
    private val store = CorePackStore(dao)

    private fun installer(dir: File, coreVersion: (File) -> String = { TestPack.VERSION }) = PackInstaller(
        verifier = PackVerifier(TestPack.publicKey),
        reader = TestPack.reader(coreVersion),
        store = store,
        directory = File(dir, "packs"),
    )

    @Test
    fun `installs every pack of a verified manifest then reports it up to date`(@TempDir dir: File) = runTest {
        val installer = installer(dir)
        val result = installer.install(TestPack.manifest, TestPack.signature, TestPack.files(dir))
        assertEquals(InstallResult.Installed(TestPack.VERSION), result)
        assertEquals(TestPack.VERSION, store.installedVersion())
        assertEquals(TestPack.PROGRAMS_VERSION, store.installedVersion(DataPackInfo.PROGRAMS))
        assertNull(installer.plan(TestPack.manifest, TestPack.signature))
    }

    @Test
    fun `a corrupted pack is refused and installed data is untouched`(@TempDir dir: File) = runTest {
        val corrupted = TestPack.pack.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertThrows<PackVerificationException> {
            installer(dir).install(TestPack.manifest, TestPack.signature, TestPack.files(dir, corrupted))
        }
        assertNull(store.installedVersion())
        assertEquals(emptyList<Any>(), dao.observeCandidates().first())
    }

    @Test
    fun `a missing pack file is refused`(@TempDir dir: File) = runTest {
        assertThrows<PackVerificationException> {
            installer(dir).install(TestPack.manifest, TestPack.signature, mapOf("core" to TestPack.packFile(dir)))
        }
        assertNull(store.installedVersion())
    }

    @Test
    fun `an unchanged pack is reused from the active set`(@TempDir dir: File) = runTest {
        // The active copy is read when reused: it then stands for the newer version.
        val installer = installer(dir) { file ->
            if (file.parentFile?.name == "active") "2026-02-01+fixture" else TestPack.VERSION
        }
        installer.install(TestPack.manifest, TestPack.signature, TestPack.files(dir))
        val newer = TestPack.manifest.decodeToString().replace(TestPack.VERSION, "2026-02-01+fixture").toByteArray()
        val plan = checkNotNull(installer.plan(newer, TestPack.sign(newer)))
        // Same bytes for both packs: everything can be reused, nothing needs a download.
        assertTrue(plan.entries.all { installer.reusableFile(it) != null })
        installer.install(newer, TestPack.sign(newer), emptyMap())
        assertEquals("2026-02-01+fixture", store.installedVersion())
    }

    @Test
    fun `rollback restores the previous verified packs`(@TempDir dir: File) = runTest {
        val newer = TestPack.manifest.decodeToString().replace(TestPack.VERSION, "2026-02-01+fixture").toByteArray()
        var nextVersion = TestPack.VERSION
        val installer = installer(dir) { file ->
            if (file.parentFile?.name == "previous") TestPack.VERSION else nextVersion
        }
        assertFalse(installer.rollback())
        installer.install(TestPack.manifest, TestPack.signature, TestPack.files(dir))
        nextVersion = "2026-02-01+fixture"
        installer.install(newer, TestPack.sign(newer), TestPack.files(dir))
        assertEquals("2026-02-01+fixture", store.installedVersion())

        assertTrue(installer.rollback())
        assertEquals(TestPack.VERSION, store.installedVersion())
        assertEquals(TestPack.PROGRAMS_VERSION, store.installedVersion(DataPackInfo.PROGRAMS))
    }
}
