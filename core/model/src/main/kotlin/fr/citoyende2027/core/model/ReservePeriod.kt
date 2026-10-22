package fr.citoyende2027.core.model

import java.time.LocalDate

/**
 * Electoral reserve period (AGENTS.md §10): when a legal advisor recommends it, the published `core`
 * pack asks the app to hide campaign content (programmes, speeches) until a later pack lifts it.
 * Official records (mandates, recorded votes, legal texts) stay available.
 */
data class ReservePeriod(val hideCampaignContent: Boolean, val since: LocalDate?) {
    companion object {
        val NONE = ReservePeriod(hideCampaignContent = false, since = null)

        /** Parses the compact JSON of the pack meta (`{"hide_campaign_content": …, "since": …}`). */
        fun parse(json: String?): ReservePeriod {
            if (json.isNullOrBlank()) return NONE
            val hide = Regex("\"hide_campaign_content\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1)
            val since = Regex("\"since\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(json)?.groupValues?.get(1)
            return ReservePeriod(hide == "true", since?.let(LocalDate::parse))
        }
    }
}
