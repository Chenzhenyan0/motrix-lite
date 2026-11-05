package com.motrixlite.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes formats common sizes`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.5 KB", Formatters.bytes(1536))
        assertEquals("1.0 MB", Formatters.bytes(1024L * 1024L))
        assertEquals("1.5 MB", Formatters.bytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `speed formats bytes per second`() {
        assertEquals("0 B/s", Formatters.speed(0))
        assertEquals("1.5 KB/s", Formatters.speed(1536))
    }

    @Test
    fun `eta handles unknown values`() {
        assertEquals("计算中", Formatters.eta(-1, 100))
        assertEquals("计算中", Formatters.eta(100, 0))
        assertEquals("剩余 10s", Formatters.eta(1000, 100))
    }
}