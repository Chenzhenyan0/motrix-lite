package com.motrixlite.downloader.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motrixlite.downloader.App
import com.motrixlite.downloader.data.RepoEvent
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.data.model.ThemeMode
import com.motrixlite.downloader.engine.HttpProbe
import com.motrixlite.downloader.util.FileOpener
import com.motrixlite.downloader.util.HyperOSGuide
import com.motrixlite.downloader.util.NotificationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class NeedNotificationPermission(val firstTime: Boolean) : UiEvent
}

@SuppressLint("StaticFieldLeak")
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container
    private val context = application.applicationContext

    val tasks: StateFlow<List<DownloadTask>> = container.repository.tasks
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
    val loaded: StateFlow<Boolean> = container.repository.loaded
    val networkStatus = container.networkMonitor.status

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _notificationGranted = MutableStateFlow(NotificationPermission.isGranted(context))
    val notificationGranted: StateFlow<Boolean> = _notificationGranted.asStateFlow()

    private val _batteryOptimized = MutableStateFlow(!HyperOSGuide.isIgnoringBatteryOptimizations(context))
    val batteryOptimized: StateFlow<Boolean> = _batteryOptimized.asStateFlow()

    init {
        viewModelScope.launch {
            container.repository.events.collect { event ->
                when (event) {
                    is RepoEvent.TaskFailed -> _uiEvents.emit(UiEvent.Message("下载失败：${event.message}"))
                    is RepoEvent.Message -> _uiEvents.emit(UiEvent.Message(event.text))
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            container.repository.load()
            if (container.settingsRepository.settings.value.autoResumeUnfinished &&
                container.repository.hasServiceTasks()
            ) {
                container.serviceController.ensureRunning()
            }
        }
        viewModelScope.launch {
            container.repository.load()
            if (NotificationPermission.canRequestRuntimePermission(context) &&
                !container.settingsRepository.settings.value.notificationPermissionAsked
            ) {
                _uiEvents.emit(UiEvent.NeedNotificationPermission(firstTime = true))
            }
        }
    }

    fun markNotificationPermissionAsked() = container.settingsRepository.markNotificationPermissionAsked()

    fun refreshSystemState() {
        _notificationGranted.value = NotificationPermission.isGranted(context)
        _batteryOptimized.value = !HyperOSGuide.isIgnoringBatteryOptimizations(context)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        refreshSystemState()
        if (!granted) {
            emit("通知权限被拒绝：后台下载仍会继续，但通知栏进度与快捷操作不可见；可到「设置 → 通知」中重新开启")
        }
    }

    suspend fun probeLink(url: String): Result<HttpProbe.Metadata> = withContext(Dispatchers.IO) {
        runCatching { container.httpProbe.probe(url.trim()) }
    }

    fun startDownload(url: String, customFileName: String?) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            emit("当前版本仅支持 HTTP/HTTPS 直链下载；磁力链接与 BT 种子暂不支持")
            return
        }
        repositoryCreate(trimmed, customFileName)
        container.serviceController.ensureRunning()
        if (!NotificationPermission.isGranted(context)) {
            emit("已开始下载。通知权限未开启：锁屏后进度通知不可见，建议在设置中开启通知权限")
        }
    }

    private fun repositoryCreate(url: String, customFileName: String?) {
        container.repository.createTask(url, customFileName, container.settingsRepository.settings.value)
    }

    fun togglePause(task: DownloadTask) {
        when (task.state) {
            TaskState.PAUSED, TaskState.FAILED -> resume(task)
            TaskState.COMPLETED -> Unit
            else -> pause(task)
        }
    }

    fun pause(task: DownloadTask) = container.repository.pause(task.id)

    fun resume(task: DownloadTask) {
        container.repository.resume(task.id)
        container.serviceController.ensureRunning()
    }

    fun retry(task: DownloadTask) {
        container.repository.retry(task.id)
        container.serviceController.ensureRunning()
    }

    fun pauseAll() = container.repository.pauseAll()

    fun resumeAll() {
        container.repository.resumeAllUnfinished()
        container.serviceController.ensureRunning()
    }

    fun deleteTask(task: DownloadTask, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile) {
                runCatching { container.publisher.delete(task.destinationUri) }
            }
            container.repository.removeTask(task.id)
            emit("已删除任务${if (deleteFile) "及文件" else ""}")
        }
    }

    fun clearCompletedTasks() {
        tasks.value.filter { it.state == TaskState.COMPLETED }.forEach { completed ->
            viewModelScope.launch(Dispatchers.IO) {
                container.repository.removeTask(completed.id)
            }
        }
        emit("已清理完成任务（文件保留）")
    }

    fun openFile(task: DownloadTask) {
        if (!FileOpener.open(context, task)) {
            emit("无法打开文件：文件可能已被移动/删除，或系统没有可用的查看器")
        }
    }

    // ---------------- settings ----------------

    fun setMaxConcurrentTasks(value: Int) = container.settingsRepository.setMaxConcurrentTasks(value)

    fun setConnections(value: Int) = container.settingsRepository.setConnections(value)

    fun setSpeedLimitKb(value: Int) = container.settingsRepository.setSpeedLimitKb(value)

    fun setWifiOnly(value: Boolean) = container.settingsRepository.setWifiOnly(value)

    fun setAutoResume(value: Boolean) = container.settingsRepository.setAutoResume(value)

    fun setNotifyOnComplete(value: Boolean) = container.settingsRepository.setNotifyOnComplete(value)

    fun setThemeMode(mode: ThemeMode) = container.settingsRepository.setThemeMode(mode)

    fun setSaveMode(mode: SaveMode) = container.settingsRepository.setSaveMode(mode)

    fun setSafFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // 某些文件管理器不授予持久化权限，发布时若失败会提示重新选择。
        }
        container.settingsRepository.setSafTreeUri(uri.toString())
        emit("已选择自定义保存文件夹")
    }

    fun hideSetupCard() = container.settingsRepository.setHideSetupCard(true)

    // ---------------- system settings jumps ----------------

    fun openBatteryOptimizationSettings() {
        if (!HyperOSGuide.openBatteryOptimizationSettings(context)) {
            emit("无法打开电池优化设置，请手动在系统设置中开启「不限制耗电」")
        }
    }

    fun openHyperOsBackgroundSettings() {
        if (!HyperOSGuide.openHyperOsAutostartSettings(context)) {
            emit("未能直接打开 HyperOS 后台设置，请手动在 设置 → 应用设置 → 应用管理 中配置「自启动」与「省电策略」")
        }
    }

    fun openNotificationSettings() {
        if (!HyperOSGuide.openNotificationSettings(context)) {
            emit("无法打开通知设置，请手动在系统设置中开启通知")
        }
    }

    fun openWifiSettings() {
        HyperOSGuide.openWifiSettings(context)
    }

    private fun emit(text: String) {
        _uiEvents.tryEmit(UiEvent.Message(text))
    }
}
