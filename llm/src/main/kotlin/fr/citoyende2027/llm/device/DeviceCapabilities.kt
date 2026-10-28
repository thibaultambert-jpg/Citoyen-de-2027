package fr.citoyende2027.llm.device

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

/** Why the assistant cannot run on this device; the app then works in "Mode sans IA" (AGENTS.md §9). */
enum class Limitation {
    MEMORY,
    STORAGE,
}

/**
 * Prerequisites of AGENTS.md §9: 6 GB of RAM and 4 GB of free space (the space is needed only while
 * the model is not installed).
 */
object DeviceRequirements {
    /**
     * A device sold with 6 GB reports less total memory (kernel and firmware reservations), around 5.5 to
     * 5.9 billion bytes. TODO(verify): threshold to confirm on real 6 GB devices.
     */
    const val MIN_TOTAL_RAM_BYTES = 5_500_000_000L
    const val MIN_FREE_STORAGE_BYTES = 4_000_000_000L

    fun limitations(totalRamBytes: Long, freeStorageBytes: Long, modelInstalled: Boolean): Set<Limitation> = buildSet {
        if (totalRamBytes < MIN_TOTAL_RAM_BYTES) add(Limitation.MEMORY)
        if (!modelInstalled && freeStorageBytes < MIN_FREE_STORAGE_BYTES) add(Limitation.STORAGE)
    }

    fun totalRam(context: Context): Long = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }.totalMem

    fun freeStorage(directory: File): Long = StatFs(directory.path).availableBytes
}

/**
 * Number of generation threads = performance cores (AGENTS.md §9): every core except those of the
 * slowest cluster, when the cores do not all have the same maximum frequency.
 */
object PerformanceCores {
    private const val MAX_THREADS = 8

    fun count(maxFrequencies: List<Long?>, availableProcessors: Int): Int {
        val known = maxFrequencies.filterNotNull()
        if (known.size != maxFrequencies.size ||
            known.isEmpty()
        ) {
            return (availableProcessors / 2).coerceIn(1, MAX_THREADS)
        }
        val slowest = known.min()
        val fast = known.count { it > slowest }
        return (if (fast == 0) known.size / 2 else fast).coerceIn(1, MAX_THREADS)
    }

    fun ofThisDevice(): Int {
        val processors = Runtime.getRuntime().availableProcessors()
        val frequencies = (0 until processors).map { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrNull()
        }
        return count(frequencies, processors)
    }
}
