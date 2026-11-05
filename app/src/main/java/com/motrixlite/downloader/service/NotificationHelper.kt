package com.motrixlite.downloader.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.motrixlite.downloader.R
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.ui.MainActivity
import com.motrixlite.downloader.util.NotificationPermission

/**
 * 所有 notify 调用前都通过 NotificationPermission 做了运行时检查，
 * 并在 runCatching 中兜底；@SuppressLint 仅用于告诉 lint 该检查封装在工具类中。
 */
@SuppressLint("MissingPermission")
class NotificationHelper(private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            context.getString(R.string.channel_downloads_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val resultChannel = NotificationChannel(
            CHANNEL_RESULTS,
            context.getString(R.string.channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_result_desc)
        }
        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(resultChannel)
    }

    fun buildForegroundNotification(tasks: List<DownloadTask>, settings: AppSettings): Notification {
        val running = tasks.filter { it.state == TaskState.QUEUED || it.state.isRunning }
        val waitingWifi = tasks.filter { it.state == TaskState.WAITING_FOR_WIFI }
        val knownTasks = running.filter { it.totalBytes > 0 }
        val totalBytes = knownTasks.sumOf { it.totalBytes }
        val downloadedBytes = knownTasks.sumOf { it.downloadedBytes }
        val speed = running.sumOf { it.speedBytesPerSec }
        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        val saving = tasks.firstOrNull { it.state == TaskState.SAVING }

        val title = when {
            saving != null -> "正在保存「${saving.fileName}」"
            running.isNotEmpty() -> "Motrix Lite 正在下载 ${running.size} 个任务"
            waitingWifi.isNotEmpty() -> "等待 Wi-Fi · ${waitingWifi.size} 个任务"
            else -> "Motrix Lite"
        }
        val text = when {
            saving != null -> {
                val progress = (saving.publishProgress * 100).toInt().coerceIn(0, 100)
                "保存到系统下载目录… $progress%"
            }

            running.isNotEmpty() -> buildString {
                if (totalBytes > 0) append("$percent% · ")
                append(Formatters.speed(speed))
                if (running.any { it.totalBytes <= 0 }) append(" · 正在获取大小")
            }

            waitingWifi.isNotEmpty() -> "已暂停网络请求，连接 Wi-Fi 后自动继续"
            else -> "任务已暂停"
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionLabel = if (running.isNotEmpty()) {
            context.getString(R.string.notification_action_pause_all)
        } else {
            context.getString(R.string.notification_action_resume_all)
        }
        val actionPending = servicePendingIntent(
            if (running.isNotEmpty()) DownloadService.ACTION_PAUSE_ALL else DownloadService.ACTION_RESUME_ALL,
            if (running.isNotEmpty()) 1 else 2,
        )

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, actionLabel, actionPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(0xFF1D4ED8.toInt())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent, totalBytes <= 0 && running.isNotEmpty())
            .build()
    }

    fun updateForeground(notification: Notification) {
        if (!NotificationPermission.isGranted(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun postCompleted(task: DownloadTask) {
        if (!NotificationPermission.isGranted(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_completed_title))
            .setContentText(task.savedFileName ?: task.fileName)
            .setContentIntent(mainPendingIntent(task.id.hashCode()))
            .setAutoCancel(true)
            .setColor(0xFF16A34A.toInt())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(resultId(task), notification) }
    }

    fun postFailed(task: DownloadTask, message: String) {
        if (!NotificationPermission.isGranted(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_failed_title))
            .setContentText("${task.fileName}：$message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.fileName}\n$message"))
            .setContentIntent(mainPendingIntent(task.id.hashCode()))
            .setAutoCancel(true)
            .setColor(0xFFDC2626.toInt())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(resultId(task), notification) }
    }

    private fun resultId(task: DownloadTask): Int = (task.id.hashCode() and 0xFFFF) + 1000

    private fun mainPendingIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_RESULTS = "download_results"
        const val NOTIFICATION_ID = 1001
    }
}
