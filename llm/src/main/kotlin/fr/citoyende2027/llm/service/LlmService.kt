package fr.citoyende2027.llm.service

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.RemoteException
import fr.citoyende2027.llm.BuildConfig
import fr.citoyende2027.llm.device.PerformanceCores
import fr.citoyende2027.llm.engine.LlamaModel
import fr.citoyende2027.llm.engine.LlamaRuntime
import fr.citoyende2027.llm.model.ModelStore
import fr.citoyende2027.rag.answer.GenerationRequest
import fr.citoyende2027.rag.answer.GenerationResult
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local inference in its own process (":llm", AGENTS.md §9), so that the model's memory never weighs on
 * the app process. The model file is memory-mapped, loaded on the first request and unloaded after
 * [IDLE_UNLOAD_MS] without use, on memory pressure, and generation stops when the device overheats.
 * Nothing received here (question, passages, generated text) is logged or stored.
 */
class LlmService : Service() {
    private val native: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "llm-native") }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var messenger: Messenger
    private lateinit var store: ModelStore
    private lateinit var power: PowerManager

    // Touched only on the native thread.
    private var model: LlamaModel? = null
    private var loadedFile: File? = null
    private var cpuFeatures = ""

    // The model generating right now, for STOP from the main thread.
    @Volatile
    private var generating: LlamaModel? = null

    private val threads by lazy { PerformanceCores.ofThisDevice() }
    private val unloadWhenIdle = Runnable { native.execute { unload() } }
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) generating?.requestStop()
    }

    override fun onCreate() {
        super.onCreate()
        store = ModelStores.create(this)
        power = getSystemService(PowerManager::class.java)
        power.addThermalStatusListener(mainExecutor, thermalListener)
        messenger = Messenger(Handler(Looper.getMainLooper(), ::handle))
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && generating == null) {
            native.execute { unload() }
        }
    }

    override fun onDestroy() {
        power.removeThermalStatusListener(thermalListener)
        main.removeCallbacks(unloadWhenIdle)
        generating?.requestStop()
        native.execute { unload() }
        native.shutdown()
        super.onDestroy()
    }

    private fun handle(message: Message): Boolean {
        val id = message.arg1
        val reply = message.replyTo
        val data = message.data
        when (message.what) {
            LlmProtocol.COUNT -> native.execute {
                val count = withModel(data)?.countPromptTokens(
                    data.getString(LlmProtocol.KEY_SYSTEM).orEmpty(),
                    data.getString(LlmProtocol.KEY_USER).orEmpty(),
                ) ?: -1
                send(reply, LlmProtocol.COUNTED, id, Bundle().apply { putInt(LlmProtocol.KEY_COUNT, count) })
                scheduleUnload()
            }

            LlmProtocol.GENERATE -> when {
                power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE ->
                    send(reply, LlmProtocol.DONE, id, failure(LlmProtocol.ERROR_THERMAL))

                generating != null -> send(reply, LlmProtocol.DONE, id, failure(LlmProtocol.ERROR_BUSY))

                else -> native.execute { generate(id, reply, data) }
            }

            LlmProtocol.STOP -> generating?.requestStop()

            else -> return false
        }
        return true
    }

    private fun generate(id: Int, reply: Messenger?, data: Bundle) {
        main.removeCallbacks(unloadWhenIdle)
        val loaded = withModel(data)
        if (loaded == null) {
            val reason = if (modelFile(data) == null) LlmProtocol.ERROR_NO_MODEL else LlmProtocol.ERROR_LOAD
            send(reply, LlmProtocol.DONE, id, failure(reason))
            return
        }
        val request = GenerationRequest(
            system = data.getString(LlmProtocol.KEY_SYSTEM).orEmpty(),
            user = data.getString(LlmProtocol.KEY_USER).orEmpty(),
            grammar = data.getString(LlmProtocol.KEY_GRAMMAR).orEmpty(),
            maxTokens = data.getInt(LlmProtocol.KEY_MAX_TOKENS, GenerationRequest.MAX_TOKENS),
        )
        generating = loaded
        var lastProgress = 0L
        val result = try {
            loaded.generate(request) { text ->
                val now = System.currentTimeMillis()
                if (now - lastProgress >= PROGRESS_INTERVAL_MS) {
                    lastProgress = now
                    send(reply, LlmProtocol.PROGRESS, id, Bundle().apply { putString(LlmProtocol.KEY_TEXT, text) })
                }
            }
        } finally {
            generating = null
        }
        val bundle = when (result) {
            is GenerationResult.Completed -> Bundle().apply {
                putInt(LlmProtocol.KEY_STATUS, LlmProtocol.STATUS_COMPLETED)
                putString(LlmProtocol.KEY_TEXT, result.text)
                putInt(LlmProtocol.KEY_PROMPT_TOKENS, result.stats.promptTokens)
                putLong(LlmProtocol.KEY_TTFT_MS, result.stats.timeToFirstTokenMs)
                putInt(LlmProtocol.KEY_TOKENS, result.stats.tokens)
                putLong(LlmProtocol.KEY_DURATION_MS, result.stats.durationMs)
            }

            GenerationResult.Stopped -> Bundle().apply {
                putInt(LlmProtocol.KEY_STATUS, LlmProtocol.STATUS_STOPPED)
                if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                    putString(LlmProtocol.KEY_ERROR, LlmProtocol.ERROR_THERMAL)
                }
            }

            is GenerationResult.Failed -> failure(result.reason)
        }
        bundle.putLong(LlmProtocol.KEY_PEAK_RSS_KB, peakResidentKb())
        bundle.putString(LlmProtocol.KEY_CPU_FEATURES, cpuFeatures)
        bundle.putInt(LlmProtocol.KEY_THREADS, threads)
        send(reply, LlmProtocol.DONE, id, bundle)
        scheduleUnload()
    }

    /** The verified model (or, in debug builds, a benchmark file of the model directory), loaded once. */
    private fun withModel(data: Bundle): LlamaModel? {
        val file = modelFile(data) ?: return null
        if (loadedFile == file && model != null) return model
        unload()
        cpuFeatures = LlamaRuntime.ensureLoaded(this)
        model = LlamaModel.load(file.path, threads)
        loadedFile = file.takeIf { model != null }
        return model
    }

    private fun modelFile(data: Bundle): File? {
        val benchmark = data.getString(LlmProtocol.KEY_MODEL_FILE)
        if (benchmark != null && BuildConfig.DEBUG && benchmark.matches(BENCHMARK_FILE)) {
            return File(store.modelFile.parentFile, benchmark).takeIf { it.isFile }
        }
        return store.installed()
    }

    private fun unload() {
        model?.close()
        model = null
        loadedFile = null
    }

    private fun scheduleUnload() {
        main.removeCallbacks(unloadWhenIdle)
        main.postDelayed(unloadWhenIdle, IDLE_UNLOAD_MS)
    }

    private fun failure(reason: String) = Bundle().apply {
        putInt(LlmProtocol.KEY_STATUS, LlmProtocol.STATUS_FAILED)
        putString(LlmProtocol.KEY_ERROR, reason)
    }

    private fun send(to: Messenger?, what: Int, id: Int, bundle: Bundle) {
        val message = Message.obtain(null, what, id, 0).apply { data = bundle }
        try {
            to?.send(message)
        } catch (_: RemoteException) {
            // The app process is gone: nothing to answer.
        }
    }

    /** Peak resident memory of this process (VmHWM), for the benchmark. */
    private fun peakResidentKb(): Long = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmHWM:") }
            .removePrefix("VmHWM:").trim().removeSuffix("kB").trim().toLong()
    }.getOrDefault(-1)

    companion object {
        /** AGENTS.md §9: unload after 2 minutes of inactivity. */
        const val IDLE_UNLOAD_MS = 2 * 60 * 1000L
        private const val PROGRESS_INTERVAL_MS = 250L
        private val BENCHMARK_FILE = Regex("bench-[a-zA-Z0-9._-]+\\.gguf")
    }
}
