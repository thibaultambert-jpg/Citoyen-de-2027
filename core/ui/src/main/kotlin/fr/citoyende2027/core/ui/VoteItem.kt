package fr.citoyende2027.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.VotePosition
import fr.citoyende2027.core.model.positionCategory

/**
 * A recorded vote: same neutral style for every position, carried by its text (AGENTS.md §10);
 * ballot subject, type and official link always shown.
 */
@Composable
fun VoteItem(vote: Vote, modifier: Modifier = Modifier, themeLabels: Map<ThemeId, String> = emptyMap()) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${vote.ballotDate.toFrenchDate()} · ${stringResource(vote.body.labelRes)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(vote.ballotTitle, style = MaterialTheme.typography.bodyLarge)
        vote.ballotSubject?.let {
            Text(
                text = stringResource(R.string.core_ui_vote_subject, stringResource(it.labelRes)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        vote.ballotType?.let {
            Text(stringResource(R.string.core_ui_vote_type, it), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.core_ui_vote_position, positionLabel(vote.positionCategory, vote)),
            style = MaterialTheme.typography.titleSmall,
        )
        if (themeLabels.isNotEmpty()) ThemesLine(vote.themes, themeLabels)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { uriHandler.openUri(vote.ballotUrl) }) {
                Text(stringResource(R.string.core_ui_vote_official_link))
            }
            SourceButton(vote.source)
        }
    }
}

/** Label of a position category; the published value when the category is unknown. */
@Composable
fun positionLabel(position: VotePosition, vote: Vote? = null): String =
    position.labelRes?.let { stringResource(it) } ?: vote?.position ?: stringResource(R.string.core_ui_position_other)
