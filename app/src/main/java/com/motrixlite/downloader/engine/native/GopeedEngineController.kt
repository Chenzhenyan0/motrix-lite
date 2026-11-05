package com.motrixlite.downloader.engine.native

import android.content.Context
import com.motrixlite.downloader.data.DownloadRepository
import com.motrixlite.downloader.data.SettingsRepository
import com.motrixlite.downloader.data.model.DownloadEngine
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.network.NetworkMonitor
import com.motrixlite.downloader.storage.DestinationPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Gopeed 引擎控制器（gomobile AAR，进程内）。
 *
 * - [GopeedBridge] 启动 AAR 内嵌 REST 服务（127.0.0.1 随机端口，X-Api-Token 鉴权）；
 * - Controller 通过 HTTP REST 创建/暂停/恢复/删除任务并轮询状态；
 * - 完成任务后把 Gopeed 下载目录里的文件交给 DestinationPublisher 发布（MediaStore/SAF）；
 * - 只由 DownloadService 持有一个实例；没有 Gopeed 任务时关闭 AAR 服务，避免 Go 线程/端口泄漏。
 *
 * 注意：AAR 与 App 同进程，Go 层 panic/OOM 会直接影响 App；正式构建需保证 AAR 版
 * 本与 App 一起回归测试。Gopeed 当前 REST API 不提供全局限速，限速设置对其不生效。
 */
