package fr.citoyende2027.feature.ask

import fr.citoyende2027.core.data.repository.AnswerGroup
import fr.citoyende2027.rag.answer.SourceCandidate

/**
 * Passages given to the local model, in rank order, with a title naming their document (and, for a
 * programme, its author, so that each position can be attributed: "Selon le programme publié de …").
 */
internal fun AnswerGroup.sourceCandidates(): List<SourceCandidate> = legal.map { passage ->
    val number = passage.article.number
    val title = if (number.any(
            Char::isDigit,
        )
    ) {
        "${passage.text.title}, article $number"
    } else {
        "${passage.text.title}, $number"
    }
    SourceCandidate(passage.article.id, title, passage.article.text)
} + programs.map { passage ->
    val title = buildString {
        append("Programme publié de ${candidate?.displayName.orEmpty()}")
        passage.document?.let { append(", ${it.title}") }
        passage.chunk.page?.let { append(", page $it") }
    }
    SourceCandidate(passage.chunk.id, title, passage.chunk.text)
}
