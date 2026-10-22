package fr.citoyende2027.core.testing

import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalPackContent
import fr.citoyende2027.core.model.LegalText
import java.time.LocalDate

/** Fictional legal corpus (AGENTS.md §2.6): no real article text, number or identifier. */
object FictionalLegal {
    val info = DataPackInfo(
        id = DataPackInfo.LEGAL,
        version = "0.0.0-fixture",
        generatedOn = LocalDate.of(2026, 1, 1),
        isFictional = true,
    )

    private fun source(id: String) = FictionalCandidates.source.copy(
        name = "Texte fictif $id",
        url = "https://example.invalid/textes/$id",
    )

    val textA = LegalText(id = "fictif-texte-a", title = "Texte fictif A", position = 1, source = source("a"))
    val textB = LegalText(id = "fictif-texte-b", title = "Texte fictif B", position = 2, source = source("b"))

    val articles = listOf(
        LegalArticle(
            id = "fictif-texte-a-art-1",
            textId = textA.id,
            ordinal = 1,
            number = "",
            headingPath = listOf("Préambule fictif"),
            text = "Paragraphe fictif d’introduction.",
            inForceSince = null,
            url = "https://example.invalid/textes/a/preambule",
        ),
        LegalArticle(
            id = "fictif-texte-a-art-2",
            textId = textA.id,
            ordinal = 2,
            number = "1",
            headingPath = listOf("Titre fictif I", "Chapitre fictif 1"),
            text = "L’élection fictive a lieu selon une règle fictive.",
            inForceSince = LocalDate.of(2020, 1, 1),
            url = "https://example.invalid/textes/a/1",
            inForceUntil = LocalDate.of(2030, 1, 1),
        ),
        LegalArticle(
            id = "fictif-texte-a-art-3",
            textId = textA.id,
            ordinal = 3,
            number = "2",
            headingPath = listOf("Titre fictif I", "Chapitre fictif 2"),
            text = "Une disposition fictive sans rapport.",
            inForceSince = LocalDate.of(2021, 1, 1),
            url = "https://example.invalid/textes/a/2",
        ),
        LegalArticle(
            id = "fictif-texte-b-art-1",
            textId = textB.id,
            ordinal = 1,
            number = "L. 1",
            headingPath = listOf("Livre fictif"),
            text = "Élection fictive : élection, élection et encore élection.",
            inForceSince = LocalDate.of(2022, 1, 1),
            url = "https://example.invalid/textes/b/l1",
        ),
    )

    val pack = LegalPackContent(info, listOf(textA, textB), articles)
}
