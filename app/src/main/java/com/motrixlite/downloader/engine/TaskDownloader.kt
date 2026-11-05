package com.motrixlite.downloader.engine

import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.Segment
import com.motrixlite.downloader.data.model.TaskState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

sealed interface DownloadResult {
    data class Completed(val task: DownloadTask) : DownloadResult

    data class Paused(val task: DownloadTask) : DownloadResult

    data class Failed(
        val task: DownloadTask,
        val message: String,
        val retryable: Boolean,
    ) : DownloadResult
}

class ServerChangedException(message: String) : IOException(message)

class FatalHttpException(message: String) : IOException(message)

class RangeUnsupportedException : IOException("服务器忽略 Range 请求")

/**
 * 单个任务的多连接分段下载器。
 *
 * 设计要点：
 * - 下载数据写入应用专属目录中的 staging 文件（RandomAccessFile 随机写），
 *   完成后再由 [com.motrixlite.downloader.storage.DestinationPublisher] 发布到 MediaStore / SAF，
 *   这样断点续传不依赖第三方 ContentProvider 是否支持 seek；
 * - 每个分段使用独立 HTTP Range 请求，协程取消即暂停，进度落盘由上层 Repository 负责；
 * - 失败按分段重试，服务器文件变化（ETag/Last-Modified/总长度不一致）视为不可恢复错误；
 * - 服务器忽略 Range 时自动降级为单连接。
 */