class GopeedEngineController(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val publisher: DestinationPublisher,
    private val networkMonitor: NetworkMonitor,
    private val engineRootDir: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private var started = false
    private var api: GopeedApi? = null
    private var serverPort = 0
    private var apiToken = ""
    private var lastFailureAt = 0L
    private val knownTaskIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val publishing = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (currentCoroutineContext().isActive) {
                try {
                    tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("tick error: ${e.message}")
                }
                delay(700)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
        runBlocking { stopServer(clearIds = true) }
    }

    // ------------------------------------------------------------------

    private suspend fun tick() {
        val tasks = repository.tasks.value.filter { it.engine == DownloadEngine.GOPEED }
        val currentIds = tasks.map { it.id }.toSet()
        val deleted = knownTaskIds.filter { it !in currentIds }
        if (deleted.isNotEmpty()) {
            val client = api
            deleted.forEach { id ->
                client?.delete(id)
                taskDir(id).deleteRecursively()
            }
            knownTaskIds.removeAll(deleted.toSet())
        }
        knownTaskIds.addAll(currentIds)

        val unfinished = tasks.filter { !it.state.isFinished }
        if (unfinished.isEmpty()) {
            stopServer(clearIds = false)
            return
        }

        val settings = settingsRepository.settings.value
        val wifiOk = !settings.wifiOnly || networkMonitor.status.value.wifi
        if (!wifiOk) {
            unfinished.filter { it.desiredRunning && it.state != TaskState.WAITING_FOR_WIFI && it.state != TaskState.PAUSED }
                .forEach { task ->
                    val id = task.engineTaskId
                    if (id != null) api?.pause(id)
                    repository.setState(task.id, TaskState.WAITING_FOR_WIFI, "等待 Wi-Fi")
                }
            return
        }
        unfinished.filter { it.state == TaskState.WAITING_FOR_WIFI && it.desiredRunning }
            .forEach { repository.setState(it.id, TaskState.QUEUED, null) }

        val needsServer = unfinished.any {
            it.desiredRunning && (it.state == TaskState.QUEUED || it.state == TaskState.CONNECTING ||
                it.state == TaskState.DOWNLOADING || it.state == TaskState.RETRYING)
        }
        if (!needsServer) {
            stopServer(clearIds = true)
            return
        }
        if (System.currentTimeMillis() - lastFailureAt < 10_000L) return
        if (!ensureServer(settings)) return

        val client = api ?: return
        val remoteTasks = try {
            client.tasks()
        } catch (e: Exception) {
            log("list tasks failed: ${e.message}")
            return
        }
        val remoteById = HashMap<String, JSONObject>()
        for (i in 0 until remoteTasks.length()) {
            val item = remoteTasks.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isNotBlank()) remoteById[id] = item
        }

        for (task in unfinished) {
            if (task.state == TaskState.SAVING || publishing.contains(task.id)) continue
            if (!task.desiredRunning) {
                val id = task.engineTaskId
                if (id != null) {
                    val remote = remoteById[id]
                    val status = remote?.optString("status")
                    if (status == "running" || status == "ready" || status == "wait") client.pause(id)
                }
                if (task.state != TaskState.PAUSED) repository.setState(task.id, TaskState.PAUSED, null)
                continue
            }
            if (task.state == TaskState.WAITING_FOR_WIFI || task.state == TaskState.PAUSED) continue

            val engineTaskId = task.engineTaskId
            if (engineTaskId.isNullOrBlank()) {
                if (task.state == TaskState.QUEUED || task.state == TaskState.CONNECTING) {
                    addTask(task.id, task.url, task.fileName, task.connections)
                }
                continue
            }
            val remote = remoteById[engineTaskId]
            if (remote == null) {
                repository.clearEngineTaskId(task.id)
                repository.setState(task.id, TaskState.QUEUED, null)
                continue
            }
            pollTask(task.id, engineTaskId, remote)
        }
    }

    private suspend fun addTask(taskId: String, url: String, fileName: String, connections: Int) {
        val client = api ?: return
        val dir = taskDir(taskId)
        if (!dir.exists()) dir.mkdirs()
        try {
            val gopeedTaskId = client.createTask(url, fileName, dir.absolutePath, connections)
            repository.updateEngineInfo(taskId, gopeedTaskId, "created")
            repository.setState(taskId, TaskState.CONNECTING, null)
        } catch (e: Exception) {
            repository.markFailed(taskId, "Gopeed 创建任务失败：${e.message}")
        }
    }

    private fun pollTask(taskId: String, engineTaskId: String, remote: JSONObject) {
        val status = remote.optString("status")
        val progress = remote.optJSONObject("progress")
        val downloaded = progress?.optLong("downloaded", 0L) ?: 0L
        val speed = progress?.optLong("speed", 0L) ?: 0L
        val total = remote.optJSONObject("meta")
            ?.optJSONObject("res")
            ?.optLong("size", 0L)
            ?.takeIf { it > 0L } ?: -1L
        repository.updateProgress(taskId, downloaded, total, emptyList(), speed)
        repository.updateEngineInfo(taskId, engineTaskId, status)
        val current = repository.task(taskId) ?: return
        when (status) {
            "running" -> {
                if (current.state != TaskState.DOWNLOADING) repository.setState(taskId, TaskState.DOWNLOADING, null)
            }

            "ready", "wait" -> {
                if (current.state != TaskState.CONNECTING) repository.setState(taskId, TaskState.CONNECTING, null)
            }

            "pause" -> {
                if (!current.desiredRunning) {
                    if (current.state != TaskState.PAUSED) repository.setState(taskId, TaskState.PAUSED, null)
                } else {
                    scope.launch { api?.continueTask(engineTaskId) }
                }
            }

            "error" -> {
                repository.clearEngineTaskId(taskId)
                repository.markFailed(taskId, "Gopeed 下载失败（原始状态: error）")
                scope.launch { runCatching { api?.delete(engineTaskId) } }
            }

            "done" -> publishCompleted(taskId, engineTaskId)
        }
    }

    private fun publishCompleted(taskId: String, engineTaskId: String) {
        if (!publishing.add(taskId)) return
        scope.launch {
            try {
                repository.setState(taskId, TaskState.SAVING, null)
                val task = repository.task(taskId) ?: return@launch
                val dir = taskDir(taskId)
                val staged = resolveStagedFile(dir, task.fileName)
                if (staged == null) {
                    repository.markFailed(taskId, "Gopeed 已完成但找不到下载文件，请重试")
                    return@launch
                }
                val result = publisher.publish(task, staged, settingsRepository.settings.value)
                repository.markCompleted(taskId, result.uri, result.displayName)
                dir.deleteRecursively()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repository.markFailed(taskId, "保存文件失败：${e.message}")
            } finally {
                publishing.remove(taskId)
                runCatching { api?.delete(engineTaskId) }
            }
        }
    }

    private fun resolveStagedFile(dir: File, expectedName: String): File? {
        val direct = File(dir, expectedName)
        if (direct.isFile && direct.length() > 0) return direct
        return dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
            .maxByOrNull { it.length() }
    }

    // ------------------------------------------------------------------
    // server
    // ------------------------------------------------------------------

    private suspend fun ensureServer(settings: com.motrixlite.downloader.data.model.AppSettings): Boolean {
        if (serverPort > 0 && api != null) return true
        if (!GopeedBridge.isAvailable()) {
            failActiveTasks("Gopeed AAR 未打包进当前 APK（app/libs/libgopeed.aar 缺失）")
            lastFailureAt = System.currentTimeMillis()
            return false
        }
        return try {
            if (!engineRootDir.exists()) engineRootDir.mkdirs()
            apiToken = UUID.randomUUID().toString()
            val config = JSONObject().apply {
                put("network", "tcp")
                put("address", "127.0.0.1:0")
                put("refreshInterval", 350)
                put("storage", "mem")
                put("storageDir", File(engineRootDir, "storage").absolutePath)
                put("apiToken", apiToken)
                put(
                    "downloadConfig",
                    JSONObject().apply {
                        put("downloadDir", engineRootDir.absolutePath)
                        put("maxRunning", settings.maxConcurrentTasks)
                    },
                )
            }
            val port = GopeedBridge.start(config.toString())
            if (port <= 0) throw IOException("Gopeed 返回非法端口 $port")
            serverPort = port
            val client = GopeedApi(httpClient, port, apiToken)
            var ready = false
            repeat(30) {
                if (!ready) {
                    try {
                        client.info()
                        ready = true
                    } catch (_: Exception) {
                        delay(200)
                    }
                }
            }
            if (!ready) {
                serverPort = 0
                GopeedBridge.stop()
                throw IOException("Gopeed REST 服务启动超时")
            }
            api = client
            lastFailureAt = 0L
            log("Gopeed server started on port $port")
            true
        } catch (e: Exception) {
            lastFailureAt = System.currentTimeMillis()
            serverPort = 0
            api = null
            GopeedBridge.stop()
            failActiveTasks("Gopeed 内核启动失败：${e.message}")
            false
        }
    }

    private suspend fun stopServer(clearIds: Boolean) {
        val oldPort = serverPort
        serverPort = 0
        api = null
        if (oldPort > 0) {
            runCatching { GopeedBridge.stop() }
        }
        if (clearIds) {
            repository.tasks.value
                .filter { it.engine == DownloadEngine.GOPEED && !it.state.isFinished }
                .forEach { repository.clearEngineTaskId(it.id) }
        }
    }

    private fun failActiveTasks(message: String) {
        repository.tasks.value
            .filter { it.engine == DownloadEngine.GOPEED && !it.state.isFinished && it.desiredRunning }
            .forEach { repository.markFailed(it.id, message) }
    }

    private fun taskDir(taskId: String): File = File(engineRootDir, "tasks/$taskId")

    private fun log(message: String) = android.util.Log.w("GopeedEngine", message)
}
