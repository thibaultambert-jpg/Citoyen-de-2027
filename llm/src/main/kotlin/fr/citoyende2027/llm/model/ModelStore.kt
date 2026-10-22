package fr.citoyende2027.llm.model

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** The model file chosen by a human (AGENTS.md §9); [isConfigured] is false in a build without one. */
data class ModelSpec(val url: String, val sha256: String, val sizeBytes: Long) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") && sha256.matches(Regex("[0-9a-fA-F]{64}")) && sizeBytes > 0
}

/**
 * The model in internal storage. A download or an import is written to a temporary file, checked
 * against [spec]'s SHA-256, then renamed into place (atomic activation). The verified hash is recorded
 * next to the file so that it is not recomputed at each start.
 */
class ModelStore(private val directory: File, val spec: ModelSpec) {
    val modelFile: File get() = File(directory, MODEL_FILE)
    val partFile: File get() = File(directory, "$MODEL_FILE.part")
    private val verifiedFile: File get() = File(directory, "$MODEL_FILE.sha256")

    /** The verified model, if it is present and matches the configured file. */
    fun installed(): File? = modelFile.takeIf {
        it.isFile && it.length() == spec.sizeBytes &&
            verifiedFile.isFile && verifiedFile.readText().trim().equals(spec.sha256, ignoreCase = true)
    }

    /** Checks the temporary file and activates it; a file with another hash is deleted. */
    fun activatePart(): Boolean {
        val part = partFile
        if (!part.isFile || part.length() != spec.sizeBytes || !sha256(part).equals(spec.sha256, ignoreCase = true)) {
            part.delete()
            return false
        }
        directory.mkdirs()
        verifiedFile.delete()
        if (!part.renameTo(modelFile)) return false
        verifiedFile.writeText(spec.sha256.lowercase())
        return true
    }

    /** Manual import (Storage Access Framework): copied, then verified like a download. */
    fun import(input: InputStream): Boolean {
        directory.mkdirs()
        partFile.outputStream().use { output -> input.use { it.copyTo(output, BUFFER) } }
        return activatePart()
    }

    fun remove() {
        listOf(modelFile, partFile, verifiedFile).forEach { it.delete() }
    }

    companion object {
        const val MODEL_FILE = "llm.gguf"
        private const val BUFFER = 1 shl 20

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
