package fr.citoyende2027.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons

/** Pages of the "Infos" screen (AGENTS.md §10, §11; personal use: no legal pages, 28/10/2026). */
enum class InfoPage(val title: Int, val summary: Int) {
    DATA(R.string.feature_about_page_data, R.string.feature_about_page_data_summary),
    SETTINGS(R.string.feature_about_page_settings, R.string.feature_about_page_settings_summary),
    METHODOLOGY(R.string.feature_about_page_methodology, R.string.feature_about_page_methodology_summary),
    SOURCES(R.string.feature_about_page_sources, R.string.feature_about_page_sources_summary),
}

@Composable
internal fun AboutScreen(
    onOpenPage: (InfoPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AboutContent(state, onOpenPage, modifier, onBack)
}

@Composable
internal fun AboutContent(
    state: AboutUiState,
    onOpenPage: (InfoPage) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(CitoyenIcons.Back), stringResource(R.string.feature_about_back))
                }
            }
            Text(
                text = stringResource(R.string.feature_about_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { heading() },
            )
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column {
                val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
                InfoPage.entries.forEach { page ->
                    ListItem(
                        headlineContent = { Text(stringResource(page.title)) },
                        supportingContent = { Text(stringResource(page.summary)) },
                        trailingContent = {
                            Icon(painterResource(CitoyenIcons.ChevronRight), contentDescription = null)
                        },
                        colors = transparent,
                        modifier = Modifier.clickable(role = Role.Button) { onOpenPage(page) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feature_about_report)) },
                    supportingContent = { Text(stringResource(R.string.feature_about_report_summary)) },
                    colors = transparent,
                    modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri(BuildConfig.REPORT_URL) },
                )
            }
        }
        version?.let {
            Text(
                text = stringResource(R.string.feature_about_version, it),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutPageScreen(
    page: InfoPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(page.title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(CitoyenIcons.Back), stringResource(R.string.feature_about_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                InfoPage.DATA -> DataPage(state, viewModel::checkForUpdates)

                InfoPage.SETTINGS -> SettingsPage(
                    state,
                    viewModel::setTextScale,
                    viewModel::setAssistantEnabled,
                    viewModel::removeModel,
                )

                InfoPage.METHODOLOGY -> MethodologyPage()

                InfoPage.SOURCES -> SourcesPage()
            }
        }
    }
}
