package com.motrixlite.downloader.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * UI 侧用来启动/操作前台服务的薄封装。
 * 所有启动异常都在这里吞掉：调用方（UI）会通过任务状态看到是否成功。
 */
class ServiceController(private val context: Context) {

    fun ensureRunning() = start(DownloadService.ACTION_ENSURE)

    fun pauseAll() = start(DownloadService.ACTION_PAUSE_ALL)

    fun resumeAll() = start(DownloadService.ACTION_RESUME_ALL)

    private fun start(action: String) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(action),
            )
        } catch (_: Throwable) {
            // Android 12+ 后台启动前台服务限制；界面会在用户下次操作时重试。
        }
    }
}
