package com.motrixlite.downloader.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `unlimited limiter returns immediately`() = runBlocking {
        val limiter = RateLimiter()
        limiter.limitBytesPerSecond = 0
        val start = System.currentTimeMillis()
        withTimeout(500) {
            repeat(10) { limiter.acquire(64 * 1024) }
        }
        assertTrue(System.currentTimeMillis() - start < 200)
    }

    @Test
    fun `suggested buffer size adapts to the limit`() {
        val limiter = RateLimiter()
        limiter.limitBytesPerSecond = 0
        assertEquals(64 * 1024, limiter.suggestedBufferSize)
        limiter.limitBytesPerSecond = 8 * 1024
        assertEquals(2048, limiter.suggestedBufferSize)
        limiter.limitBytesPerSecond = 1024 * 1024
        assertEquals(64 * 1024, limiter.suggestedBufferSize)
    }

    @Test
    fun `limited limiter eventually admits bytes`() = runBlocking {
        val limiter = RateLimiter()
        limiter.limitBytesPerSecond = 1000 * 1024
        withTimeout(3000) {
            repeat(5) { limiter.acquire(128 * 1024) }
        }
    }
}