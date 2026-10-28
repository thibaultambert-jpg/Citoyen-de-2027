package fr.citoyende2027.core.ui

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateFormatTest {
    @Test
    fun `dates are formatted JJ MM AAAA`() {
        assertEquals("05/03/2027", LocalDate.of(2027, 3, 5).toFrenchDate())
    }
}
