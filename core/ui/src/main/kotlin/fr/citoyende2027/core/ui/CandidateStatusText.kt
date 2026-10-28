package fr.citoyende2027.core.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import fr.citoyende2027.core.model.Candidate

/** "Candidature déclarée le JJ/MM/AAAA": same wording pattern for every candidate. */
@Composable
fun CandidateStatusText(
    candidate: Candidate,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = stringResource(candidate.status.labelRes, candidate.statusDate.toFrenchDate()),
        style = style,
        modifier = modifier,
    )
}
