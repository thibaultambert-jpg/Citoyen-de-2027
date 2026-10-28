package fr.citoyende2027.core.model

import java.text.Collator
import java.util.Locale

@JvmInline
value class ThemeId(val value: String) {
    val isUnknown: Boolean get() = value == UNKNOWN

    companion object {
        /** Items matched by no rule (AGENTS.md §7.4). */
        const val UNKNOWN = "inconnu"
    }
}

/** A theme of the fixed taxonomy (pipeline/inputs/themes.yaml). */
data class Theme(val id: ThemeId, val label: String)

enum class TagMethod {
    /** Explicit keyword rule of the pipeline. */
    RULE,

    /** Decision recorded by a human. */
    MANUAL,
}

enum class TagConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

/** A theme attached to a vote or a programme excerpt, with how it was attached. */
data class ThemeTag(val themeId: ThemeId, val method: TagMethod, val confidence: TagConfidence)

/** Alphabetical (French collation), the "undetermined" theme last: no theme is put forward. */
fun List<Theme>.alphabetical(): List<Theme> {
    val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
    return sortedWith(compareBy<Theme> { it.id.isUnknown }.thenBy(collator) { it.label }.thenBy { it.id.value })
}
