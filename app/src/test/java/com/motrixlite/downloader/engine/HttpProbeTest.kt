package com.motrixlite.downloader.engine

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpProbeTest {

    @get:Rule
    val server = MockWebServer()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Test
    fun `detects range support and parses metadata`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(ByteArray(1)))
                    .setHeader("Content-Range", "bytes 0-0/12345678")
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("ETag", "\"etag-1\"")
                    .setHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT")
                    .setHeader("Content-Disposition", "attachment; filename*=UTF-8''%E7%94%B5%E5%BD%B1.mp4")
            }
        }
        val metadata = HttpProbe(client).probe(server.url("/download").toString())
        assertEquals(12345678L, metadata.totalBytes)
        assertTrue(metadata.supportsRanges)
        assertEquals("电影.mp4", metadata.fileName)
        assertEquals("video/mp4", metadata.mimeType)
        assertEquals("\"etag-1\"", metadata.etag)
    }

    @Test
    fun `detects server without range support`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(ByteArray(2048)))
                    .setHeader("Content-Length", "2048")
                    .setHeader("Content-Type", "application/octet-stream")
            }
        }
        val metadata = HttpProbe(client).probe(server.url("/file.bin").toString())
        assertEquals(2048L, metadata.totalBytes)
        assertFalse(metadata.supportsRanges)
        assertEquals("file.bin", metadata.fileName)
    }

    @Test
    fun `reports http errors with retryability`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/missing" -> MockResponse().setResponseCode(404)
                    "/busy" -> MockResponse().setResponseCode(503)
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        val error = runCatching { HttpProbe(client).probe(server.url("/missing").toString()) }.exceptionOrNull()
        assertTrue(error is ProbeException)
        assertFalse((error as ProbeException).retryable)

        val retryableError = runCatching {
            HttpProbe(client).probe(server.url("/busy").toString())
        }.exceptionOrNull()
        assertTrue(retryableError is ProbeException)
        assertTrue((retryableError as ProbeException).retryable)
    }

    @Test
    fun `parses content range helpers`() {
        assertEquals(100L, HttpProbe.parseTotalFromContentRange("bytes 0-99/100"))
        assertEquals(null, HttpProbe.parseTotalFromContentRange("bytes 0-99/*"))
        assertEquals(0L, HttpProbe.parseUnsatisfiedTotal("bytes */0"))
    }
}