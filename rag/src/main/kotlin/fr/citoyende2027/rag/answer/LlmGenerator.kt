package fr.citoyende2027.rag.answer

/**
 * One constrained generation (AGENTS.md §8, steps 6–7). [system] and [user] are formatted with the chat
 * template of the model file itself; [grammar] is a GBNF grammar that the sampler enforces.
 */
data class GenerationRequest(
    val system: String,
    val user: String,
    val grammar: String,
    val maxTokens: Int = MAX_TOKENS,
    val temperature: Float = 0f,
) {
    companion object {
        /** AGENTS.md §9. */
        const val MAX_TOKENS = 350
    }
}

/** Measured on the device: time to first token, generated tokens and total generation time. */
data class GenerationStats(val promptTokens: Int, val timeToFirstTokenMs: Long, val tokens: Int, val durationMs: Long) {
    val tokensPerSecond: Double get() = if (durationMs <= 0) 0.0 else tokens * 1000.0 / durationMs
}

sealed interface GenerationResult {
    data class Completed(val text: String, val stats: GenerationStats) : GenerationResult

    /** Stopped by the user, or by the device (thermal status, memory pressure). */
    data object Stopped : GenerationResult

    data class Failed(val reason: String) : GenerationResult
}

/** The local model, wherever it runs (a separate process on Android, a fake in tests). */
interface LlmGenerator {
    /** Tokens of [system] and [user] once formatted with the model's chat template. */
    suspend fun countPromptTokens(system: String, user: String): Int

    /** [onText] receives the text generated so far, for the progress indicator only: it is never shown. */
    suspend fun generate(request: GenerationRequest, onText: (String) -> Unit = {}): GenerationResult
}
