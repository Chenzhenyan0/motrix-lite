package com.motrixlite.downloader.data

import com.motrixlite.downloader.data.model.DownloadEngine
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.Segment
import com.motrixlite.downloader.data.model.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskJsonCodecTest {

    private val sample = DownloadTask(
        id = "task-1",
        url = "https://example.com/file.zip",
        finalUrl = "https://cdn.example.com/file.zip",
        fileName = "文件.zip",
        fileNameLocked = true,
        mimeType = "application/zip",
        totalBytes = 10_000_000,
        downloadedBytes = 5_000_000,
        state = TaskState.DOWNLOADING,
        segments = listOf(
            Segment(0, 0, 4_999_999, 2_500_000),
            Segment(1, 5_000_000, 9_999_999, 2_500_000),
        ),
        supportsRanges = true,
        etag = "abc",
        lastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
        connections = 8,
        error = null,
        retryCount = 2,
        desiredRunning = true,
        createdAt = 1_700_000_000_000,
        completedAt = null,
        destinationUri = null,
        savedFileName = null,
        stagingName = "task-1.part",
        saveMode = SaveMode.SAF,
        safTreeUri = "content://tree/1",
        engine = DownloadEngine.ARIA2,
        engineTaskId = "gid-123",
        engineStatus = "active",
        speedBytesPerSec = 12345,
        speedHistory = listOf(1, 2, 3),
        publishProgress = 0.5f,
    )

    @Test
    fun `round trips persisted fields and drops runtime fields`() {
        val decoded = TaskJsonCodec.decode(TaskJsonCodec.encode(listOf(sample)))
        assertEquals(1, decoded.size)
        val task = decoded[0]
        assertEquals(sample.id, task.id)
        assertEquals(sample.url, task.url)
        assertEquals(sample.fileName, task.fileName)
        assertEquals(sample.totalBytes, task.totalBytes)
        assertEquals(sample.downloadedBytes, task.downloadedBytes)
        assertEquals(sample.state, task.state)
        assertEquals(2, task.segments.size)
        assertEquals(2_500_000, task.segments[0].downloaded)
        assertEquals(sample.saveMode, task.saveMode)
        assertEquals(sample.safTreeUri, task.safTreeUri)
        assertEquals(sample.etag, task.etag)
        assertEquals(DownloadEngine.ARIA2, task.engine)
        assertEquals("gid-123", task.engineTaskId)
        assertEquals("active", task.engineStatus)
        // runtime-only fields should be reset
        assertEquals(0L, task.speedBytesPerSec)
        assertTrue(task.speedHistory.isEmpty())
        assertEquals(-1f, task.publishProgress)
    }

    @Test
    fun `old v1 tasks without engine decode as builtin engine`() {
        val json = """{"version":1,"tasks":[{"id":"old-1","url":"https://example.com/a.bin","fileName":"a.bin","stagingName":"old-1.part"}]}"""
        val decoded = TaskJsonCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(DownloadEngine.BUILTIN, decoded[0].engine)
    }

    @Test
    fun `decodes empty or malformed payload safely`() {
        assertTrue(TaskJsonCodec.decode("").isEmpty())
        assertTrue(TaskJsonCodec.decode("{}").isEmpty())
    }
}