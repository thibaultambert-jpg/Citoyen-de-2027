package fr.citoyende2027.llm.engine

import fr.citoyende2027.rag.answer.AnswerGrammar
import fr.citoyende2027.rag.answer.GenerationResult
import fr.citoyende2027.rag.answer.ModelOutput
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Runs the real JNI bridge on the JVM (Linux build of src/main/cpp) with the tiny test model of the
 * pinned llama.cpp release (stories15M, downloaded by CI with its SHA-256). Skipped without them.
 * The model writes children's stories: only the mechanics are tested here, not answer quality.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "llm.hostLib", matches = ".+")
class LlamaBridgeHostTest {
    private lateinit var model: LlamaModel

    @BeforeAll
    fun load() {
        System.load(System.getProperty("llm.hostLib"))
        LlamaNative.init(System.getProperty("java.io.tmpdir"))
        model = checkNotNull(LlamaModel.load(System.getProperty("llm.testModel"), nThreads = 2, nCtx = 512)) {
            "test model not loaded"
        }
    }

    @AfterAll
    fun close() = model.close()

    @Test
    fun `a missing file is refused`() {
        assertNull(LlamaModel.load("/nonexistent/model.gguf", nThreads = 1))
    }

    @Test
    fun `tokens are counted`() {
        assertTrue(model.countTokens("Once upon a time") in 2..10)
    }

    @Test
    fun `the grammar constrains the output exactly`() {
        val grammar = "root ::= \"{\\\"answerable\\\": \" (\"true\" | \"false\") \"}\"\n"
        val result = assertInstanceOf(
            GenerationResult.Completed::class.java,
            model.generateRaw("Once upon a time", grammar, maxTokens = 40),
        )
        assertTrue(result.text == "{\"answerable\": true}" || result.text == "{\"answerable\": false}", result.text)
        assertTrue(result.stats.promptTokens > 0 && result.stats.tokens > 0)
    }

    @Test
    fun `the answer grammar yields JSON the app can decode when generation ends`() {
        val result = assertInstanceOf(
            GenerationResult.Completed::class.java,
            model.generateRaw("Once upon a time", AnswerGrammar.gbnf(listOf("S1", "S2")), maxTokens = 200),
        )
        if (result.stats.tokens < 200) assertNotNull(ModelOutput.parse(result.text), result.text)
    }

    @Test
    fun `reusing the cached prompt prefix gives the same greedy output`() {
        val grammar = "root ::= [a-z ]{1,40}\n"
        val first = model.generateRaw(
            "Once upon a time there was",
            grammar,
            maxTokens = 20,
        ) as GenerationResult.Completed
        val second = model.generateRaw(
            "Once upon a time there was",
            grammar,
            maxTokens = 20,
        ) as GenerationResult.Completed
        val other = model.generateRaw(
            "Once upon a time there lived",
            grammar,
            maxTokens = 20,
        ) as GenerationResult.Completed
        val again = model.generateRaw(
            "Once upon a time there was",
            grammar,
            maxTokens = 20,
        ) as GenerationResult.Completed
        assertEquals(first.text, second.text)
        assertEquals(first.text, again.text)
        assertTrue(other.text.isNotEmpty())
    }

    @Test
    fun `stop interrupts the generation`() {
        val result = model.generateRaw("Once upon a time", "root ::= [a-z ]+\n", maxTokens = 100) {
            model.requestStop()
        }
        assertEquals(GenerationResult.Stopped, result)
    }

    @Test
    fun `a prompt larger than the context is refused`() {
        val long = List(600) { "story" }.joinToString(" ")
        assertEquals(
            GenerationResult.Failed("prompt too long for the context"),
            model.generateRaw(long, "root ::= \"x\"\n", maxTokens = 10),
        )
    }

    @Test
    fun `an invalid grammar is refused`() {
        assertEquals(
            GenerationResult.Failed("invalid grammar"),
            model.generateRaw("Once", "root ::= (", maxTokens = 10),
        )
    }
}
