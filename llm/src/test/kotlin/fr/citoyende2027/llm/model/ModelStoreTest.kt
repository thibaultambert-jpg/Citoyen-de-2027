package fr.citoyende2027.llm.model

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelStoreTest {
    private val content = "contenu fictif du modèle".toByteArray()
    private val directory: File = Files.createTempDirectory("models").toFile()

    private fun store(sha: String = sha(content)) =
        ModelStore(directory, ModelSpec("https://example.invalid/model.gguf", sha, content.size.toLong()))

    private fun sha(bytes: ByteArray): String {
        val file = File.createTempFile("sha", ".bin").apply { writeBytes(bytes) }
        return ModelStore.sha256(file)
    }

    @Test
    fun `a file with the expected hash is activated atomically and recognised`() {
        val store = store()
        assertNull(store.installed())
        assertTrue(store.import(content.inputStream()))
        assertEquals(store.modelFile, store.installed())
        assertFalse(store.partFile.exists())
        store.remove()
        assertNull(store.installed())
    }

    @Test
    fun `a file with another hash is refused and deleted`() {
        val store = store(sha = "0".repeat(64))
        assertFalse(store.import(content.inputStream()))
        assertNull(store.installed())
        assertFalse(store.partFile.exists())
        assertFalse(store.modelFile.exists())
    }

    @Test
    fun `a model verified for another configuration is not used`() {
        assertTrue(store().import(content.inputStream()))
        assertNull(store(sha = "f".repeat(64)).installed())
    }

    @Test
    fun `a build without model URL or hash is not configured`() {
        assertFalse(ModelSpec("", "", 0).isConfigured)
        assertFalse(ModelSpec("http://example.invalid/m.gguf", "a".repeat(64), 1).isConfigured)
        assertTrue(ModelSpec("https://example.invalid/m.gguf", "a".repeat(64), 1).isConfigured)
    }
}
