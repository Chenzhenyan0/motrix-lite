package com.motrixlite.downloader.engine

import com.motrixlite.downloader.data.DownloadRepository
import com.motrixlite.downloader.data.SettingsRepository
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.network.NetworkMonitor
import com.motrixlite.downloader.storage.DestinationPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载调度器：任务队列、并发上限、Wi-Fi 策略、自动重试、完成发布。
 *
 * 运行在前台服务进程内；UI 通过 repository 状态驱动，双方不直接互相引用。
 */
class DownloadCoordinator(
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val publisher: DestinationPublisher,
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter,
    private val stagingDir: File,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeDownloaders = ConcurrentHashMap<String, TaskDownloader>()
    private val kickLock = Any()
    private var scope: CoroutineScope? = null
    private var settings: AppSettings = AppSettings()

    fun start() {
        if (scope != null) return
        val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = coordinatorScope
        val initial = settingsRepository.settings.value
        settings = initial
        rateLimiter.limitBytesPerSecond = initial.speedLimitKbPerSec.toLong() * 1024L

        coordinatorScope.launch {
            repository.load()
            kick()
        }
        coordinatorScope.launch {
            settingsRepository.settings.collect { value ->
                settings = value
                rateLimiter.limitBytesPerSecond = value.speedLimitKbPerSec.toLong() * 1024L
                kick()
            }
        }
        coordinatorScope.launch {
            repository.tasks.collect { kick() }
        }
        coordinatorScope.launch {
            networkMonitor.status.collect { kick() }
        }
    }

    fun stop() {
        val current = scope
        scope = null
        current?.cancel()
        activeDownloaders.values.forEach { downloader -> runCatching { downloader.cancelActiveCalls() } }
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        activeDownloaders.clear()
    }

    // ------------------------------------------------------------------
    // scheduling
    // ------------------------------------------------------------------

    private fun kick() {
        synchronized(kickLock) {
            val currentScope = scope ?: return
            val tasks = repository.tasks.value
            val byId = tasks.associateBy { it.id }
            val network = networkMonitor.status.value
            val networkAllowed = !settings.wifiOnly || network.wifi

            // 1. 取消不再需要运行的任务（被用户暂停/删除/已完成）
            val cancelIds = activeJobs.keys.filter { id ->
                val task = byId[id]
                task == null || task.state.isFinished || !task.desiredRunning || task.state == TaskState.PAUSED
            }
            cancelIds.forEach { id ->
                activeDownloaders.remove(id)?.cancelActiveCalls()
                activeJobs.remove(id)?.cancel()
            }

            // 2. Wi-Fi 恢复后，把等待中的任务重新排队
            if (networkAllowed) {
                tasks.filter { it.desiredRunning && it.state == TaskState.WAITING_FOR_WIFI }
                    .forEach { repository.setState(it.id, TaskState.QUEUED, null) }
            }

            // 3. 按并发上限启动排队任务
            var slots = settings.maxConcurrentTasks - activeJobs.size
            if (slots <= 0) return@synchronized
            for (task in tasks) {
                if (slots <= 0) break
                if (!task.desiredRunning || task.state != TaskState.QUEUED) continue
                if (activeJobs.containsKey(task.id)) continue
                if (settings.wifiOnly && !network.wifi) {
                    repository.setState(task.id, TaskState.WAITING_FOR_WIFI, "等待 Wi-Fi 网络")
                    continue
                }
                activeJobs[task.id] = currentScope.launch { runTask(task.id) }
                repository.setState(task.id, TaskState.CONNECTING, null)
                slots--
            }
        }
    }

    // ------------------------------------------------------------------
    // one task
    // ------------------------------------------------------------------

    private suspend fun runTask(id: String) {
        try {
            var autoRetry = 0
            while (true) {
                val task = repository.task(id) ?: return
                if (!task.desiredRunning || task.state.isFinished || task.state == TaskState.PAUSED) return

                val downloader = TaskDownloader(
                    initialTask = task,
                    client = client,
                    stagingFile = File(stagingDir, task.stagingName),
                    rateLimiter = rateLimiter,
                    onProgress = { downloaded, total, speed, segments ->
                        repository.updateProgress(id, downloaded, total, segments, speed)
                    },
                    onState = { state ->
                        if (state == TaskState.DOWNLOADING && repository.task(id)?.state != TaskState.DOWNLOADING) {
                            repository.setState(id, TaskState.DOWNLOADING)
                        }
                    },
                )
                activeDownloaders[id] = downloader

                when (val result = downloader.run()) {
                    is DownloadResult.Completed -> {
                        repository.applyDownloadResult(id, result.task)
                        repository.setState(id, TaskState.SAVING)
                        publishResult(id)
                        return
                    }

                    is DownloadResult.Paused -> {
                        repository.updateProgress(id, result.task.downloadedBytes, result.task.totalBytes, result.task.segments, 0L)
                        val current = repository.task(id)
                        if (current != null && current.desiredRunning && !current.state.isFinished) {
                            repository.setState(id, TaskState.PAUSED)
                        }
                        return
                    }

                    is DownloadResult.Failed -> {
                        repository.updateProgress(id, result.task.downloadedBytes, result.task.totalBytes, result.task.segments, 0L)
                        val current = repository.task(id) ?: return
                        if (!current.desiredRunning) return
                        if (result.retryable && autoRetry < AUTO_RETRY_DELAYS.size) {
                            repository.setState(id, TaskState.RETRYING, result.message)
                            delay(AUTO_RETRY_DELAYS[autoRetry])
                            autoRetry++
                            repository.setState(id, TaskState.QUEUED, null)
                            continue
                        }
                        repository.markFailed(id, result.message)
                        return
                    }
                }
            }
        } catch (e: CancellationException) {
            val current = repository.task(id)
            if (current != null && current.desiredRunning && !current.state.isFinished && current.state != TaskState.PAUSED) {
                repository.setState(id, TaskState.PAUSED)
            }
            throw e
        } catch (e: Exception) {
            repository.markFailed(id, "下载异常：${e.message ?: e.javaClass.simpleName}")
        } finally {
            activeDownloaders.remove(id)
            activeJobs.remove(id)
            kick()
        }
    }

    private suspend fun publishResult(id: String) {
        try {
            val task = repository.task(id) ?: return
            val stagingFile = File(stagingDir, task.stagingName)
            val result = publisher.publish(task, stagingFile, settingsRepository.settings.value) { progress ->
                repository.updatePublishProgress(id, progress)
            }
            repository.markCompleted(id, result.uri, result.displayName)
            runCatching { stagingFile.delete() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.markFailed(id, "保存文件失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        private val AUTO_RETRY_DELAYS = longArrayOf(3_000L, 8_000L, 15_000L)
    }
}
