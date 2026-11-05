package com.motrixlite.downloader.data

import com.motrixlite.downloader.data.model.DownloadTask
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 任务持久化：单文件 JSON + 临时文件原子替换。
 * 进程被杀最多丢失最近约 1 秒的进度（重新下载该区间即可，不存在文件损坏问题）。
 */
class TaskStore(private val file: File) {

    private val lock = Any()

    fun load(): List<DownloadTask> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return try {
                TaskJsonCodec.decode(file.readText(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                runCatching {
                    val backup = File(file.parentFile, file.name + ".bad-" + System.currentTimeMillis())
                    file.copyTo(backup, overwrite = true)
                }
                emptyList()
            }
        }
    }

    fun save(tasks: List<DownloadTask>) {
        synchronized(lock) {
            val parent = file.parentFile ?: return
            if (!parent.exists()) parent.mkdirs()
            val tmp = File(parent, file.name + ".tmp")
            tmp.writeText(TaskJsonCodec.encode(tasks), StandardCharsets.UTF_8)
            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
