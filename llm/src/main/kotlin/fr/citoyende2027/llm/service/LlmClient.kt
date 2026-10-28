package fr.citoyende2027.llm.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.citoyende2027.rag.answer.GenerationRequest
import fr.citoyende2027.rag.answer.GenerationResult
import fr.citoyende2027.rag.answer.GenerationStats
import fr.citoyende2027.rag.answer.LlmGenerator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A generation as reported by the ":llm" process, with what the benchmark needs. */
data class ServiceGeneration(
    val result: GenerationResult,
    val error: String?,
    val peakResidentKb: Long,
    val cpuFeatures: String,
    val threads: Int,
)

/**
 * The local model seen from the app process: requests go to [LlmService] in the ":llm" process. If
 * that process dies (out of memory, native crash), pending requests fail and the app keeps working
 * without AI.
 */
@Singleton
class LlmClient @Inject constructor(@ApplicationContext private val context: Context) : LlmGenerator {
    private class Pending(val onProgress: ((String) -> Unit)?, val reply: CompletableDeferred<Bundle>)

    private val mutex = Mutex()
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val thread by lazy { HandlerThread("llm-client").apply { start() } }
    private val replies by lazy { Messenger(Handler(thread.looper, ::dispatch)) }

    @Volatile
    private var service: Messenger? = null
    private var connected: CompletableDeferred<Messenger>? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val messenger = Messenger(binder)
            service = messenger
            connected?.complete(messenger)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            connected = CompletableDeferred()
            failAll(ERROR_SERVICE_DIED)
        }

        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
            context.unbindService(this)
            bound = false
        }
    }

    override suspend fun countPromptTokens(system: String, user: String): Int {
        val reply = call(
            LlmProtocol.COUNT,
            Bundle().apply {
                putString(LlmProtocol.KEY_SYSTEM, system)
                putString(LlmProtocol.KEY_USER, user)
            },
            onProgress = null,
        )
        return reply.getInt(LlmProtocol.KEY_COUNT, -1).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    override suspend fun generate(request: GenerationRequest, onText: (String) -> Unit): GenerationResult =
        generateDetailed(request, modelFile = null, onText = onText).result

    /** [modelFile]: another file of the model directory, debug builds only (benchmark). */
    suspend fun generateDetailed(
        request: GenerationRequest,
        modelFile: String?,
        onText: (String) -> Unit = {},
    ): ServiceGeneration {
        val bundle = Bundle().apply {
            putString(LlmProtocol.KEY_SYSTEM, request.system)
            putString(LlmProtocol.KEY_USER, request.user)
            putString(LlmProtocol.KEY_GRAMMAR, request.grammar)
            putInt(LlmProtocol.KEY_MAX_TOKENS, request.maxTokens)
            modelFile?.let { putString(LlmProtocol.KEY_MODEL_FILE, it) }
        }
        val reply = call(LlmProtocol.GENERATE, bundle, onText)
        val error = reply.getString(LlmProtocol.KEY_ERROR)
        val result = when (reply.getInt(LlmProtocol.KEY_STATUS, LlmProtocol.STATUS_FAILED)) {
            LlmProtocol.STATUS_COMPLETED -> GenerationResult.Completed(
                text = reply.getString(LlmProtocol.KEY_TEXT).orEmpty(),
                stats = GenerationStats(
                    promptTokens = reply.getInt(LlmProtocol.KEY_PROMPT_TOKENS),
                    timeToFirstTokenMs = reply.getLong(LlmProtocol.KEY_TTFT_MS),
                    tokens = reply.getInt(LlmProtocol.KEY_TOKENS),
                    durationMs = reply.getLong(LlmProtocol.KEY_DURATION_MS),
                ),
            )

            LlmProtocol.STATUS_STOPPED -> GenerationResult.Stopped

            else -> GenerationResult.Failed(error ?: "unknown")
        }
        return ServiceGeneration(
            result = result,
            error = error,
            peakResidentKb = reply.getLong(LlmProtocol.KEY_PEAK_RSS_KB, -1),
            cpuFeatures = reply.getString(LlmProtocol.KEY_CPU_FEATURES).orEmpty(),
            threads = reply.getInt(LlmProtocol.KEY_THREADS),
        )
    }

    private suspend fun call(what: Int, bundle: Bundle, onProgress: ((String) -> Unit)?): Bundle {
        val messenger = connect()
        val id = ids.incrementAndGet()
        val reply = CompletableDeferred<Bundle>()
        pending[id] = Pending(onProgress, reply)
        try {
            messenger.send(
                Message.obtain(null, what, id, 0).apply {
                    data = bundle
                    replyTo = replies
                },
            )
            return reply.await()
        } catch (e: RemoteException) {
            return failure(ERROR_SERVICE_DIED)
        } catch (e: CancellationException) {
            // "Arrêter" or a closed screen: the generation stops in the other process too.
            if (what == LlmProtocol.GENERATE) {
                runCatching { messenger.send(Message.obtain(null, LlmProtocol.STOP, id, 0)) }
            }
            throw e
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun connect(): Messenger = mutex.withLock {
        service?.let { return it }
        val waiting = connected ?: CompletableDeferred<Messenger>().also { connected = it }
        if (!bound) {
            bound = context.bindService(Intent(context, LlmService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        waiting
    }.await()

    private fun dispatch(message: Message): Boolean {
        val target = pending[message.arg1] ?: return true
        when (message.what) {
            LlmProtocol.PROGRESS -> target.onProgress?.invoke(message.data.getString(LlmProtocol.KEY_TEXT).orEmpty())
            LlmProtocol.COUNTED, LlmProtocol.DONE -> target.reply.complete(message.data)
        }
        return true
    }

    private fun failAll(reason: String) {
        pending.values.forEach { it.reply.complete(failure(reason)) }
    }

    private fun failure(reason: String) = Bundle().apply {
        putInt(LlmProtocol.KEY_STATUS, LlmProtocol.STATUS_FAILED)
        putString(LlmProtocol.KEY_ERROR, reason)
    }

    companion object {
        const val ERROR_SERVICE_DIED = "service_died"
    }
}
