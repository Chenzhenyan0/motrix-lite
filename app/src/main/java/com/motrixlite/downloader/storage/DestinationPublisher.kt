package com.motrixlite.downloader.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

/**
 * 把 staging 临时文件发布到用户可见的保存位置：
 * - Android 10+ 默认 MediaStore.Downloads（Download/MotrixLite，无需任何存储权限）；
 * - 用户通过 SAF 选择目录时使用 DocumentFile；
 * - Android 8/9 回退到应用专属外部目录（文件管理器可见，卸载会删除）。
 */
class DestinationPublisher(private val context: Context) {

    data class PublishResult(val uri: String, val displayName: String)

    suspend fun publish(
        task: DownloadTask,
        stagingFile: File,
        settings: AppSettings,
        onProgress: (Float) -> Unit = {},
    ): PublishResult {
        if (!stagingFile.exists()) throw IOException("临时文件不存在，无法保存")
        val useSaf = task.saveMode == SaveMode.SAF
        return when {
            useSaf -> publishToSaf(task, stagingFile, settings, onProgress)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> publishToMediaStore(task, stagingFile, onProgress)
            else -> publishToAppExternalDir(task, stagingFile, onProgress)
        }
    }

    suspend fun delete(uri: String?): Boolean = withContext(Dispatchers.IO) {
        if (uri.isNullOrBlank()) return@withContext false
        runCatching {
            val parsed = Uri.parse(uri)
            when (parsed.scheme) {
                "content" -> context.contentResolver.delete(parsed, null, null) > 0
                "file" -> parsed.path?.let { File(it).delete() } ?: false
                else -> false
            }
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // MediaStore (Android 10+)
    // ------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publishToMediaStore(
        task: DownloadTask,
        stagingFile: File,
        onProgress: (Float) -> Unit,
    ): PublishResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val displayName = uniqueMediaStoreName(task.fileName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, task.mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("无法在系统下载目录创建文件")
        try {
            copyWithProgress(stagingFile, uri, onProgress)
            val finish = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, finish, null, null)
            val finalName = queryDisplayName(uri) ?: displayName
            PublishResult(uri.toString(), finalName)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun uniqueMediaStoreName(preferred: String): String {
        val base = preferred.substringBeforeLast('.', preferred)
        val extension = preferred.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var candidate = preferred
        var index = 1
        while (index < 100 && mediaStoreNameExists(candidate)) {
            candidate = "$base ($index)$suffix"
            index++
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreNameExists(name: String): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        return try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(name, RELATIVE_PATH),
                null,
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // SAF
    // ------------------------------------------------------------------

    private suspend fun publishToSaf(
        task: DownloadTask,
        stagingFile: File,
        settings: AppSettings,
        onProgress: (Float) -> Unit,
    ): PublishResult = withContext(Dispatchers.IO) {
        val treeUri = task.safTreeUri ?: settings.safTreeUri
        if (treeUri.isNullOrBlank()) throw IOException("尚未选择保存文件夹，请到设置中选择或改用系统下载目录")
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: throw IOException("无法访问所选保存文件夹，请重新选择")
        if (!tree.canWrite()) throw IOException("所选保存文件夹不可写，请重新选择")
        val mime = task.mimeType.ifBlank { "application/octet-stream" }
        val name = uniqueSafName(tree, task.fileName)
        val document = tree.createFile(mime, name) ?: throw IOException("无法在所选文件夹创建文件")
        try {
            val output = context.contentResolver.openOutputStream(document.uri, "w")
                ?: throw IOException("无法写入所选文件夹")
            output.use { stream ->
                copyStream(stagingFile, stream, onProgress)
            }
            PublishResult(document.uri.toString(), document.name ?: name)
        } catch (e: Throwable) {
            runCatching { document.delete() }
            throw e
        }
    }

    private fun uniqueSafName(tree: DocumentFile, preferred: String): String {
        if (tree.findFile(preferred) == null) return preferred
        val base = preferred.substringBeforeLast('.', preferred)
        val extension = preferred.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var index = 1
        while (index < 100) {
            val candidate = "$base ($index)$suffix"
            if (tree.findFile(candidate) == null) return candidate
            index++
        }
        return "$base-${System.currentTimeMillis()}$suffix"
    }

    // ------------------------------------------------------------------
    // Android 8/9 fallback
    // ------------------------------------------------------------------

    private suspend fun publishToAppExternalDir(
        task: DownloadTask,
        stagingFile: File,
        onProgress: (Float) -> Unit,
    ): PublishResult = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        val dir = File(base, "MotrixLite")
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueFile(dir, task.fileName)
        copyWithProgressToFile(stagingFile, target, onProgress)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(task.mimeType), null)
        PublishResult(Uri.fromFile(target).toString(), target.name)
    }

    private fun uniqueFile(dir: File, preferred: String): File {
        var candidate = File(dir, preferred)
        if (!candidate.exists()) return candidate
        val base = preferred.substringBeforeLast('.', preferred)
        val extension = preferred.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var index = 1
        while (candidate.exists() && index < 100) {
            candidate = File(dir, "$base ($index)$suffix")
            index++
        }
        return candidate
    }

    // ------------------------------------------------------------------
    // copy helpers
    // ------------------------------------------------------------------

    private suspend fun copyWithProgress(stagingFile: File, target: Uri, onProgress: (Float) -> Unit) {
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IOException("无法打开目标文件写入流")
        output.use { copyStream(stagingFile, it, onProgress) }
    }

    private suspend fun copyWithProgressToFile(stagingFile: File, target: File, onProgress: (Float) -> Unit) {
        java.io.FileOutputStream(target).use { copyStream(stagingFile, it, onProgress) }
    }

    private suspend fun copyStream(stagingFile: File, output: OutputStream, onProgress: (Float) -> Unit) {
        val total = stagingFile.length().coerceAtLeast(1L)
        var copied = 0L
        FileInputStream(stagingFile).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                onProgress((copied.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f))
            }
            output.flush()
        }
    }

    companion object {
        const val RELATIVE_PATH = "Download/MotrixLite/"
        const val DISPLAY_RELATIVE_PATH = "Download/MotrixLite"
    }
}
