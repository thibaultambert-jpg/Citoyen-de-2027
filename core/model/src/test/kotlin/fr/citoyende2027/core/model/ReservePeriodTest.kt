package fr.citoyende2027.core.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReservePeriodTest {
    @Test
    fun `pack meta is parsed, absent or off means nothing hidden`() {
        assertEquals(ReservePeriod.NONE, ReservePeriod.parse(null))
        assertEquals(ReservePeriod.NONE, ReservePeriod.parse("""{"hide_campaign_content":false,"since":null}"""))
        assertEquals(
            ReservePeriod(true, LocalDate.of(2026, 1, 20)),
            ReservePeriod.parse("""{"hide_campaign_content":true,"since":"2026-01-20"}"""),
        )
    }
}
