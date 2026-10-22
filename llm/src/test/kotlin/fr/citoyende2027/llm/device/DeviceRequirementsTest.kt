package fr.citoyende2027.llm.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceRequirementsTest {
    @Test
    fun `memory and storage prerequisites`() {
        val gb = 1_000_000_000L
        assertEquals(emptySet<Limitation>(), DeviceRequirements.limitations(8 * gb, 10 * gb, modelInstalled = false))
        assertEquals(setOf(Limitation.MEMORY), DeviceRequirements.limitations(4 * gb, 10 * gb, modelInstalled = false))
        assertEquals(setOf(Limitation.STORAGE), DeviceRequirements.limitations(8 * gb, 3 * gb, modelInstalled = false))
        // Once the model is installed, free space no longer matters.
        assertEquals(emptySet<Limitation>(), DeviceRequirements.limitations(8 * gb, 1 * gb, modelInstalled = true))
    }

    @Test
    fun `threads are the cores outside the slowest cluster`() {
        // 4 efficiency + 3 performance + 1 prime core.
        assertEquals(4, PerformanceCores.count(listOf(1800L, 1800, 1800, 1800, 2400, 2400, 2400, 3200), 8))
        // A single cluster: half of the cores.
        assertEquals(4, PerformanceCores.count(List(8) { 2000L }, 8))
        // Unknown frequencies: half of the processors, at least one.
        assertEquals(3, PerformanceCores.count(listOf(null, 2000L, 2000, 2000, 2000, 2000), 6))
        assertEquals(1, PerformanceCores.count(listOf(null), 1))
    }
}
