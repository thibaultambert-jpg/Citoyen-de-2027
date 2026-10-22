package fr.citoyende2027.llm.engine

import fr.citoyende2027.rag.answer.GenerationRequest
import fr.citoyende2027.rag.answer.GenerationResult
import fr.citoyende2027.rag.answer.GenerationStats
import java.io.ByteArrayOutputStream

/**
 * A model loaded by llama.cpp (memory-mapped file). Blocking calls, confined to one thread by the
 * caller; [requestStop] may be called from any thread.
 */
class LlamaModel private constructor(private val handle: Long) : AutoCloseable {
    @Volatile
    private var closed = false

    /** [system] and [user] formatted with the chat template of the file; null if the template is unsupported. */
    fun format(system: String, user: String): String? =
        LlamaNative.formatChat(handle, system.toByteArray(), user.toByteArray())?.toString(Charsets.UTF_8)

    fun countTokens(text: String): Int = LlamaNative.countTokens(handle, text.toByteArray())

    /** Prompt size once formatted; [Int.MAX_VALUE] when it cannot be formatted (nothing will fit). */
    fun countPromptTokens(system: String, user: String): Int =
        format(system, user)?.let { countTokens(it) }?.takeIf { it >= 0 } ?: Int.MAX_VALUE

    fun generate(request: GenerationRequest, onText: (String) -> Unit = {}): GenerationResult {
        val prompt = format(request.system, request.user)
            ?: return GenerationResult.Failed("chat template not supported")
        return generateRaw(prompt, request.grammar, request.maxTokens, onText)
    }

    /** Generation on an already formatted prompt (benchmark and tests). Greedy, temperature 0. */
    fun generateRaw(prompt: String, grammar: String, maxTokens: Int, onText: (String) -> Unit = {}): GenerationResult {
        val start = System.nanoTime()
        var firstToken = -1L
        var promptTokens = 0
        var tokens = 0
        val text = ByteArrayOutputStream()
        val sink = object : LlamaNative.TokenSink {
            override fun onPrompt(tokens: Int) {
                promptTokens = tokens
            }

            override fun onPiece(bytes: ByteArray): Boolean {
                if (firstToken < 0) firstToken = System.nanoTime()
                tokens++
                text.write(bytes)
                onText(text.toString(Charsets.UTF_8.name()))
                return true
            }
        }
        val status = LlamaNative.generate(handle, prompt.toByteArray(), grammar.toByteArray(), maxTokens, sink)
        val end = System.nanoTime()
        return when (status) {
            LlamaNative.OK -> GenerationResult.Completed(
                text = text.toString(Charsets.UTF_8.name()),
                stats = GenerationStats(
                    promptTokens = promptTokens,
                    timeToFirstTokenMs = if (firstToken < 0) -1 else (firstToken - start) / NANOS_PER_MS,
                    tokens = tokens,
                    durationMs = (end - start) / NANOS_PER_MS,
                ),
            )

            LlamaNative.STOPPED -> GenerationResult.Stopped

            else -> GenerationResult.Failed(errorName(status))
        }
    }

    /** Pooled embedding of [text], L2-normalised; null on failure. */
    fun embed(text: String): FloatArray? = LlamaNative.embed(handle, text.toByteArray())?.also { vector ->
        val norm = kotlin.math.sqrt(vector.fold(0.0) { sum, v -> sum + v * v })
        if (norm > 0) for (i in vector.indices) vector[i] = (vector[i] / norm).toFloat()
    }

    fun requestStop() {
        if (!closed) LlamaNative.requestStop(handle)
    }

    override fun close() {
        if (closed) return
        closed = true
        LlamaNative.free(handle)
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L

        /** Context size: prompt ≤ 1 500 tokens + 350 generated, within AGENTS.md §8.6 (n_ctx ≤ 3 072). */
        const val CONTEXT_SIZE = 2048

        fun load(path: String, nThreads: Int, nCtx: Int = CONTEXT_SIZE, embeddings: Boolean = false): LlamaModel? =
            LlamaNative.load(path, nCtx, nThreads, embeddings).takeIf { it != 0L }?.let(::LlamaModel)

        private fun errorName(status: Int) = when (status) {
            LlamaNative.ERR_TOKENIZE -> "tokenization failed"
            LlamaNative.ERR_CONTEXT_FULL -> "prompt too long for the context"
            LlamaNative.ERR_GRAMMAR -> "invalid grammar"
            LlamaNative.ERR_DECODE -> "decoding failed"
            else -> "error $status"
        }
    }
}
