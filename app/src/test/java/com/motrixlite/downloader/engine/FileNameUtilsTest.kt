package com.motrixlite.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameUtilsTest {

    @Test
    fun `parses rfc5987 content disposition`() {
        val header = "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95%E6%96%87%E4%BB%B6.bin"
        assertEquals("测试文件.bin", FileNameUtils.fromContentDisposition(header))
    }

    @Test
    fun `parses quoted content disposition`() {
        assertEquals("report 2024.pdf", FileNameUtils.fromContentDisposition("attachment; filename=\"report 2024.pdf\""))
        assertEquals("plain.txt", FileNameUtils.fromContentDisposition("attachment; filename=plain.txt"))
        assertNull(FileNameUtils.fromContentDisposition(null))
    }

    @Test
    fun `derives name from url`() {
        assertEquals("movie.mp4", FileNameUtils.fromUrl("https://cdn.example.com/a/movie.mp4?token=1"))
        assertNull(FileNameUtils.fromUrl("https://example.com/"))
    }

    @Test
    fun `sanitizes invalid characters and keeps extension`() {
        val sanitized = FileNameUtils.sanitize("a/b:c*?\"<>|.zip")
        assertTrue(sanitized.endsWith(".zip"))
        assertTrue(!sanitized.contains('/'))
    }

    @Test
    fun `adds extension from mime type`() {
        assertEquals("video.mp4", FileNameUtils.ensureExtension("video", "video/mp4"))
        assertEquals("video.mp4", FileNameUtils.ensureExtension("video.mp4", "video/mp4"))
        assertEquals("unknown", FileNameUtils.ensureExtension("unknown", null))
    }

    @Test
    fun `maps mime and extension both ways`() {
        assertEquals("mp4", FileNameUtils.extensionForMime("video/mp4"))
        assertEquals("video/mp4", FileNameUtils.mimeForExtension("mp4"))
        assertEquals("application/octet-stream", FileNameUtils.mimeForExtension("nope"))
    }
}