package fr.citoyende2027.llm.assistant

import androidx.work.WorkInfo
import fr.citoyende2027.llm.device.Limitation
import fr.citoyende2027.llm.model.ModelSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssistantStatusTest {
    private val spec = ModelSpec("https://example.invalid/m.gguf", "a".repeat(64), 100)

    private fun status(
        spec: ModelSpec = this.spec,
        installed: Boolean = false,
        limitations: Set<Limitation> = emptySet(),
        work: WorkInfo.State? = null,
        verifying: Boolean = false,
        importFailed: Boolean = false,
    ) = assistantStatus(spec, installed, limitations, work, 40, verifying, importFailed, enabled = true)

    @Test
    fun `every state of the assistant`() {
        assertEquals(AssistantStatus.NotConfigured, status(spec = ModelSpec("", "", 0)))
        assertEquals(
            AssistantStatus.Unsupported(setOf(Limitation.MEMORY)),
            status(limitations = setOf(Limitation.MEMORY), installed = true),
        )
        assertEquals(AssistantStatus.Ready(enabled = true), status(installed = true))
        assertEquals(
            AssistantStatus.Unsupported(setOf(Limitation.STORAGE)),
            status(limitations = setOf(Limitation.STORAGE)),
        )
        assertEquals(
            AssistantStatus.Downloading(40, 100, waitingForWifi = false),
            status(work = WorkInfo.State.RUNNING),
        )
        assertEquals(
            AssistantStatus.Downloading(40, 100, waitingForWifi = true),
            status(work = WorkInfo.State.ENQUEUED),
        )
        assertEquals(AssistantStatus.Failed(100), status(work = WorkInfo.State.FAILED))
        assertEquals(AssistantStatus.Failed(100), status(importFailed = true))
        assertEquals(AssistantStatus.Verifying, status(verifying = true))
        assertEquals(AssistantStatus.NotInstalled(100), status(work = WorkInfo.State.CANCELLED))
    }
}
