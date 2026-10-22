package fr.citoyende2027.feature.ask

import androidx.compose.ui.test.junit4.createComposeRule
import fr.citoyende2027.core.data.repository.AnswerGroup
import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.LegalPassage
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.FictionalLegal
import fr.citoyende2027.core.uitesting.TALL_WINDOW
import fr.citoyende2027.core.uitesting.assertAccessible
import fr.citoyende2027.llm.assistant.AssistantStatus
import fr.citoyende2027.rag.answer.AnswerSource
import fr.citoyende2027.rag.answer.GroundedAnswer
import fr.citoyende2027.rag.answer.VerifiedClaim
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AGENTS.md §11 on the question screen: assistant card, passages and a verified generated answer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_WINDOW)
class AskAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = AssistantActions({}, {}, {}, {}, {}, {}, {})
    private val article = FictionalLegal.articles[1]
    private val source = AnswerSource("S1", article.id, "Texte fictif A, article 1", article.text, truncated = false)

    private val answered = AskUiState(
        candidates = FictionalCandidates.all,
        question = "Selon quelle règle a lieu l'élection fictive ?",
        answer = AskAnswer.Passages(
            listOf(AnswerGroup(null, listOf(LegalPassage(article, FictionalLegal.textA)), emptyList())),
        ),
        assistant = AssistantStatus.Ready(enabled = true),
        ai = AiAnswer.Done(
            GroundedAnswer.Answered(
                claims = listOf(VerifiedClaim("L'élection fictive a lieu selon une règle fictive.", listOf(source))),
                sources = listOf(source),
                rejected = emptyList(),
                stats = null,
            ),
        ),
    )

    private val notInstalled =
        AskUiState(candidates = FictionalCandidates.all, assistant = AssistantStatus.NotInstalled(2_000_000_000))

    @Test
    fun answerAtDefaultSize() = check(answered, 1f)

    @Test
    fun answerAtLargestFontSize() = check(answered, 2f)

    @Test
    fun activationAtLargestFontSize() = check(notInstalled, 2f)

    private fun check(state: AskUiState, fontScale: Float) = composeRule.assertAccessible(fontScale) {
        CitoyenTheme { AskContent(state, {}, {}, {}, {}, actions) }
    }
}
