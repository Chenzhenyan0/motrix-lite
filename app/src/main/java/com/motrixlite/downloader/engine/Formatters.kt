package com.motrixlite.downloader.engine

import java.util.Locale

/**
 * 纯 Kotlin 的格式化工具，便于 JVM 单元测试。
 */
object Formatters {

    fun bytes(value: Long): String {
        if (value < 0) return "未知"
        if (value < 1024) return "$value B"
        var v = value.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var idx = 0
        while (v >= 1024.0 && idx < units.lastIndex) {
            v /= 1024.0
            idx++
        }
        return if (v >= 100 || idx == 0) {
            String.format(Locale.US, "%.0f %s", v, units[idx])
        } else {
            String.format(Locale.US, "%.1f %s", v, units[idx])
        }
    }

    fun speed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        return "${bytes(bytesPerSecond)}/s"
    }

    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (remainingBytes < 0 || bytesPerSecond <= 0) return "计算中"
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds < 60 -> "剩余 ${seconds}s"
            seconds < 3600 -> "剩余 ${seconds / 60}m ${seconds % 60}s"
            else -> "剩余 ${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    fun dateTime(epochMillis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMillis
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }
}
