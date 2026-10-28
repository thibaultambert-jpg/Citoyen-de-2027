package fr.citoyende2027.llm.assistant

import android.net.Uri
import fr.citoyende2027.rag.answer.GroundedAnswer
import fr.citoyende2027.rag.answer.SourceCandidate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The local assistant as the screens see it (replaced by a fake in tests). */
interface Assistant {
    val status: Flow<AssistantStatus>

    fun download()

    fun cancelDownload()

    /** false when the file is not the expected model (SHA-256). */
    suspend fun import(uri: Uri): Boolean

    fun remove()

    fun setEnabled(enabled: Boolean)

    suspend fun answer(question: String, sources: List<SourceCandidate>, onText: (String) -> Unit = {}): GroundedAnswer
}

internal class DefaultAssistant @Inject constructor(
    private val manager: AssistantManager,
    private val local: LocalAssistant,
) : Assistant {
    override val status: Flow<AssistantStatus> get() = manager.status

    override fun download() = manager.download()

    override fun cancelDownload() = manager.cancelDownload()

    override suspend fun import(uri: Uri): Boolean = manager.import(uri)

    override fun remove() = manager.remove()

    override fun setEnabled(enabled: Boolean) = manager.setEnabled(enabled)

    override suspend fun answer(question: String, sources: List<SourceCandidate>, onText: (String) -> Unit) =
        local.answer(question, sources, onText)
}
