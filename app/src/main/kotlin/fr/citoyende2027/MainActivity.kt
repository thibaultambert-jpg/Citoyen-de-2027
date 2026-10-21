package fr.citoyende2027

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.ui.CitoyenApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitoyenTheme {
                CitoyenApp()
            }
        }
    }
}
