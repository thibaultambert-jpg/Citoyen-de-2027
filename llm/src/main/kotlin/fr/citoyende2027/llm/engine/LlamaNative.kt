package fr.citoyende2027.llm.engine

/**
 * JNI functions of `libcitoyen_llm.so` (src/main/cpp/citoyen_llm.cpp). Text crosses the boundary as
 * UTF-8 bytes. Calls on one handle must not overlap, except [requestStop].
 */
internal object LlamaNative {
    /** Receives the prompt size, then each generated piece; returns false to stop. */
    interface TokenSink {
        fun onPrompt(tokens: Int)

        fun onPiece(bytes: ByteArray): Boolean
    }

    const val OK = 0
    const val STOPPED = 1
    const val ERR_TOKENIZE = -1
    const val ERR_CONTEXT_FULL = -2
    const val ERR_GRAMMAR = -3
    const val ERR_DECODE = -4

    /** Loads the CPU variants found in [nativeLibDir]; returns the CPU features of the variant in use. */
    external fun init(nativeLibDir: String): String

    /** 0 when the file cannot be loaded. */
    external fun load(path: String, nCtx: Int, nThreads: Int, embeddings: Boolean): Long

    external fun free(handle: Long)

    /** The prompt formatted with the chat template of the model file; null without a supported template. */
    external fun formatChat(handle: Long, system: ByteArray, user: ByteArray): ByteArray?

    external fun countTokens(handle: Long, text: ByteArray): Int

    external fun requestStop(handle: Long)

    external fun generate(handle: Long, prompt: ByteArray, grammar: ByteArray, maxTokens: Int, sink: TokenSink): Int

    external fun embed(handle: Long, text: ByteArray): FloatArray?
}
