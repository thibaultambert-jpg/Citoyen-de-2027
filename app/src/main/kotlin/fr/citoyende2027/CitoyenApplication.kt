package fr.citoyende2027

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import fr.citoyende2027.core.data.pack.PackSyncWorker
import fr.citoyende2027.data.StartupDataSeeder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CitoyenApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var startupDataSeeder: StartupDataSeeder

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // The ":llm" process only runs the local model (AGENTS.md §9): no data seeding, no sync there.
        if (Application.getProcessName().endsWith(LLM_PROCESS_SUFFIX)) return
        // Never blocks startup; screens observe the database and update when data lands.
        applicationScope.launch {
            startupDataSeeder.seedIfNeeded()
            PackSyncWorker.schedule(WorkManager.getInstance(this@CitoyenApplication))
        }
    }

    private companion object {
        const val LLM_PROCESS_SUFFIX = ":llm"
    }
}
