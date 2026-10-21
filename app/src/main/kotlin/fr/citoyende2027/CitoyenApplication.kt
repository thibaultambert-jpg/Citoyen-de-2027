package fr.citoyende2027

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.citoyende2027.data.StartupDataSeeder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CitoyenApplication : Application() {
    @Inject
    lateinit var startupDataSeeder: StartupDataSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Never blocks startup; screens observe the database and update when data lands.
        applicationScope.launch { startupDataSeeder.seedIfNeeded() }
    }
}
