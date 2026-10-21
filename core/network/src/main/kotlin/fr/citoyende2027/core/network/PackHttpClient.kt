package fr.citoyende2027.core.network

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens a connection; replaced by a fake in tests so that no test reaches the network. */
fun interface ConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class HttpStatusException(val status: Int, url: String) : IOException("HTTP $status for $url")

/**
 * Downloads pack files over HTTPS only. Sends no cookie, no identifier and no user data: the only
 * information leaving the device is the requested file URL (AGENTS.md §2.4).
 */
class PackHttpClient @Inject constructor(private val connections: ConnectionFactory) {
    /** Small files (manifest, signature), read fully in memory with a size limit. */
    suspend fun fetchBytes(url: String, maxBytes: Int = MAX_SMALL_FILE): ByteArray = withContext(Dispatchers.IO) {
        val connection = open(url, rangeStart = 0)
        try {
            connection.expectStatus(url, HttpURLConnection.HTTP_OK)
            connection.inputStream.use { input -> input.readAtMost(maxBytes, url) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads [url] into [target], resuming from the bytes already present (HTTP Range).
     * A server that ignores the range answers 200: the file is then restarted from zero.
     */
    suspend fun download(url: String, target: File, expectedSize: Long) = withContext(Dispatchers.IO) {
        val already = if (target.exists()) target.length() else 0L
        if (already == expectedSize) return@withContext
        if (already > expectedSize) target.delete()
        val start = if (target.exists()) target.length() else 0L
        val connection = open(url, rangeStart = start)
        try {
            val append = when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> start > 0
                HttpURLConnection.HTTP_OK -> false
                else -> throw HttpStatusException(status, url)
            }
            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, append).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (target.length() != expectedSize) {
            throw IOException("Incomplete download of $url: ${target.length()} / $expectedSize bytes")
        }
    }

    private fun open(url: String, rangeStart: Long): HttpURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS downloads are allowed: $url" }
        return connections.open(URL(url)).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
        }
    }

    // InputStream.readNBytes needs API 33; minSdk is 29.
    private fun java.io.InputStream.readAtMost(maxBytes: Int, url: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) return output.toByteArray()
            output.write(buffer, 0, read)
            if (output.size() > maxBytes) throw IOException("File too large: $url")
        }
    }

    private fun HttpURLConnection.expectStatus(url: String, expected: Int) {
        val status = responseCode
        if (status != expected) throw HttpStatusException(status, url)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_SMALL_FILE = 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
    }
}
