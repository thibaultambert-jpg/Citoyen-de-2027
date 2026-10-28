package fr.citoyende2027.rag

import fr.citoyende2027.core.model.LegalArticle
import fr.citoyende2027.core.model.LegalText
import fr.citoyende2027.core.model.ProgramChunk

/** Indexed text of an article: text title, headings, number and verbatim body. Same in app and eval. */
fun LegalArticle.asPassage(text: LegalText): Passage = Passage(
    id = id,
    corpus = Corpus.LEGAL,
    candidateId = null,
    text = listOf(text.title, headingPath.joinToString(" "), "article $number", this.text).joinToString("\n"),
)

/** Indexed text of a programme excerpt: headings and verbatim text. */
fun ProgramChunk.asPassage(): Passage = Passage(
    id = id,
    corpus = Corpus.PROGRAM,
    candidateId = candidateId,
    text = (headingPath + text).joinToString("\n"),
)
