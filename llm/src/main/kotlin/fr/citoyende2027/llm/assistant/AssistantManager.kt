package fr.citoyende2027.llm.assistant

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.llm.device.DeviceRequirements
import fr.citoyende2027.llm.device.Limitation
import fr.citoyende2027.llm.model.ModelDownloadWorker
import fr.citoyende2027.llm.model.ModelSpec
import fr.citoyende2027.llm.model.ModelStores
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** What the "Question" screen shows about the local assistant (AGENTS.md §5, §9, §11). */
sealed interface AssistantStatus {
    /** This build has no model file configured (MODEL_URL / MODEL_SHA256 not provided). */
    data object NotConfigured : AssistantStatus

    /** "Mode sans IA": the device does not meet the prerequisites. */
    data class Unsupported(val limitations: Set<Limitation>) : AssistantStatus

    data class NotInstalled(val sizeBytes: Long) : AssistantStatus

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val waitingForWifi: Boolean) :
        AssistantStatus

    /** Checking an imported or downloaded file (SHA-256). */
    data object Verifying : AssistantStatus

    /** The last download or import was refused (wrong file) or failed. */
    data class Failed(val sizeBytes: Long) : AssistantStatus

    data class Ready(val enabled: Boolean) : AssistantStatus
}

/** Pure decision, tested on the JVM. */
internal fun assistantStatus(
    spec: ModelSpec,
    installed: Boolean,
    limitations: Set<Limitation>,
    work: WorkInfo.State?,
    downloadedBytes: Long,
    verifying: Boolean,
    importFailed: Boolean,
    enabled: Boolean,
): AssistantStatus = when {
    !spec.isConfigured -> AssistantStatus.NotConfigured

    Limitation.MEMORY in limitations -> AssistantStatus.Unsupported(limitations)

    installed -> AssistantStatus.Ready(enabled)

    verifying -> AssistantStatus.Verifying

    limitations.isNotEmpty() -> AssistantStatus.Unsupported(limitations)

    work == WorkInfo.State.RUNNING -> AssistantStatus.Downloading(
        downloadedBytes,
        spec.sizeBytes,
        waitingForWifi = false,
    )

    work == WorkInfo.State.ENQUEUED || work == WorkInfo.State.BLOCKED ->
        AssistantStatus.Downloading(downloadedBytes, spec.sizeBytes, waitingForWifi = true)

    work == WorkInfo.State.FAILED || importFailed -> AssistantStatus.Failed(spec.sizeBytes)

    else -> AssistantStatus.NotInstalled(spec.sizeBytes)
}

/**
 * Activation of the assistant: download (Wi-Fi), manual import of the file, removal, and the user's
 * choice to use it. Nothing is downloaded without an explicit action.
 */
@Singleton
class AssistantManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val store = ModelStores.create(context)
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val preferences = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    private val enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))
    private val verifying = MutableStateFlow(false)
    private val importFailed = MutableStateFlow(false)

    /** Refreshed every second while observed (download progress is the size of the partial file). */
    val status: Flow<AssistantStatus> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME),
        ticker(),
        enabled,
        verifying,
        importFailed,
    ) { infos, _, enabled, verifying, importFailed ->
        val installed = store.installed() != null
        assistantStatus(
            spec = store.spec,
            installed = installed,
            limitations = DeviceRequirements.limitations(
                totalRamBytes = DeviceRequirements.totalRam(context),
                freeStorageBytes = DeviceRequirements.freeStorage(context.filesDir),
                modelInstalled = installed,
            ),
            work = infos.lastOrNull()?.state,
            downloadedBytes = store.partFile.length(),
            verifying = verifying,
            importFailed = importFailed,
            enabled = enabled,
        )
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun download() {
        importFailed.value = false
        ModelDownloadWorker.enqueue(workManager)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
        store.partFile.delete()
    }

    /** Copies the chosen file, checks its SHA-256 and activates it; false if it is not the expected file. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        verifying.value = true
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            store.import(input).also { importFailed.value = !it }
        } finally {
            verifying.value = false
        }
    }

    fun remove() {
        cancelDownload()
        store.remove()
        workManager.pruneWork()
    }

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        enabled.value = value
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MS)
        }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val REFRESH_MS = 1_000L
    }
}
