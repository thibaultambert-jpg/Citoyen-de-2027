package fr.citoyende2027.core.data.pack

import com.google.crypto.tink.subtle.Ed25519Verify
import java.io.File
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64

class PackVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Checks the Ed25519 signature of the manifest, then size and sha256 of each pack file, before any
 * activation (AGENTS.md §5). Tink is used because java.security has no Ed25519 below API 33.
 */
class PackVerifier(publicKeyBase64: String) {
    private val verifier = Ed25519Verify(Base64.getDecoder().decode(publicKeyBase64.trim()))

    fun verifyManifest(manifest: ByteArray, signatureFile: ByteArray): PackManifest {
        val signature = try {
            Base64.getDecoder().decode(signatureFile.decodeToString().trim())
        } catch (error: IllegalArgumentException) {
            throw PackVerificationException("Malformed signature", error)
        }
        try {
            verifier.verify(signature, manifest)
        } catch (error: GeneralSecurityException) {
            throw PackVerificationException("Manifest signature does not match the embedded key", error)
        }
        return PackManifest.parse(manifest)
    }

    fun verifyFile(file: File, entry: PackEntry) {
        if (file.length() != entry.size) {
            throw PackVerificationException("Size of ${entry.file} is ${file.length()}, expected ${entry.size}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(entry.sha256, ignoreCase = true)) {
            throw PackVerificationException("sha256 of ${entry.file} does not match the signed manifest")
        }
    }
}
