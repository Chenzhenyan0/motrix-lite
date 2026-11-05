package com.motrixlite.downloader.engine

import com.motrixlite.downloader.data.model.Segment
import java.util.Locale

/**
 * 下载分段规划（纯 Kotlin）。
 *
 * 规则：
 * - 服务器不支持 Range 或总长度未知 -> 单连接；
 * - 文件小于 2MB -> 单连接（避免大量小分段反而降低吞吐）；
 * - 否则按连接数切分，每段至少 1MB，最多 16 段。
 */
object SegmentPlanner {

    const val MIN_MULTI_SEGMENT_BYTES = 2L * 1024 * 1024
    const val MIN_SEGMENT_BYTES = 1L * 1024 * 1024

    fun plan(totalBytes: Long, connections: Int): List<Segment> {
        val conns = connections.coerceIn(1, 16)
        if (totalBytes <= 0L) return listOf(Segment(index = 0, start = 0, end = -1))
        if (conns <= 1 || totalBytes < MIN_MULTI_SEGMENT_BYTES) {
            return listOf(Segment(index = 0, start = 0, end = totalBytes - 1))
        }
        val segmentCount = minOf(
            conns.toLong(),
            maxOf(2L, totalBytes / MIN_SEGMENT_BYTES),
        ).toInt().coerceIn(2, 16)
        val base = totalBytes / segmentCount
        val remainder = totalBytes % segmentCount
        val result = ArrayList<Segment>(segmentCount)
        var start = 0L
        for (i in 0 until segmentCount) {
            val size = base + if (i < remainder) 1L else 0L
            val end = start + size - 1
            result += Segment(index = i, start = start, end = end)
            start = end + 1
        }
        return result
    }

    fun describe(segments: List<Segment>): String = String.format(
        Locale.US,
        "%d 段 / %s",
        segments.size,
        Formatters.bytes(segments.sumOf { it.length.coerceAtLeast(0) }),
    )
}
