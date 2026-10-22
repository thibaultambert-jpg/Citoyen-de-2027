package fr.citoyende2027.llm.service

/** Messages between the app process and [LlmService] (":llm" process), over a Messenger. */
internal object LlmProtocol {
    // App → service. arg1 = request id.
    const val COUNT = 1
    const val GENERATE = 2
    const val STOP = 3

    // Service → app. arg1 = request id.
    const val COUNTED = 101
    const val PROGRESS = 102
    const val DONE = 103

    const val KEY_SYSTEM = "system"
    const val KEY_USER = "user"
    const val KEY_GRAMMAR = "grammar"
    const val KEY_MAX_TOKENS = "maxTokens"

    /** Benchmark only (debug builds): another model file in the app's model directory. */
    const val KEY_MODEL_FILE = "modelFile"

    const val KEY_COUNT = "count"
    const val KEY_TEXT = "text"
    const val KEY_STATUS = "status"
    const val KEY_ERROR = "error"
    const val KEY_PROMPT_TOKENS = "promptTokens"
    const val KEY_TTFT_MS = "ttftMs"
    const val KEY_TOKENS = "tokens"
    const val KEY_DURATION_MS = "durationMs"
    const val KEY_PEAK_RSS_KB = "peakRssKb"
    const val KEY_CPU_FEATURES = "cpuFeatures"
    const val KEY_THREADS = "threads"

    const val STATUS_COMPLETED = 0
    const val STATUS_STOPPED = 1
    const val STATUS_FAILED = 2

    // Failure reasons understood by the app (KEY_ERROR).
    const val ERROR_NO_MODEL = "no_model"
    const val ERROR_LOAD = "load_failed"
    const val ERROR_THERMAL = "thermal"
    const val ERROR_BUSY = "busy"
}
