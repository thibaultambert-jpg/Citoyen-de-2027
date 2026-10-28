package fr.citoyende2027.llm.model

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.citoyende2027.core.network.HttpStatusException
import fr.citoyende2027.core.network.PackHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the model (≈ 2 GB): Wi-Fi only (unmetered network), resumed where it stopped (HTTP Range)
 * after a cut or when the system interrupts the work, then checked against its SHA-256 before use.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val http: PackHttpClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = ModelStores.create(applicationContext)
        val spec = store.spec
        if (!spec.isConfigured) return Result.failure()
        if (store.installed() != null) return Result.success()
        store.partFile.parentFile?.mkdirs()
        try {
            http.download(spec.url, store.partFile, spec.sizeBytes)
        } catch (e: HttpStatusException) {
            return if (e.status in 500..599) Result.retry() else Result.failure()
        } catch (e: IOException) {
            return Result.retry()
        }
        return if (store.activatePart()) Result.success() else Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "llm-model-download"

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
