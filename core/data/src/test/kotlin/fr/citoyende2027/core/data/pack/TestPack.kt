package fr.citoyende2027.core.data.pack

import com.google.crypto.tink.subtle.Ed25519Sign
import fr.citoyende2027.core.model.CorePackContent
import fr.citoyende2027.core.testing.FictionalCandidates
import java.io.File
import java.util.Base64

/**
 * Files written by `make test-pack` (pipeline/citoyen_pipeline/testpack.py): a fictional pack signed
 * by the pipeline with a TEST key derived from the public seed 0x00..0x1f.
 */
object TestPack {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResource("/packs/$name")) { "Missing test resource $name" }.readBytes()

    val manifest: ByteArray get() = resource("manifest.json")
    val signature: ByteArray get() = resource("manifest.json.sig")
    val pack: ByteArray get() = resource("core.sqlite")
    val publicKey: String get() = resource("test_public_key.txt").decodeToString().trim()
    const val VERSION = "2026-01-15+fixture"

    private val signer = Ed25519Sign(ByteArray(32) { it.toByte() })

    fun sign(manifest: ByteArray): ByteArray =
        (Base64.getEncoder().encodeToString(signer.sign(manifest)) + "\n").toByteArray()

    fun packFile(dir: File, bytes: ByteArray = pack): File = File(dir, "download.sqlite").apply { writeBytes(bytes) }

    /** Content a reader would return for a pack of [version] (the real reader is tested separately). */
    fun content(version: String = VERSION): CorePackContent = FictionalCandidates.corePack.copy(
        info = FictionalCandidates.packInfo.copy(version = version, isFictional = false),
    )
}
