package fr.citoyende2027.rag.answer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GBNF grammar of the answer (AGENTS.md §8.7): `{"answerable": bool, "claims": [{"text", "sources"}]}`,
 * at most [MAX_CLAIMS] claims, each citing 1 to [sourceLabels] existing labels. String rules follow
 * `grammars/json.gbnf` of the pinned llama.cpp version.
 */
object AnswerGrammar {
    const val MAX_CLAIMS = 5

    fun gbnf(sourceLabels: List<String>): String {
        require(sourceLabels.isNotEmpty()) { "A grammar needs at least one source" }
        val sources = sourceLabels.joinToString(" | ") { "\"\\\"$it\\\"\"" }
        val more = if (sourceLabels.size > 1) " (\",\" ws source){0,${sourceLabels.size - 1}}" else ""
        return """
            root ::= "{" ws "\"answerable\":" ws boolean "," ws "\"claims\":" ws claims "}" ws
            boolean ::= ("true" | "false") ws
            claims ::= "[" ws (claim ("," ws claim){0,${MAX_CLAIMS - 1}})? "]" ws
            claim ::= "{" ws "\"text\":" ws text "," ws "\"sources\":" ws sources "}" ws
            sources ::= "[" ws source$more "]" ws
            source ::= ($sources) ws
            text ::= "\"" char+ "\"" ws
            char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4})
            ws ::= | " " | "\n" [ \t]{0,20}
        """.trimIndent() + "\n"
    }
}

@Serializable
data class ModelClaim(val text: String, val sources: List<String>)

/** The model output, decoded strictly; anything else is invalid (one retry, then abstention). */
@Serializable
data class ModelOutput(val answerable: Boolean, val claims: List<ModelClaim>) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        fun parse(text: String): ModelOutput? = runCatching {
            json.decodeFromString(serializer(), text.trim())
        }.getOrNull()
    }
}
