package fr.citoyende2027.llm.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.llm.service.LlmClient
import fr.citoyende2027.rag.answer.GroundedAnswer
import fr.citoyende2027.rag.answer.GroundedAnswerer
import fr.citoyende2027.rag.answer.SourceCandidate
import javax.inject.Inject
import javax.inject.Singleton

/** The answer chain with the local model and the system prompt `llm/prompts/answer_fr.txt`. */
@Singleton
class LocalAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: LlmClient,
) {
    private val systemPrompt: String by lazy {
        context.assets.open(SYSTEM_PROMPT).bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    suspend fun answer(
        question: String,
        sources: List<SourceCandidate>,
        onText: (String) -> Unit = {
        },
    ): GroundedAnswer = GroundedAnswerer(client, systemPrompt).answer(question, sources, onText)

    private companion object {
        const val SYSTEM_PROMPT = "answer_fr.txt"
    }
}
