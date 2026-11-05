package com.motrixlite.downloader.data

import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.Segment
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.engine.FileNameUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 任务仓库：内存 StateFlow 为唯一数据源，JSON 文件负责持久化。
 * UI 与前台服务同进程共享该实例（由 AppContainer 提供）。
 */
class DownloadRepository(
    private val store: TaskStore,
    val stagingDir: File,
    private val scope: CoroutineScope,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<RepoEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RepoEvent> = _events.asSharedFlow()

    private val loadMutex = Mutex()
    private val persistMutex = Mutex()
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val saveJob: Job

    init {
        saveJob = scope.launch {
            for (ignored in saveSignal) {
                delay(SAVE_DEBOUNCE_MS)
                persist()
            }
        }
        if (!stagingDir.exists()) stagingDir.mkdirs()
    }

    // ------------------------------------------------------------------
    // load / recovery
    // ------------------------------------------------------------------

    suspend fun load() {
        loadMutex.withLock {
            if (_loaded.value) return
            val loadedTasks = withContext(Dispatchers.IO) { store.load() }
            _tasks.value = loadedTasks.map(::recoverAfterRestart)
            _loaded.value = true
            requestSave()
        }
    }

    /**
     * 进程重启后：
     * - 上次处于运行态且用户希望运行 -> 重新排队；
     * - 用户此前暂停/失败/完成的状态保持不变；
     * - 速度、图表等运行期字段清零。
     */
    private fun recoverAfterRestart(task: DownloadTask): DownloadTask {
        val active = task.state == TaskState.QUEUED ||
            task.state == TaskState.CONNECTING ||
            task.state == TaskState.DOWNLOADING ||
            task.state == TaskState.RETRYING ||
            task.state == TaskState.SAVING ||
            task.state == TaskState.WAITING_FOR_WIFI
        val recoveredState = when {
            active && task.desiredRunning -> TaskState.QUEUED
            active -> TaskState.PAUSED
            else -> task.state
        }
        return task.copy(
            state = recoveredState,
            speedBytesPerSec = 0L,
            speedHistory = emptyList(),
            publishProgress = -1f,
            error = if (recoveredState == TaskState.QUEUED) null else task.error,
            // 原生引擎的进程内 ID 在进程重启后必然失效；清空后由 EngineController 重新 addUri/创建任务。
            engineTaskId = if (task.isNativeEngine) null else task.engineTaskId,
            engineStatus = if (task.isNativeEngine) null else task.engineStatus,
        )
    }

    // ------------------------------------------------------------------
    // queries
    // ------------------------------------------------------------------

    fun task(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun stagingFile(task: DownloadTask): File = File(stagingDir, task.stagingName)

    fun hasServiceTasks(): Boolean = _tasks.value.any { it.state.needsService }

    // ------------------------------------------------------------------
    // task lifecycle
    // ------------------------------------------------------------------

    fun createTask(url: String, customFileName: String?, settings: AppSettings): DownloadTask {
        val trimmed = url.trim()
        val id = UUID.randomUUID().toString()
        val locked = !customFileName.isNullOrBlank()
        val rawName = if (locked) FileNameUtils.sanitize(customFileName!!) else FileNameUtils.suggestName(trimmed)
        val mime = if (rawName.contains('.')) {
            FileNameUtils.mimeForExtension(rawName.substringAfterLast('.'))
        } else {
            "application/octet-stream"
        }
        val task = DownloadTask(
            id = id,
            url = trimmed,
            fileName = FileNameUtils.ensureExtension(rawName, mime),
            fileNameLocked = locked,
            mimeType = mime,
            connections = settings.connectionsPerTask,
            stagingName = "$id.part",
            state = TaskState.QUEUED,
            desiredRunning = true,
            createdAt = System.currentTimeMillis(),
            saveMode = settings.saveMode,
            safTreeUri = if (settings.saveMode == SaveMode.SAF) settings.safTreeUri else null,
            engine = settings.downloadEngine,
        )
        _tasks.update { it + task }
        persistNow()
        _events.tryEmit(RepoEvent.TaskAdded(task))
        return task
    }

    fun setState(id: String, state: TaskState, error: String? = null) {
        mutate(id) {
            it.copy(
                state = state,
                error = error,
                speedBytesPerSec = if (state.isRunning || state == TaskState.QUEUED) it.speedBytesPerSec else 0L,
            )
        }
        requestSave()
    }

    fun updateProgress(id: String, downloaded: Long, total: Long, segments: List<Segment>, speed: Long) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id != id) {
                    task
                } else {
                    task.copy(
                        downloadedBytes = downloaded,
                        totalBytes = if (total >= 0L) total else task.totalBytes,
                        segments = segments,
                        speedBytesPerSec = speed,
                        speedHistory = (task.speedHistory + speed).takeLast(SPEED_HISTORY_SIZE),
                    )
                }
            }
        }
        requestSave()
    }

    fun updatePublishProgress(id: String, progress: Float) {
        mutate(id) { it.copy(publishProgress = progress) }
        requestSave()
    }

    /** 下载阶段结束后，把探测到的文件名/大小/分段信息回写到任务。 */
    fun applyDownloadResult(id: String, source: DownloadTask) {
        mutate(id) { task ->
            task.copy(
                finalUrl = source.finalUrl,
                fileName = source.fileName,
                mimeType = source.mimeType,
                totalBytes = source.totalBytes,
                supportsRanges = source.supportsRanges,
                etag = source.etag,
                lastModified = source.lastModified,
                downloadedBytes = source.downloadedBytes,
                segments = source.segments,
            )
        }
        requestSave()
    }

    /** 原生引擎的任务 ID / 原始状态回写（aria2 gid、Gopeed task id 等）。 */
    fun updateEngineInfo(id: String, engineTaskId: String? = null, engineStatus: String? = null) {
        mutate(id) { task ->
            task.copy(
                engineTaskId = engineTaskId ?: task.engineTaskId,
                engineStatus = engineStatus ?: task.engineStatus,
            )
        }
        requestSave()
    }

    /** 原生引擎（aria2/Gopeed）在服务重启后 ID 会失效，需要清空后重新 add。 */
    fun clearEngineTaskId(id: String) {
        mutate(id) { it.copy(engineTaskId = null, engineStatus = null) }
        requestSave()
    }

    fun pause(id: String) {
        mutate(id) {
            it.copy(
                state = TaskState.PAUSED,
                desiredRunning = false,
                speedBytesPerSec = 0L,
                error = null,
            )
        }
        persistNow()
    }

    fun resume(id: String) {
        mutate(id) {
            if (it.state == TaskState.COMPLETED) {
                it
            } else {
                it.copy(
                    state = TaskState.QUEUED,
                    desiredRunning = true,
                    error = null,
                    retryCount = 0,
                    speedBytesPerSec = 0L,
                )
            }
        }
        persistNow()
    }

    fun retry(id: String) = resume(id)

    fun pauseAll() {
        val ids = _tasks.value
            .filter { it.state == TaskState.QUEUED || it.state.isRunning || it.state == TaskState.WAITING_FOR_WIFI }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return
        _tasks.update { list ->
            list.map { task ->
                if (task.id in ids) {
                    task.copy(state = TaskState.PAUSED, desiredRunning = false, speedBytesPerSec = 0L)
                } else {
                    task
                }
            }
        }
        persistNow()
    }

    fun resumeAllUnfinished() {
        var changed = false
        _tasks.update { list ->
            list.map { task ->
                if (task.state == TaskState.PAUSED || task.state == TaskState.FAILED) {
                    changed = true
                    task.copy(
                        state = TaskState.QUEUED,
                        desiredRunning = true,
                        error = null,
                        retryCount = 0,
                        speedBytesPerSec = 0L,
                    )
                } else {
                    task
                }
            }
        }
        if (changed) persistNow()
    }

    fun markCompleted(id: String, destinationUri: String, savedFileName: String?) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id != id) {
                    task
                } else {
                    task.copy(
                        state = TaskState.COMPLETED,
                        completedAt = System.currentTimeMillis(),
                        destinationUri = destinationUri,
                        savedFileName = savedFileName,
                        desiredRunning = false,
                        speedBytesPerSec = 0L,
                        publishProgress = -1f,
                        error = null,
                        engineTaskId = null,
                        engineStatus = null,
                        downloadedBytes = if (task.totalBytes > 0) task.totalBytes else task.downloadedBytes,
                    )
                }
            }
        }
        persistNow()
        task(id)?.let { _events.tryEmit(RepoEvent.TaskCompleted(it)) }
    }

    fun markFailed(id: String, message: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id != id) {
                    task
                } else {
                    task.copy(
                        state = TaskState.FAILED,
                        error = message,
                        desiredRunning = false,
                        speedBytesPerSec = 0L,
                        publishProgress = -1f,
                    )
                }
            }
        }
        persistNow()
        task(id)?.let { _events.tryEmit(RepoEvent.TaskFailed(it, message)) }
    }

    fun removeTask(id: String) {
        val task = task(id) ?: return
        _tasks.update { list -> list.filterNot { it.id == id } }
        scope.launch(Dispatchers.IO) {
            runCatching { stagingFile(task).delete() }
        }
        persistNow()
    }

    fun postMessage(text: String) {
        _events.tryEmit(RepoEvent.Message(text))
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    suspend fun flush() = persist()

    private fun mutate(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { task -> if (task.id == id) transform(task) else task }
        }
    }

    private fun requestSave() {
        saveSignal.trySend(Unit)
    }

    private fun persistNow() {
        requestSave()
        scope.launch { persist() }
    }

    private suspend fun persist() {
        persistMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { store.save(_tasks.value) }
            }
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 700L
        private const val SPEED_HISTORY_SIZE = 30
    }
}
