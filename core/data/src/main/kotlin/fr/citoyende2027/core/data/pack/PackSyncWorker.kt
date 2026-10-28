package fr.citoyende2027.core.data.pack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Background pack sync: Wi-Fi (unmetered) by default, resumable, never blocking (AGENTS.md §5). */
@HiltWorker
class PackSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: PackSynchronizer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (synchronizer.sync()) {
        is SyncResult.Installed, SyncResult.UpToDate, SyncResult.Disabled -> Result.success()

        is SyncResult.Transient -> Result.retry()

        // A refused pack is not retried before the next period: the installed data stays active.
        is SyncResult.Rejected -> Result.failure()
    }

    companion object {
        private const val UNIQUE_NAME = "pack-sync"
        private const val UNIQUE_NAME_NOW = "pack-sync-now"
        private const val UNIQUE_NAME_LAUNCH = "pack-sync-launch"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<PackSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One sync when the app starts, on Wi-Fi (AGENTS.md §5): data refreshed without waiting a day. */
        fun syncAtLaunch(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<PackSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME_LAUNCH, ExistingWorkPolicy.KEEP, request)
        }

        /** One sync at the user's request ("Rechercher une mise à jour"), on any network. */
        fun syncNow(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<PackSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
