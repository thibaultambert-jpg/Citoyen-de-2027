package fr.citoyende2027.llm.model

import android.content.Context
import fr.citoyende2027.llm.BuildConfig
import java.io.File

/** The same store in both processes: app (download, import) and ":llm" (inference). */
object ModelStores {
    val spec = ModelSpec(BuildConfig.MODEL_URL, BuildConfig.MODEL_SHA256, BuildConfig.MODEL_SIZE_BYTES)

    fun directory(context: Context) = File(context.filesDir, "models")

    fun create(context: Context) = ModelStore(directory(context), spec)
}
