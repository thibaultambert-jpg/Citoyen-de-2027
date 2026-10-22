package fr.citoyende2027.core.uitesting

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp

/** Minimum touch target (Material / Android accessibility guidelines). */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Checks of AGENTS.md §11 on the composed screen: every enabled clickable element has a label that
 * TalkBack can read and a touch target of at least 48 dp, and the screen has at least one heading.
 * Returns the problems found, empty when the screen passes.
 */
fun SemanticsNodeInteractionsProvider.accessibilityProblems(): List<String> {
    val root = onNode(isRoot()).fetchSemanticsNode()
    val density = root.layoutInfo.density
    val minPx = with(density) { MIN_TOUCH_TARGET.toPx() } - 1f
    val nodes = root.descendants()
    val problems = mutableListOf<String>()
    nodes.filter { node ->
        SemanticsActions.OnClick in node.config && SemanticsProperties.Disabled !in node.config
    }.forEach { node ->
        val label = node.label()
        if (label.isBlank()) problems += "Clickable element without a label (node ${node.id})"
        val bounds = node.touchBoundsInRoot
        if (bounds.width < minPx || bounds.height < minPx) {
            problems += "Touch target smaller than 48 dp: '$label' (${bounds.width} x ${bounds.height} px)"
        }
    }
    if (nodes.none { SemanticsProperties.Heading in it.config }) problems += "No heading on the screen"
    return problems
}

private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }

/** What TalkBack reads: content description, then text, merged with the node's descendants. */
private fun SemanticsNode.label(): String {
    val own = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.StateDescription),
    )
    return (own + children.map { it.label() }).joinToString(" ").trim()
}
