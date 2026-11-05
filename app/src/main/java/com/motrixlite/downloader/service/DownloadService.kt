package com.motrixlite.downloader.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.motrixlite.downloader.App
import com.motrixlite.downloader.R
import com.motrixlite.downloader.data.RepoEvent
import com.motrixlite.downloader.data.model.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 下载前台服务。
 *
 * - Android 14+：声明 dataSync 类型并传入 foregroundServiceType；
 * - Android 15+：重写 onTimeout，达到系统 6 小时/24 小时限制时优雅暂停任务；
 * - 通知权限被拒绝时仍可启动前台服务，只是通知不显示，UI 会给出降级提示；
 * - 任务全部结束/暂停后自动停止服务并释放 WakeLock。
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: com.motrixlite.downloader.AppContainer
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var coordinator: com.motrixlite.downloader.engine.DownloadCoordinator
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null
    private var foregroundStarted = false
    private var idleSince = 0L

    override fun onCreate() {
        super.onCreate()
        container = (application as App).container
        notificationHelper = NotificationHelper(this)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotrixLite:Download")
            .apply { setReferenceCounted(false) }

        coordinator = com.motrixlite.downloader.engine.DownloadCoordinator(
            repository = container.repository,
            settingsRepository = container.settingsRepository,
            networkMonitor = container.networkMonitor,
            publisher = container.publisher,
            client = container.httpClient,
            rateLimiter = container.rateLimiter,
            stagingDir = container.stagingDir,
        )

        serviceScope.launch {
            container.repository.events.collect { event ->
                when (event) {
                    is RepoEvent.TaskCompleted -> {
                        if (container.settingsRepository.settings.value.notifyOnComplete) {
                            notificationHelper.postCompleted(event.task)
                        }
                    }

                    is RepoEvent.TaskFailed -> {
                        if (container.settingsRepository.settings.value.notifyOnComplete) {
                            notificationHelper.postFailed(event.task, event.message)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundWithNotification()
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            handleStartForegroundFailure()
            return START_NOT_STICKY
        }
        ensureMonitoring()
        serviceScope.launch {
            container.repository.load()
            when (intent?.action) {
                ACTION_PAUSE_ALL -> container.repository.pauseAll()
                ACTION_RESUME_ALL -> container.repository.resumeAllUnfinished()
                else -> Unit
            }
            coordinator.start()
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        if (foregroundStarted) return
        val notification: Notification = notificationHelper.buildForegroundNotification(
            container.repository.tasks.value,
            container.settingsRepository.settings.value,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, type)
        foregroundStarted = true
    }

    private fun handleStartForegroundFailure() {
        serviceScope.launch {
            runCatching {
                container.repository.load()
                container.repository.pauseAll()
                container.repository.postMessage("系统限制了后台前台服务启动，任务已暂停。请打开应用后继续，并按设置页指引配置后台运行权限。")
            }
            stopSelf()
        }
    }

    private fun ensureMonitoring() {
        if (monitorJob != null) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                val tasks = container.repository.tasks.value
                val settings = container.settingsRepository.settings.value
                notificationHelper.updateForeground(
                    notificationHelper.buildForegroundNotification(tasks, settings),
                )
                updateWakeLock(tasks.any { it.state.isRunning })
                val hasWork = tasks.any { it.state.needsService }
                if (hasWork) {
                    idleSince = 0L
                } else {
                    if (idleSince == 0L) idleSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - idleSince > IDLE_STOP_DELAY_MS) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        foregroundStarted = false
                        stopSelf()
                        break
                    }
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun updateWakeLock(needed: Boolean) {
        val lock = wakeLock ?: return
        runCatching {
            if (needed && !lock.isHeld) {
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            } else if (!needed && lock.isHeld) {
                lock.release()
            }
        }
    }

    /** Android 14 起对 shortService 的超时回调，本项目未使用该类型，保留兼容。 */
    override fun onTimeout(startId: Int) {
        handleForegroundTimeout()
    }

    /** Android 15 起 dataSync 前台服务超过 6 小时/24 小时的回调。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            handleForegroundTimeout()
        }
    }

    private fun handleForegroundTimeout() {
        serviceScope.launch {
            runCatching {
                container.repository.load()
                container.repository.pauseAll()
                container.repository.postMessage(
                    "Android 15 前台服务已达系统时限（dataSync 类型 6 小时/24 小时），任务已暂停；重新打开应用即可继续。",
                )
            }
            updateWakeLock(false)
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        coordinator.stop()
        monitorJob?.cancel()
        updateWakeLock(false)
        container.applicationScope.launch { runCatching { container.repository.flush() } }
        serviceScope.cancel()
        wakeLock = null
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DownloadService"
        private const val IDLE_STOP_DELAY_MS = 4_000L
        private const val MONITOR_INTERVAL_MS = 800L
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        const val ACTION_ENSURE = "com.motrixlite.downloader.action.ENSURE"
        const val ACTION_PAUSE_ALL = "com.motrixlite.downloader.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.motrixlite.downloader.action.RESUME_ALL"
    }
}
