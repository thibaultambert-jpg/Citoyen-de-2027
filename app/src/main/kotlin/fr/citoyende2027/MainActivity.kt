package fr.citoyende2027

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.citoyende2027.core.data.repository.AppInfoRepository
import fr.citoyende2027.core.data.repository.TextScale
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.ui.CitoyenApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appInfo: AppInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // In-app text size (Infos > Paramètres), on top of the system font size.
            val textScale by appInfo.textScale.collectAsStateWithLifecycle(TextScale.SYSTEM)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * textScale.factor),
            ) {
                CitoyenTheme {
                    CitoyenApp()
                }
            }
        }
    }
}
