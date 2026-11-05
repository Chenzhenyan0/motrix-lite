package com.motrixlite.downloader.engine

/**
 * 滑动平均测速器。时间源可注入，便于测试。
 */
class SpeedMeter(private val nowMillis: () -> Long = System::currentTimeMillis) {

    private var lastBytes: Long = -1L
    private var lastTime: Long = 0L
    private var average: Double = 0.0

    fun sample(totalBytes: Long): Long {
        val now = nowMillis()
        if (lastBytes < 0L) {
            lastBytes = totalBytes
            lastTime = now
            return 0L
        }
        val elapsed = now - lastTime
        if (elapsed < 100L) return average.toLong()
        val delta = (totalBytes - lastBytes).coerceAtLeast(0L)
        val instant = delta.toDouble() * 1000.0 / elapsed.toDouble()
        average = if (average <= 0.0) instant else average * 0.6 + instant * 0.4
        lastBytes = totalBytes
        lastTime = now
        return average.toLong()
    }

    fun reset() {
        lastBytes = -1L
        lastTime = 0L
        average = 0.0
    }
}
