package com.motrixlite.downloader.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/**
 * 全局令牌桶限速器。所有任务、所有连接共享同一个桶。
 *
 * limitBytesPerSecond <= 0 表示不限速，直接返回（零开销）。
 * 时间源可注入，便于测试。
 */
class RateLimiter(private val nanoTime: () -> Long = System::nanoTime) {

    @Volatile
    var limitBytesPerSecond: Long = 0L

    private val mutex = Mutex()
    private var tokens: Double = 0.0
    private var lastRefillNanos: Long = nanoTime()

    /** 建议的读取块大小：低限速时使用更小的块，保证暂停/限速响应及时。 */
    val suggestedBufferSize: Int
        get() {
            val limit = limitBytesPerSecond
            if (limit <= 0L) return 64 * 1024
            return minOf(64 * 1024, maxOf(1024, (limit / 4).toInt()))
        }

    suspend fun acquire(byteCount: Int) {
        if (byteCount <= 0) return
        while (true) {
            val limit = limitBytesPerSecond
            if (limit <= 0L) return
            val waitMillis = mutex.withLock {
                val now = nanoTime()
                val elapsedNanos = (now - lastRefillNanos).coerceAtLeast(0L)
                lastRefillNanos = now
                val capacity = maxOf(limit.toDouble(), byteCount.toDouble())
                tokens = minOf(capacity, tokens + elapsedNanos / 1_000_000_000.0 * limit)
                if (tokens >= byteCount) {
                    tokens -= byteCount
                    0L
                } else {
                    val needed = byteCount - tokens
                    ceil(needed / limit * 1000.0).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMillis <= 0L) return
            delay(waitMillis)
        }
    }
}
