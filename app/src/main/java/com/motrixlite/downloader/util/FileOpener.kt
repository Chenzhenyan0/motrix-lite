package com.motrixlite.downloader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.motrixlite.downloader.data.model.DownloadTask
import java.io.File

/**
 * 用系统「文件管理器/相册/播放器」打开已完成的文件。
 * 优先使用 MediaStore/SAF 返回的 content:// uri；旧系统 file:// 通过 FileProvider 转换。
 */
object FileOpener {

    fun open(context: Context, task: DownloadTask): Boolean {
        val destination = task.destinationUri ?: return false
        val uri = toOpenableUri(context, destination) ?: return false
        val mime = task.mimeType.ifBlank { "application/octet-stream" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun toOpenableUri(context: Context, destination: String): Uri? {
        return try {
            val parsed = Uri.parse(destination)
            if (parsed.scheme == "file") {
                val file = parsed.path?.let { File(it) } ?: return null
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                parsed
            }
        } catch (_: Exception) {
            null
        }
    }
}
