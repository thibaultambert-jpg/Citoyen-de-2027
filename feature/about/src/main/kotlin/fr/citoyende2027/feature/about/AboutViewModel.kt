package fr.citoyende2027.feature.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.core.data.repository.AppInfoRepository
import fr.citoyende2027.core.data.repository.InstalledPack
import fr.citoyende2027.core.data.repository.PackUpdate
import fr.citoyende2027.core.data.repository.TextScale
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.llm.assistant.Assistant
import fr.citoyende2027.llm.assistant.AssistantStatus
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** On-device storage used by the app (Paramètres > Stockage). */
data class StorageUse(val dataBytes: Long, val modelBytes: Long)

/** What the "Infos" pages show; static texts come from resources. */
data class AboutUiState(
    val packs: List<InstalledPack> = emptyList(),
    val history: List<PackUpdate> = emptyList(),
    val textScale: TextScale = TextScale.SYSTEM,
    val assistant: AssistantStatus? = null,
    val syncRequested: Boolean = false,
    val storage: StorageUse = StorageUse(0, 0),
) {
    val core: InstalledPack? get() = packs.firstOrNull { it.id == DataPackInfo.CORE }
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInfo: AppInfoRepository,
    private val assistant: Assistant,
) : ViewModel() {
    private val syncRequested = MutableStateFlow(false)

    val uiState: StateFlow<AboutUiState> = combine(
        appInfo.observeInstalledPacks(),
        appInfo.observeHistory(),
        appInfo.textScale,
        assistant.status.onStart<AssistantStatus?> { emit(null) },
        syncRequested,
    ) { packs, history, scale, assistantStatus, requested ->
        AboutUiState(packs, history, scale, assistantStatus, requested, storage())
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutUiState())

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { appInfo.setTextScale(scale) }
    }

    fun checkForUpdates() {
        appInfo.requestSync()
        syncRequested.value = true
    }

    fun setAssistantEnabled(enabled: Boolean) = assistant.setEnabled(enabled)

    fun removeModel() = assistant.remove()

    /** Sizes of the app's files: data (database, packs) and the assistant's model directory. */
    private fun storage(): StorageUse {
        val model = File(context.filesDir, MODEL_DIRECTORY)
        val data = context.filesDir.parentFile?.let { app ->
            listOfNotNull(app.resolve("databases"), app.resolve("files")).sumOf(::sizeOf)
        } ?: 0L
        val modelBytes = sizeOf(model)
        return StorageUse(dataBytes = data - modelBytes, modelBytes = modelBytes)
    }

    private fun sizeOf(file: File): Long = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        /** Directory of the model file (`:llm`, ModelStores). */
        const val MODEL_DIRECTORY = "models"
    }
}
