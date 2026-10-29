package fr.citoyende2027.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopLevelDestinationTest {
    @Test
    fun `top level destinations follow the screen order of the specification`() {
        assertEquals(
            listOf("CANDIDATES", "COMPARE", "ASK", "INSTITUTIONS"),
            TopLevelDestination.entries.map { it.name },
        )
    }

    @Test
    fun `each destination route matches its route class`() {
        TopLevelDestination.entries.forEach { assertEquals(it.routeClass, it.route::class) }
    }
}