class TaskDownloader(
    initialTask: DownloadTask,
    private val client: OkHttpClient,
    private val stagingFile: File,
    private val rateLimiter: RateLimiter,
    private val onProgress: (downloaded: Long, total: Long, speedBytesPerSec: Long, segments: List<Segment>) -> Unit = { _, _, _, _ -> },
    private val onState: (TaskState) -> Unit = {},
    private val maxSegmentRetries: Int = 3,
    private val probe: HttpProbe = HttpProbe(client),
) {
    private var task: DownloadTask = initialTask
    private var segments: List<Segment> = initialTask.segments
    private var progress: AtomicLongArray = AtomicLongArray(0)
    private var speedMeter: SpeedMeter = SpeedMeter()
    private var lastReportAt: Long = 0L
    private val activeCalls: MutableSet<Call> = ConcurrentHashMap.newKeySet<Call>()

    suspend fun run(): DownloadResult {
        try {
            onState(TaskState.CONNECTING)
            reportProgress(force = true)
            val metadata = try {
                probe.probe(task.finalUrl ?: task.url, task.fileName)
            } catch (e: ProbeException) {
                return DownloadResult.Failed(
                    snapshot(TaskState.FAILED, e.message),
                    e.message ?: "解析下载链接失败",
                    e.retryable,
                )
            } catch (e: IOException) {
                return if (!currentCoroutineContext().isActive) {
                    DownloadResult.Paused(snapshot(TaskState.PAUSED))
                } else {
                    DownloadResult.Failed(snapshot(TaskState.FAILED, friendlyError(e)), friendlyError(e), true)
                }
            }

            prepare(metadata)

            if (task.totalBytes == 0L) {
                stagingFile.parentFile?.mkdirs()
                stagingFile.writeBytes(ByteArray(0))
                val finished = snapshot(TaskState.COMPLETED)
                return DownloadResult.Completed(finished)
            }

            stagingFile.parentFile?.mkdirs()
            segments = task.segments
            initProgress()
            ensureStagingFileLength()
            speedMeter = SpeedMeter()
            onState(TaskState.DOWNLOADING)
            downloadWithFallback()
            val finished = finalizeTask()
            reportProgress(force = true)
            return DownloadResult.Completed(finished)
        } catch (e: CancellationException) {
            return DownloadResult.Paused(snapshot(TaskState.PAUSED))
        } catch (e: ServerChangedException) {
            val message = e.message ?: "服务器文件已变化"
            return DownloadResult.Failed(snapshot(TaskState.FAILED, message), message, false)
        } catch (e: FatalHttpException) {
            val message = e.message ?: "下载失败"
            return DownloadResult.Failed(snapshot(TaskState.FAILED, message), message, false)
        } catch (e: IOException) {
            return if (!currentCoroutineContext().isActive) {
                DownloadResult.Paused(snapshot(TaskState.PAUSED))
            } else {
                val message = friendlyError(e)
                DownloadResult.Failed(snapshot(TaskState.FAILED, message), message, true)
            }
        } catch (e: Exception) {
            val message = e.message ?: "未知错误"
            return DownloadResult.Failed(snapshot(TaskState.FAILED, message), message, true)
        } finally {
            cancelActiveCalls()
            withContext(NonCancellable) {
                runCatching { reportProgress(force = true) }
            }
        }
    }

    /** 协调器在暂停/删除任务时先调用此方法，尽快中断阻塞中的 HTTP 读取。 */
    fun cancelActiveCalls() {
        activeCalls.forEach { call -> runCatching { call.cancel() } }
    }

    // ------------------------------------------------------------------
    // prepare
    // ------------------------------------------------------------------

    private fun prepare(metadata: HttpProbe.Metadata) {
        val previousDownloaded = task.downloadedBytes
        val previousTotal = task.totalBytes
        val previousEtag = task.etag
        val previousLastModified = task.lastModified
        val stagingExists = stagingFile.exists()

        var fileChanged = false
        if (previousDownloaded > 0) {
            if (previousTotal > 0 && metadata.totalBytes > 0 && previousTotal != metadata.totalBytes) fileChanged = true
            if (!previousEtag.isNullOrBlank() && !metadata.etag.isNullOrBlank() && previousEtag != metadata.etag) fileChanged = true
            if (previousEtag.isNullOrBlank() && previousLastModified != null &&
                metadata.lastModified != null && previousLastModified != metadata.lastModified
            ) {
                fileChanged = true
            }
            if (!stagingExists) fileChanged = true
            if (stagingExists && metadata.totalBytes <= 0 && stagingFile.length() != previousDownloaded) fileChanged = true
        }

        val newName = if (!task.fileNameLocked && metadata.fileName.isNotBlank()) metadata.fileName else task.fileName
        val cleanedName = FileNameUtils.sanitize(FileNameUtils.ensureExtension(newName, metadata.mimeType))

        task = task.copy(
            finalUrl = metadata.finalUrl,
            totalBytes = metadata.totalBytes,
            supportsRanges = metadata.supportsRanges,
            etag = metadata.etag ?: task.etag,
            lastModified = metadata.lastModified ?: task.lastModified,
            mimeType = metadata.mimeType.ifBlank { task.mimeType },
            fileName = cleanedName,
            error = null,
            speedBytesPerSec = 0L,
        )

        if (fileChanged) {
            runCatching { stagingFile.delete() }
            task = task.copy(downloadedBytes = 0L, segments = emptyList())
        }

        val oldSegments = task.segments
        val singleUnknown = oldSegments.size == 1 && oldSegments[0].start == 0L && oldSegments[0].end < 0
        val exactSegments = metadata.totalBytes > 0 && oldSegments.isNotEmpty() &&
            oldSegments.all { it.end >= 0 } &&
            oldSegments.sumOf { it.length } == metadata.totalBytes &&
            oldSegments.zipWithNext().all { (a, b) -> a.end + 1 == b.start }

        var planned = when {
            exactSegments -> oldSegments
            singleUnknown -> {
                if (metadata.totalBytes > 0) {
                    val downloaded = oldSegments[0].downloaded.coerceIn(0L, metadata.totalBytes)
                    listOf(Segment(index = 0, start = 0, end = metadata.totalBytes - 1, downloaded = downloaded))
                } else {
                    listOf(Segment(index = 0, start = 0, end = -1, downloaded = oldSegments[0].downloaded))
                }
            }

            else -> emptyList()
        }
        if (planned.isEmpty()) {
            task = task.copy(downloadedBytes = 0L)
            planned = SegmentPlanner.plan(
                metadata.totalBytes,
                if (metadata.supportsRanges && metadata.totalBytes > 0) task.connections else 1,
            )
        }
        val downloaded = planned.sumOf { it.downloaded }
        task = task.copy(
            segments = planned,
            downloadedBytes = if (metadata.totalBytes > 0) {
                downloaded.coerceIn(0L, metadata.totalBytes)
            } else {
                downloaded.coerceAtLeast(0L)
            },
        )
    }

    private fun initProgress() {
        progress = AtomicLongArray(segments.size)
        segments.forEachIndexed { index, segment ->
            val max = if (segment.end >= 0) segment.length else Long.MAX_VALUE
            progress[index] = segment.downloaded.coerceIn(0L, max)
        }
    }

    private fun ensureStagingFileLength() {
        RandomAccessFile(stagingFile, "rw").use { raf ->
            if (task.totalBytes > 0L && raf.length() != task.totalBytes) {
                raf.setLength(task.totalBytes)
            }
        }
    }

    // ------------------------------------------------------------------
    // downloading
    // ------------------------------------------------------------------

    private suspend fun downloadWithFallback() {
        var fallbackTried = false
        while (true) {
            try {
                downloadAll()
                return
            } catch (e: RangeUnsupportedException) {
                if (fallbackTried || segments.size <= 1) {
                    throw FatalHttpException("服务器不支持多连接分段下载（Range 请求返回完整内容）")
                }
                fallbackTried = true
                resetToSingleConnection()
            }
        }
    }

    private fun resetToSingleConnection() {
        runCatching {
            RandomAccessFile(stagingFile, "rw").use { it.setLength(0) }
        }
        segments = SegmentPlanner.plan(task.totalBytes, 1)
        task = task.copy(segments = segments, supportsRanges = false, downloadedBytes = 0L, etag = null, lastModified = null)
        initProgress()
    }

    private suspend fun downloadAll() {
        speedMeter = SpeedMeter()
        coroutineScope {
            val reporter = launch {
                while (isActive) {
                    delay(PROGRESS_INTERVAL_MS)
                    reportProgress()
                }
            }
            try {
                segments.mapIndexed { index, segment ->
                    async(Dispatchers.IO) {
                        if (!segment.isComplete()) downloadSegment(index)
                    }
                }.awaitAll()
            } finally {
                reporter.cancel()
            }
        }
        reportProgress(force = true)
    }

    private suspend fun downloadSegment(index: Int) {
        val segment = segments[index]
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (segment.end >= 0 && progress[index] >= segment.length) return
            try {
                fetchSegment(index)
                if (segment.end >= 0 && progress[index] < segment.length) {
                    throw IOException("连接提前结束（${progress[index]}/${segment.length} 字节）")
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: RangeUnsupportedException) {
                throw e
            } catch (e: ServerChangedException) {
                throw e
            } catch (e: FatalHttpException) {
                throw e
            } catch (e: IOException) {
                if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
                attempt++
                if (attempt > maxSegmentRetries) throw e
                delay(retryDelayMillis(attempt))
            }
        }
    }

    private suspend fun fetchSegment(index: Int) {
        val segment = segments[index]
        val already = progress[index]
        val startPos = segment.start + already
        if (segment.end >= 0 && startPos > segment.end) return

        val etag = task.etag
        val lastModified = task.lastModified
        val requestBuilder = Request.Builder()
            .url(task.finalUrl ?: task.url)
            .header("User-Agent", HttpProbe.USER_AGENT)
            .header("Accept", "*/*")
        if (segment.end >= 0) {
            requestBuilder.header("Range", "bytes=$startPos-${segment.end}")
        } else if (already > 0) {
            requestBuilder.header("Range", "bytes=$startPos-")
        }
        if (!etag.isNullOrBlank() && !etag.startsWith("W/")) {
            requestBuilder.header("If-Range", etag)
        } else if (!lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Range", lastModified)
        }
        val request = requestBuilder.build()
        val call = client.newCall(request)
        activeCalls.add(call)
        try {
            val response = call.await()
            try {
                when {
                    response.code == 206 -> {
                        val range = parseContentRange(response.header("Content-Range"))
                            ?: throw IOException("服务器返回了无效的 Content-Range")
                        if (range.start != startPos) {
                            throw IOException("服务器返回的分段起点不一致（期望 $startPos，实际 ${range.start}）")
                        }
                        val body = response.body ?: throw IOException("服务器返回了空响应体")
                        writeStream(body, index, segment, startPos, range.end)
                    }

                    response.code == 200 -> {
                        val body = response.body ?: throw IOException("服务器返回了空响应体")
                        if (already > 0 && task.supportsRanges) {
                            throw ServerChangedException("服务器上的文件已更新或不支持断点续传，请重新下载该任务")
                        }
                        if (segment.end >= 0 && segment.start > 0) {
                            throw RangeUnsupportedException()
                        }
                        // 单连接且服务器忽略 Range：清空已下载数据，从头写入。
                        if (already > 0) {
                            truncateStaging()
                            progress[index] = 0L
                        }
                        writeStream(body, index, segment, 0L, if (segment.end >= 0) segment.end else -1L)
                    }

                    response.code == 416 -> {
                        if (segment.end >= 0 && already >= segment.length) return
                        throw FatalHttpException("HTTP 416：服务器拒绝该分段范围，请删除任务后重新下载")
                    }

                    response.code in 400..499 -> {
                        throw FatalHttpException(HttpProbe.httpErrorText(response.code))
                    }

                    else -> throw IOException("HTTP ${response.code}：服务器返回错误")
                }
            } finally {
                response.close()
                activeCalls.remove(call)
            }
        } catch (e: Throwable) {
            activeCalls.remove(call)
            throw e
        }
    }

    private suspend fun writeStream(
        body: ResponseBody,
        index: Int,
        segment: Segment,
        writeStart: Long,
        limitEnd: Long?,
    ) {
        val stream = body.byteStream()
        val buffer = ByteArray(rateLimiter.suggestedBufferSize)
        RandomAccessFile(stagingFile, "rw").use { raf ->
            raf.seek(writeStart)
            var position = writeStart
            val hardLimit = limitEnd ?: -1L
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = if (hardLimit >= 0) hardLimit - position + 1 else Long.MAX_VALUE
                if (remaining <= 0) break
                val toRead = min(buffer.size.toLong(), remaining).toInt()
                rateLimiter.acquire(toRead)
                val read = stream.read(buffer, 0, toRead)
                if (read <= 0) break
                raf.write(buffer, 0, read)
                progress.addAndGet(index, read.toLong())
                position += read
            }
        }
    }

    private fun truncateStaging() {
        runCatching {
            RandomAccessFile(stagingFile, "rw").use { it.setLength(0) }
        }
    }

    // ------------------------------------------------------------------
    // finalize / progress
    // ------------------------------------------------------------------

    private fun finalizeTask(): DownloadTask {
        val snapshotSegments = snapshotSegments()
        if (task.totalBytes > 0L) {
            val missing = snapshotSegments.any { it.end >= 0 && it.downloaded != it.length }
            val sum = snapshotSegments.sumOf { it.downloaded }
            if (missing || sum != task.totalBytes) {
                throw IOException("下载数据不完整（$sum/${task.totalBytes} 字节），请重试")
            }
            if (stagingFile.length() != task.totalBytes) {
                throw IOException("临时文件大小校验失败，请重试")
            }
        } else {
            val sum = snapshotSegments.sumOf { it.downloaded }
            if (stagingFile.length() != sum) {
                throw IOException("临时文件大小校验失败，请重试")
            }
        }
        return task.copy(
            segments = snapshotSegments,
            downloadedBytes = snapshotSegments.sumOf { it.downloaded },
            state = TaskState.COMPLETED,
            error = null,
            speedBytesPerSec = 0L,
            publishProgress = -1f,
        )
    }

    private fun reportProgress(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastReportAt < PROGRESS_INTERVAL_MS) return
        lastReportAt = now
        val downloaded = totalDownloaded()
        val speed = speedMeter.sample(downloaded)
        onProgress(downloaded, task.totalBytes, speed, snapshotSegments())
    }

    private fun totalDownloaded(): Long {
        if (progress.length() != segments.size) return task.downloadedBytes
        var total = 0L
        for (i in 0 until progress.length()) total += progress[i]
        return total
    }

    private fun snapshotSegments(): List<Segment> {
        if (progress.length() != segments.size) return segments
        return segments.mapIndexed { index, segment ->
            segment.copy(downloaded = progress[index])
        }
    }

    private fun snapshot(state: TaskState, error: String? = null): DownloadTask {
        val snapshotSegments = snapshotSegments()
        return task.copy(
            segments = snapshotSegments,
            downloadedBytes = maxOf(snapshotSegments.sumOf { it.downloaded }, 0L),
            state = state,
            error = error ?: task.error,
            speedBytesPerSec = 0L,
            publishProgress = -1f,
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private data class ContentRange(val start: Long, val end: Long?, val total: Long?)

    private fun parseContentRange(header: String?): ContentRange? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE).find(header) ?: return null
        return ContentRange(
            start = match.groupValues[1].toLongOrNull() ?: return null,
            end = match.groupValues[2].toLongOrNull(),
            total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull(),
        )
    }

    private fun friendlyError(e: IOException): String = when (e) {
        is SocketTimeoutException -> "连接超时，网络不稳定或服务器响应过慢"
        is UnknownHostException -> "无法解析服务器域名，请检查网络或 DNS"
        is ConnectException -> "无法连接服务器，请检查网络"
        is SSLException -> "HTTPS 证书/握手失败，无法建立安全连接"
        else -> "网络错误：${e.message ?: e.javaClass.simpleName}"
    }

    private fun retryDelayMillis(attempt: Int): Long =
        (1500L * (1 shl (attempt - 1).coerceIn(0, 4))).coerceAtMost(12_000L)

    companion object {
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
