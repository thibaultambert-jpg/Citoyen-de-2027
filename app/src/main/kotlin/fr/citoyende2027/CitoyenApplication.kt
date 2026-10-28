package fr.citoyende2027

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import fr.citoyende2027.core.data.pack.PackSyncWorker
import fr.citoyende2027.core.data.repository.AppInfoRepository
import fr.citoyende2027.core.data.repository.AskRepository
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

    @Inject
    lateinit var appInfo: AppInfoRepository

    @Inject
    lateinit var askRepository: AskRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // The ":llm" process only runs the local model (AGENTS.md §9): no data seeding, no sync there.
        if (Application.getProcessName().endsWith(LLM_PROCESS_SUFFIX)) return
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        // Never blocks startup; screens observe the database and update when data lands.
        applicationScope.launch {
            startupDataSeeder.seedIfNeeded()
            val workManager = WorkManager.getInstance(this@CitoyenApplication)
            PackSyncWorker.schedule(workManager)
            PackSyncWorker.syncAtLaunch(workManager)
        }
        // "Historique des mises à jour": every pack version activated on this device.
        applicationScope.launch {
            appInfo.observeInstalledPacks().collect { packs ->
                appInfo.recordHistory(packs)
                // The search index is ready before the first question (AGENTS.md §4: retrieval < 300 ms).
                askRepository.warmUp()
            }
        }
    }

    /** Debug builds: disk or network work on the main thread is logged (cold start < 2 s, AGENTS.md §4). */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val LLM_PROCESS_SUFFIX = ":llm"
    }
}
