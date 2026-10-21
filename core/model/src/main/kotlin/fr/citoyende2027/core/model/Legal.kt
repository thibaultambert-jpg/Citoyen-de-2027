package fr.citoyende2027.core.model

import java.time.LocalDate

/** A text of the legal corpus (AGENTS.md §8): Constitution and the texts listed in the pipeline. */
data class LegalText(
    val id: String,
    val title: String,
    /** Display position set by the pipeline input (inputs/legal/texts.yaml). */
    val position: Int,
    /** Official consolidated text (Légifrance), retrieval date, licence and pack version. */
    val source: SourceRef,
)

/** One article, verbatim, as in force on the build date of the pack. One article = one chunk. */
data class LegalArticle(
    val id: String,
    val textId: String,
    /** Position in the text (reading order). */
    val ordinal: Int,
    /** Article number as published ("7", "L. 52-11"), empty when the article has none. */
    val number: String,
    /** Titles, chapters and sections above the article, outermost first. */
    val headingPath: List<String>,
    val text: String,
    /** Start date of the version in force, when the source gives one. */
    val inForceSince: LocalDate?,
    /** Official page of the article. */
    val url: String,
)

/** Content of the `legal` pack. */
data class LegalPackContent(val info: DataPackInfo, val texts: List<LegalText>, val articles: List<LegalArticle>) {
    init {
        require(info.id == DataPackInfo.LEGAL) { "Not a legal pack: ${info.id}" }
        val ids = texts.map { it.id }.toSet()
        require(ids.size == texts.size) { "Duplicate legal text ids" }
        require(articles.all { it.textId in ids }) { "Article of an unknown text" }
        require(articles.map { it.id }.toSet().size == articles.size) { "Duplicate article ids" }
    }
}

/** A search result: the article, its text, and a verbatim excerpt around the first match. */
data class LegalSearchHit(val article: LegalArticle, val text: LegalText, val excerpt: String, val score: Double)
