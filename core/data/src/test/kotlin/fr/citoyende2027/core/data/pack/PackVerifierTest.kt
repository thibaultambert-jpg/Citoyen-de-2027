package fr.citoyende2027.core.data.pack

import com.google.crypto.tink.subtle.Ed25519Sign
import java.io.File
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class PackVerifierTest {
    private val verifier = PackVerifier(TestPack.publicKey)

    @Test
    fun `accepts the manifest signed by the pipeline`() {
        val manifest = verifier.verifyManifest(TestPack.manifest, TestPack.signature)
        assertEquals(2, manifest.schemaVersion)
        assertEquals(TestPack.VERSION, manifest.pack("core")?.version)
    }

    @Test
    fun `kotlin and python produce the same Ed25519 signature`() {
        assertEquals(TestPack.signature.decodeToString(), TestPack.sign(TestPack.manifest).decodeToString())
    }

    @Test
    fun `rejects a modified manifest`() {
        val tampered = TestPack.manifest.decodeToString().replace("fixture", "fixturf").toByteArray()
        assertThrows<PackVerificationException> { verifier.verifyManifest(tampered, TestPack.signature) }
    }

    @Test
    fun `rejects another key`() {
        val otherKey = Ed25519Sign.KeyPair.newKeyPair().publicKey
        val other = PackVerifier(Base64.getEncoder().encodeToString(otherKey))
        assertThrows<PackVerificationException> { other.verifyManifest(TestPack.manifest, TestPack.signature) }
    }

    @Test
    fun `checks size and sha256 of the pack file`(@TempDir dir: File) {
        val entry = verifier.verifyManifest(TestPack.manifest, TestPack.signature).pack("core")!!
        verifier.verifyFile(TestPack.packFile(dir), entry)
        val corrupted = TestPack.pack.copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertThrows<PackVerificationException> { verifier.verifyFile(TestPack.packFile(dir, corrupted), entry) }
    }
}
