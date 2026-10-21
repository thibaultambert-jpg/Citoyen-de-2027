package fr.citoyende2027.core.data.pack

import com.google.crypto.tink.subtle.Ed25519Sign
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.model.ProgramsPackContent
import fr.citoyende2027.core.testing.FictionalCandidates
import java.io.File
import java.util.Base64

/**
 * Files written by `make test-pack` (pipeline/citoyen_pipeline/testpack.py): fictional `core` and
 * `programs` packs signed by the pipeline with a TEST key derived from the public seed 0x00..0x1f.
 */
object TestPack {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResource("/packs/$name")) { "Missing test resource $name" }.readBytes()

    val manifest: ByteArray get() = resource("manifest.json")
    val signature: ByteArray get() = resource("manifest.json.sig")
    val pack: ByteArray get() = resource("core.sqlite")
    val programs: ByteArray get() = resource("programs.sqlite")
    val publicKey: String get() = resource("test_public_key.txt").decodeToString().trim()
    const val VERSION = "2026-01-15+fixture"
    const val PROGRAMS_VERSION = "2026-01-15+programs-fixture"

    private val signer = Ed25519Sign(ByteArray(32) { it.toByte() })

    fun sign(manifest: ByteArray): ByteArray =
        (Base64.getEncoder().encodeToString(signer.sign(manifest)) + "\n").toByteArray()

    fun packFile(dir: File, bytes: ByteArray = pack): File = File(dir, "download.sqlite").apply { writeBytes(bytes) }

    fun programsFile(dir: File, bytes: ByteArray = programs): File =
        File(dir, "download-programs.sqlite").apply { writeBytes(bytes) }

    /** Files of every pack of the manifest, as downloaded. */
    fun files(dir: File, core: ByteArray = pack) = mapOf("core" to packFile(dir, core), "programs" to programsFile(dir))

    /** Content a reader would return for a pack of [version] (the real readers are tested separately). */
    fun content(version: String = VERSION): CorePackContent = FictionalCandidates.corePack.copy(
        info = FictionalCandidates.packInfo.copy(version = version, isFictional = false),
    )

    fun programsContent(version: String = PROGRAMS_VERSION): ProgramsPackContent =
        FictionalCandidates.programsPack.copy(
            info = FictionalCandidates.programsInfo.copy(version = version, isFictional = false),
        )

    /** Reader returning fixtures; [coreVersion] decides the core version read from a file. */
    fun reader(coreVersion: (File) -> String = { VERSION }) = object : PackFileReader {
        override fun readCore(file: File) = content(coreVersion(file))

        override fun readPrograms(file: File) = programsContent()
    }
}
