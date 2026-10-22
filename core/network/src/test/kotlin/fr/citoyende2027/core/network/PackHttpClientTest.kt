package fr.citoyende2027.core.network

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/** In-memory server: no test touches the network (AGENTS.md §4). */
private class FakeServer(private val body: ByteArray, private val honoursRange: Boolean = true) {
    val requestedRanges = mutableListOf<String?>()

    val factory = ConnectionFactory { url -> FakeConnection(url) }

    inner class FakeConnection(url: URL) : HttpURLConnection(url) {
        private var range: String? = null

        override fun setRequestProperty(key: String, value: String) {
            if (key == "Range") range = value
        }

        private val start get() = range?.removePrefix("bytes=")?.removeSuffix("-")?.toLong() ?: 0L

        override fun getResponseCode(): Int {
            requestedRanges += range
            return if (range != null && honoursRange) HTTP_PARTIAL else HTTP_OK
        }

        override fun getInputStream(): InputStream {
            val from = if (range != null && honoursRange) start.toInt() else 0
            return ByteArrayInputStream(body, from, body.size - from)
        }

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false
    }
}

class PackHttpClientTest {
    private val body = ByteArray(1000) { it.toByte() }

    @Test
    fun `refuses plain http`() = runTest {
        val client = PackHttpClient(FakeServer(body).factory)
        assertThrows<IllegalArgumentException> { client.fetchBytes("http://example.invalid/manifest.json") }
    }

    @Test
    fun `fetches small files with a size limit`() = runTest {
        val client = PackHttpClient(FakeServer(body).factory)
        assertArrayEquals(body, client.fetchBytes("https://example.invalid/m", maxBytes = 1000))
        assertThrows<IOException> { client.fetchBytes("https://example.invalid/m", maxBytes = 999) }
    }

    @Test
    fun `resumes a partial download with a range request`(@TempDir dir: File) = runTest {
        val server = FakeServer(body)
        val target = File(dir, "core.part").apply { writeBytes(body.copyOf(400)) }
        PackHttpClient(server.factory).download("https://example.invalid/core.sqlite", target, body.size.toLong())
        assertEquals(listOf<String?>("bytes=400-"), server.requestedRanges)
        assertArrayEquals(body, target.readBytes())
    }

    @Test
    fun `restarts when the server ignores the range`(@TempDir dir: File) = runTest {
        val target = File(dir, "core.part").apply { writeBytes(body.copyOf(400)) }
        PackHttpClient(FakeServer(body, honoursRange = false).factory)
            .download("https://example.invalid/core.sqlite", target, body.size.toLong())
        assertArrayEquals(body, target.readBytes())
    }

    @Test
    fun `rejects a download of unexpected size`(@TempDir dir: File) = runTest {
        val target = File(dir, "core.part")
        assertThrows<IOException> {
            PackHttpClient(FakeServer(body).factory).download("https://example.invalid/core", target, 2000)
        }
    }
}
