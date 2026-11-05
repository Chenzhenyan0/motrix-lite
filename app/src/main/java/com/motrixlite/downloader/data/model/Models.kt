package com.motrixlite.downloader.data.model

/**
 * 下载任务状态机。
 *
 * QUEUED -> CONNECTING -> DOWNLOADING -> SAVING -> COMPLETED
 *              |               |
 *              +-> RETRYING ---+--> FAILED
 * 任意活动态 -> PAUSED (用户暂停 / 任务被系统中断)
 * 限流策略触发 -> WAITING_FOR_WIFI
 */
enum class TaskState {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    WAITING_FOR_WIFI,
    RETRYING,
    SAVING,
    COMPLETED,
    FAILED;

    /** 真正在消耗网络/磁盘的活动状态（用于通知聚合进度）。 */
    val isRunning: Boolean
        get() = this == CONNECTING || this == DOWNLOADING || this == RETRYING || this == SAVING

    /** 是否还需要前台服务保持运行。 */
    val needsService: Boolean
        get() = this == QUEUED || isRunning || this == WAITING_FOR_WIFI

    val isFinished: Boolean
        get() = this == COMPLETED || this == FAILED
}

/**
 * 一个分段。[end] 为 -1 时表示服务器没有返回总长度（单连接、不可分段）。
 * 分段区间为闭区间 [start, end]。
 */
data class Segment(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long = 0L,
) {
    val length: Long
        get() = if (end < 0) -1L else (end - start + 1)

    fun isComplete(): Boolean = if (end < 0) false else downloaded >= length
}

enum class SaveMode {
    /** 保存到系统「下载」目录 Download/MotrixLite（MediaStore，无需存储权限）。 */
    MEDIASTORE,

    /** 保存到用户通过 SAF 选择的目录。 */
    SAF,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 下载引擎。BUILTIN 为项目自研 OkHttp 内核；GOPEED/ARIA2 为新增的原生内核。
 * 任务创建时固定，之后不随设置切换而改变。
 */
enum class DownloadEngine {
    BUILTIN,
    GOPEED,
    ARIA2;

    val displayName: String
        get() = when (this) {
            BUILTIN -> "内置引擎"
            GOPEED -> "Gopeed"
            ARIA2 -> "aria2"
        }
}

data class AppSettings(
    val maxConcurrentTasks: Int = 3,
    val connectionsPerTask: Int = 8,
    /** 0 表示不限速，单位 KB/s，作用于全局所有任务。 */
    val speedLimitKbPerSec: Int = 0,
    val wifiOnly: Boolean = false,
    val autoResumeUnfinished: Boolean = true,
    val notifyOnComplete: Boolean = true,
    val saveMode: SaveMode = SaveMode.MEDIASTORE,
    val safTreeUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationPermissionAsked: Boolean = false,
    val hideSetupCard: Boolean = false,
    /** 新建任务默认使用的下载引擎；已有任务保留各自引擎。 */
    val downloadEngine: DownloadEngine = DownloadEngine.BUILTIN,
)

data class DownloadTask(
    val id: String,
    val url: String,
    val finalUrl: String? = null,
    val fileName: String,
    /** 用户手动指定过文件名时不再被服务器元数据覆盖。 */
    val fileNameLocked: Boolean = false,
    val mimeType: String = "application/octet-stream",
    /** -1 表示服务器未返回总长度。 */
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val state: TaskState = TaskState.QUEUED,
    val segments: List<Segment> = emptyList(),
    val supportsRanges: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null,
    val connections: Int = 8,
    val error: String? = null,
    val retryCount: Int = 0,
    /** 用户期望该任务处于运行状态；用于 App 重启后决定是否自动恢复。 */
    val desiredRunning: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** 完成后的 content:// 或 SAF uri。 */
    val destinationUri: String? = null,
    /** 保存到磁盘的实际文件名（可能因重名被系统追加后缀）。 */
    val savedFileName: String? = null,
    /** 下载中的临时文件名（位于应用专属目录，完成后删除）。 */
    val stagingName: String,
    val saveMode: SaveMode = SaveMode.MEDIASTORE,
    val safTreeUri: String? = null,
    /** 固定于创建任务时；切换设置不影响已有任务。 */
    val engine: DownloadEngine = DownloadEngine.BUILTIN,
    /** 原生引擎内部任务 ID（aria2 gid / Gopeed task id）；进程重启后会清空重建。 */
    val engineTaskId: String? = null,
    /** 原生引擎的原始状态字符串，便于诊断。 */
    val engineStatus: String? = null,

    // ------- 仅运行期字段，不参与持久化 -------
    val speedBytesPerSec: Long = 0L,
    val speedHistory: List<Long> = emptyList(),
    /** -1 表示未处于保存阶段；0..1 表示保存到 MediaStore/SAF 的进度。 */
    val publishProgress: Float = -1f,
) {
    val hasKnownSize: Boolean
        get() = totalBytes > 0L

    val progressFraction: Float
        get() = when {
            totalBytes > 0L -> (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            state == TaskState.COMPLETED -> 1f
            else -> 0f
        }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase().take(5)

    val isNativeEngine: Boolean
        get() = engine != DownloadEngine.BUILTIN

    val displaySize: String
        get() = when {
            totalBytes > 0L -> "$downloadedBytes/$totalBytes"
            else -> downloadedBytes.toString()
        }
}
