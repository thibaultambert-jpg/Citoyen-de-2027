package fr.citoyende2027.llm.engine

import android.content.Context

/** Loads the native library once per process and the CPU variant suited to this device. */
internal object LlamaRuntime {
    private var cpuFeatures: String? = null

    /** Returns the CPU features of the variant in use (shown by the benchmark). */
    @Synchronized
    fun ensureLoaded(context: Context): String = cpuFeatures ?: run {
        System.loadLibrary("citoyen_llm")
        LlamaNative.init(context.applicationInfo.nativeLibraryDir).also { cpuFeatures = it }
    }
}
