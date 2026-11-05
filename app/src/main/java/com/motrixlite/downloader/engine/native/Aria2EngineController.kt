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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * aria2 引擎控制器：
 * - 由 DownloadService 持有；只在存在 aria2 任务时启动 aria2c 子进程；
 * - 通过 JSON-RPC 创建/暂停/恢复/删除任务并轮询进度；
 * - 完成后把 staging 文件交给 DestinationPublisher 发布到 MediaStore/SAF；
 * - 进程停止时清空 aria2 gid，恢复时用 `continue=true` 重新 addUri，利用 .aria2 控制文件续传；
 * - 与 UI 的交互只通过 DownloadRepository，保持任务列表统一。
 */
class Aria2EngineController(
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
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private var started = false
    private var process: Process? = null
    private var rpc: Aria2RpcClient? = null
    private var lastLimitBytes = Long.MIN_VALUE
    private var lastMaxConcurrent = -1
    private var lastFailureAt = 0L
    private val gids = ConcurrentHashMap<String, String>()
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
        runBlocking { shutdownProcess(clearIds = true) }
    }

    // ------------------------------------------------------------------

    private suspend fun tick() {
        val allTasks = repository.tasks.value
        val tasks = allTasks.filter { it.engine == DownloadEngine.ARIA2 }
        val currentIds = tasks.map { it.id }.toSet()
        val deleted = knownTaskIds.filter { it !in currentIds }
        if (deleted.isNotEmpty()) {
            deleted.forEach { id ->
                val gid = gids.remove(id)
                if (gid != null) {
                    rpc?.let { client ->
                        runCatching { client.remove(gid) }
                        runCatching { client.removeDownloadResult(gid) }
                    }
                }
                taskDir(id).deleteRecursively()
            }
            knownTaskIds.removeAll(deleted.toSet())
        }
        knownTaskIds.addAll(currentIds)

        // 进程异常退出：清空 gid，下一轮重建
        val proc = process
        if (proc != null && !proc.isAlive) {
            log("aria2c exited with code ${runCatching { proc.exitValue() }.getOrNull()}")
            process = null
            rpc = null
            gids.clear()
            tasks.filter { !it.state.isFinished }.forEach { repository.clearEngineTaskId(it.id) }
        }

        val unfinished = tasks.filter { !it.state.isFinished }
        if (unfinished.isEmpty()) {
            shutdownProcess(clearIds = false)
            return
        }

        val settings = settingsRepository.settings.value
        val wifiOk = !settings.wifiOnly || networkMonitor.status.value.wifi
        if (!wifiOk) {
            unfinished.filter { it.desiredRunning && it.state != TaskState.WAITING_FOR_WIFI && it.state != TaskState.PAUSED }
                .forEach { task ->
                    gids[task.id]?.let { rpc?.pause(it) }
                    repository.setState(task.id, TaskState.WAITING_FOR_WIFI, "等待 Wi-Fi")
                }
            return
        }
        unfinished.filter { it.state == TaskState.WAITING_FOR_WIFI && it.desiredRunning }
            .forEach { repository.setState(it.id, TaskState.QUEUED, null) }

        val needsKernel = unfinished.any {
            it.desiredRunning && (it.state == TaskState.QUEUED || it.state == TaskState.CONNECTING ||
                it.state == TaskState.DOWNLOADING || it.state == TaskState.RETRYING)
        }
        if (!needsKernel) {
            shutdownProcess(clearIds = true)
            return
        }
        if (System.currentTimeMillis() - lastFailureAt < 10_000L) return
        if (!ensureProcess(settings)) return
        applyGlobalOptions(settings)

        for (task in unfinished) {
            if (task.state == TaskState.SAVING || publishing.contains(task.id)) continue
            if (!task.desiredRunning) {
                val gid = gids[task.id] ?: task.engineTaskId
                if (gid != null) rpc?.pause(gid)
                if (task.state != TaskState.PAUSED) repository.setState(task.id, TaskState.PAUSED, null)
                continue
            }
            if (task.state == TaskState.WAITING_FOR_WIFI || task.state == TaskState.PAUSED) continue

            var gid = gids[task.id]
            if (gid == null && task.engineTaskId != null) {
                gid = task.engineTaskId
                gids[task.id] = gid
            }
            if (gid == null) {
                if (task.state == TaskState.QUEUED || task.state == TaskState.CONNECTING) {
                    addTask(task.id, task.url, task.fileName, task.connections)
                }
                continue
            }
            pollTask(task.id, gid)
        }
    }

    private suspend fun addTask(taskId: String, url: String, fileName: String, connections: Int) {
        val client = rpc ?: return
        val dir = taskDir(taskId)
        if (!dir.exists()) dir.mkdirs()
        val options = mapOf(
            "dir" to dir.absolutePath,
            "out" to fileName,
            "continue" to "true",
            "auto-file-renaming" to "false",
            "allow-overwrite" to "true",
            "max-connection-per-server" to connections.toString(),
            "split" to connections.toString(),
            "min-split-size" to "1M",
            "user-agent" to EngineAvailability.USER_AGENT,
            "summary-interval" to "0",
            "console-log-level" to "warn",
        )
        try {
            val gid = client.addUri(url, options)
            gids[taskId] = gid
            log("addUri($taskId) -> gid=$gid")
            repository.updateEngineInfo(taskId, gid, "added")
            repository.setState(taskId, TaskState.CONNECTING, null)
        } catch (e: Exception) {
            repository.markFailed(taskId, "aria2 添加任务失败：${e.message}")
        }
    }

    private suspend fun pollTask(taskId: String, gid: String) {
        val client = rpc ?: return
        val status = try {
            client.tellStatus(gid)
        } catch (e: Exception) {
            log("tellStatus($gid) error: ${e.message}")
            return
        }
        if (status == null) {
            gids.remove(taskId)
            repository.clearEngineTaskId(taskId)
            if (repository.task(taskId)?.desiredRunning == true) repository.setState(taskId, TaskState.QUEUED, null)
            return
        }
        val rawStatus = status.optString("status")
        val completed = status.optLong("completedLength", 0L)
        val total = status.optLong("totalLength", 0L).takeIf { it > 0L } ?: -1L
        val speed = status.optLong("downloadSpeed", 0L)
        repository.updateProgress(taskId, completed, total, emptyList(), speed)
        repository.updateEngineInfo(taskId, gid, rawStatus)
        val current = repository.task(taskId) ?: return
        when (rawStatus) {
            "active", "waiting" -> {
                if (current.state != TaskState.DOWNLOADING && current.state != TaskState.CONNECTING) {
                    repository.setState(taskId, TaskState.DOWNLOADING, null)
                }
            }

            "paused" -> {
                if (!current.desiredRunning) {
                    if (current.state != TaskState.PAUSED) repository.setState(taskId, TaskState.PAUSED, null)
                } else {
                    runCatching { client.unpause(gid) }
                }
            }

            "complete" -> publishCompleted(taskId, gid)

            "error" -> {
                val message = status.optString("errorMessage").ifBlank { "aria2 下载失败（错误码 ${status.optInt("errorCode")}）" }
                gids.remove(taskId)
                repository.clearEngineTaskId(taskId)
                repository.markFailed(taskId, message)
                client.removeDownloadResult(gid)
            }

            "removed" -> {
                gids.remove(taskId)
                repository.clearEngineTaskId(taskId)
                if (current.desiredRunning) repository.setState(taskId, TaskState.QUEUED, null)
            }
        }
    }

    private fun publishCompleted(taskId: String, gid: String) {
        if (!publishing.add(taskId)) return
        scope.launch {
            try {
                repository.setState(taskId, TaskState.SAVING, null)
                val task = repository.task(taskId) ?: return@launch
                val dir = taskDir(taskId)
                val staged = resolveStagedFile(dir, task.fileName)
                if (staged == null) {
                    repository.markFailed(taskId, "aria2 已完成但 staging 文件不存在，请重试")
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
                gids.remove(taskId)
                rpc?.let { runCatching { it.removeDownloadResult(gid) } }
            }
        }
    }

    private fun resolveStagedFile(dir: File, expectedName: String): File? {
        val direct = File(dir, expectedName)
        if (direct.isFile && direct.length() > 0) return direct
        return dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".aria2") && it.length() > 0 }
            ?.maxByOrNull { it.length() }
    }

    // ------------------------------------------------------------------
    // process
    // ------------------------------------------------------------------

    private suspend fun ensureProcess(settings: com.motrixlite.downloader.data.model.AppSettings): Boolean {
        val alive = process?.isAlive == true && rpc != null
        if (alive) return true
        val binary = EngineAvailability.aria2Binary(context)
        if (binary == null) {
            failActiveTasks("aria2 内核未打包进当前 APK（缺少 nativeLibraryDir/libaria2c.so）")
            lastFailureAt = System.currentTimeMillis()
            return false
        }
        if (!binary.canExecute()) {
            runCatching { binary.setExecutable(true, false) }
        }
        return try {
            if (!engineRootDir.exists()) engineRootDir.mkdirs()
            val port = freePort()
            val secret = UUID.randomUUID().toString()
            val logFile = File(engineRootDir, "aria2.log")
            val args = mutableListOf(
                binary.absolutePath,
                "--enable-rpc=true",
                "--rpc-listen-all=false",
                "--rpc-listen-port=$port",
                "--rpc-secret=$secret",
                "--dir=${engineRootDir.absolutePath}",
                "--continue=true",
                "--auto-file-renaming=false",
                "--allow-overwrite=true",
                "--file-allocation=none",
                "--max-concurrent-downloads=${settings.maxConcurrentTasks}",
                "--max-overall-download-limit=${limitBytes(settings)}",
                "--console-log-level=warn",
                "--log-level=warn",
                "--log=${logFile.absolutePath}",
                "--summary-interval=0",
                "--enable-color=false",
                "--no-conf=true",
            )
            val builder = ProcessBuilder(args)
            builder.redirectErrorStream(true)
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            val newProcess = builder.start()
            process = newProcess
            val client = Aria2RpcClient(httpClient, port, secret)
            var ready = false
            repeat(40) {
                if (!ready) {
                    try {
                        client.version()
                        ready = true
                    } catch (_: Exception) {
                        delay(250)
                    }
                }
            }
            if (!ready) {
                runCatching { newProcess.destroyForcibly() }
                process = null
                throw IOException("aria2c RPC 启动超时")
            }
            rpc = client
            lastLimitBytes = Long.MIN_VALUE
            lastMaxConcurrent = -1
            lastFailureAt = 0L
            log("aria2c started on port $port log=$logFile")
            true
        } catch (e: Exception) {
            lastFailureAt = System.currentTimeMillis()
            process = null
            rpc = null
            log("aria2c start failed: ${e.message}")
            failActiveTasks("aria2 内核启动失败：${e.message}")
            false
        }
    }

    private suspend fun applyGlobalOptions(settings: com.motrixlite.downloader.data.model.AppSettings) {
        val client = rpc ?: return
        val limit = limitBytes(settings)
        if (limit != lastLimitBytes) {
            val options = JSONObject().put("max-overall-download-limit", if (limit <= 0) "0" else limit.toString())
            if (client.changeGlobalOption(options)) lastLimitBytes = limit
        }
        if (settings.maxConcurrentTasks != lastMaxConcurrent) {
            val options = JSONObject().put("max-concurrent-downloads", settings.maxConcurrentTasks.toString())
            if (client.changeGlobalOption(options)) lastMaxConcurrent = settings.maxConcurrentTasks
        }
    }

    private fun limitBytes(settings: com.motrixlite.downloader.data.model.AppSettings): Long =
        settings.speedLimitKbPerSec.toLong() * 1024L

    private fun freePort(): Int = ServerSocket(0).use { socket -> socket.localPort }

    private suspend fun shutdownProcess(clearIds: Boolean) {
        val client = rpc
        val proc = process
        rpc = null
        process = null
        gids.clear()
        if (client != null) {
            runCatching { client.shutdown() }
        }
        if (proc != null) {
            try {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
            } catch (_: Exception) {
                runCatching { proc.destroyForcibly() }
            }
        }
        if (clearIds) {
            repository.tasks.value
                .filter { it.engine == DownloadEngine.ARIA2 && !it.state.isFinished }
                .forEach { repository.clearEngineTaskId(it.id) }
        }
    }

    private fun failActiveTasks(message: String) {
        repository.tasks.value
            .filter { it.engine == DownloadEngine.ARIA2 && !it.state.isFinished && it.desiredRunning }
            .forEach { repository.markFailed(it.id, message) }
    }

    private fun taskDir(taskId: String): File = File(engineRootDir, "tasks/$taskId")

    private fun log(message: String) = android.util.Log.w("Aria2Engine", message)
}
