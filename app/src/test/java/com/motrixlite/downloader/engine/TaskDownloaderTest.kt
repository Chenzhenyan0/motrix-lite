package com.motrixlite.downloader.engine

import com.motrixlite.downloader.data.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class TaskDownloaderTest {

    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun newTask(url: String, staging: File, connections: Int = 4): DownloadTask = DownloadTask(
        id = "test-task",
        url = url,
        fileName = "testfile.bin",
        mimeType = "application/octet-stream",
        connections = connections,
        stagingName = staging.name,
    )

    @Test
    fun `downloads multi-segment file and writes exact bytes`() = runBlocking {
        val data = ByteArray(3 * 1024 * 1024 + 123) { (it % 251).toByte() }
        server.dispatcher = RangeDispatcher(data)
        val staging = File(tempFolder.newFolder(), "task.part")
        val task = newTask(server.url("/file.bin").toString(), staging)

        val result = TaskDownloader(
            initialTask = task,
            client = client(),
            stagingFile = staging,
            rateLimiter = RateLimiter(),
        ).run()

        assertTrue("expected Completed but was $result", result is DownloadResult.Completed)
        val completed = (result as DownloadResult.Completed).task
        assertEquals(data.size.toLong(), completed.totalBytes)
        assertEquals(data.size.toLong(), completed.downloadedBytes)
        assertTrue("segments=${completed.segments.size}", completed.segments.size >= 2)
        assertArrayEquals(data, staging.readBytes())
    }

    @Test
    fun `pause then resume downloads only the remaining ranges`() = runBlocking {
        val data = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        server.dispatcher = RangeDispatcher(data, throttle = true)
        val staging = File(tempFolder.newFolder(), "resume.part")
        val task = newTask(server.url("/resume.bin").toString(), staging)

        var paused: DownloadResult? = null
        val job = launch(Dispatchers.IO) {
            paused = TaskDownloader(
                initialTask = task,
                client = client(),
                stagingFile = staging,
                rateLimiter = RateLimiter(),
            ).run()
        }
        delay(350)
        job.cancel()
        job.join()

        val pausedResult = paused
        assertTrue("expected Paused but was $pausedResult", pausedResult is DownloadResult.Paused)
        val pausedTask = (pausedResult as DownloadResult.Paused).task
        assertTrue("downloaded=${pausedTask.downloadedBytes}", pausedTask.downloadedBytes > 0)
        assertTrue(pausedTask.downloadedBytes < data.size)

        val resumed = TaskDownloader(
            initialTask = pausedTask,
            client = client(),
            stagingFile = staging,
            rateLimiter = RateLimiter(),
        ).run()
        assertTrue("expected Completed but was $resumed", resumed is DownloadResult.Completed)
        assertArrayEquals(data, staging.readBytes())
    }

    @Test
    fun `falls back to single connection when server ignores range`() = runBlocking {
        val data = ByteArray(3 * 1024 * 1024) { (it % 97).toByte() }
        server.dispatcher = RangeDispatcher(data, supportRanges = false)
        val staging = File(tempFolder.newFolder(), "single.part")
        val task = newTask(server.url("/single.bin").toString(), staging)

        val result = TaskDownloader(
            initialTask = task,
            client = client(),
            stagingFile = staging,
            rateLimiter = RateLimiter(),
        ).run()

        assertTrue("expected Completed but was $result", result is DownloadResult.Completed)
        val completed = (result as DownloadResult.Completed).task
        assertEquals(data.size.toLong(), completed.totalBytes)
        assertEquals(false, completed.supportsRanges)
        assertEquals(1, completed.segments.size)
        assertArrayEquals(data, staging.readBytes())
    }

    private class RangeDispatcher(
        private val data: ByteArray,
        private val supportRanges: Boolean = true,
        private val throttle: Boolean = false,
    ) : Dispatcher() {
        private val rangeRequestCount = AtomicInteger(0)

        override fun dispatch(request: RecordedRequest): MockResponse {
            val rangeHeader = request.getHeader("Range")
            if (!supportRanges || rangeHeader == null) {
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(data))
                    .setHeader("Content-Type", "application/octet-stream")
                    .setHeader("Content-Disposition", "attachment; filename=\"testfile.bin\"")
            }
            val match = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
                ?: return MockResponse().setResponseCode(416)
            val start = match.groupValues[1].toLong()
            val requestedEnd = match.groupValues[2].ifBlank { (data.size - 1).toString() }.toLong()
            if (start >= data.size) {
                return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${data.size}")
            }
            val end = min(requestedEnd, (data.size - 1).toLong())
            val slice = data.copyOfRange(start.toInt(), (end + 1).toInt())
            val response = MockResponse()
                .setResponseCode(206)
                .setBody(Buffer().write(slice))
                .setHeader("Content-Range", "bytes $start-$end/${data.size}")
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", "\"stable-etag\"")
                .setHeader("Content-Disposition", "attachment; filename=\"testfile.bin\"")
            if (throttle && rangeRequestCount.incrementAndGet() > 1) {
                response.throttleBody(64 * 1024, 30, TimeUnit.MILLISECONDS)
            }
            return response
        }
    }
}
