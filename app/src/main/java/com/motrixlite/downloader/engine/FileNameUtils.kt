package com.motrixlite.downloader.engine

import java.net.URLDecoder
import java.util.Locale

/**
 * 文件名解析/清洗工具（纯 Kotlin，可单元测试）。
 *
 * 逻辑借鉴了 HTTP 下载器常见做法：
 * 1. Content-Disposition 的 filename* (RFC 5987) 优先；
 * 2. 其次 filename=；
 * 3. 再次 URL 最后一段路径；
 * 4. 最后回退到 download-<时间戳>。
 */
object FileNameUtils {

    private const val MAX_NAME_LENGTH = 160

    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // RFC 5987: filename*=UTF-8''%E4%B8%AD%E6%96%87.mp4
        val extended = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            val raw = extended.groupValues[2].trim().trim('"')
            val decoded = percentDecode(raw, charset)
            if (!decoded.isNullOrBlank()) return decoded
        }
        // filename="name.ext" 或 filename=name.ext
        val plain = Regex("""filename\s*=\s*("([^"]*)"|([^;]+))""", RegexOption.IGNORE_CASE).find(header)
        if (plain != null) {
            val raw = (plain.groupValues[2].ifBlank { plain.groupValues[3] }).trim()
            if (raw.isNotBlank()) {
                val decoded = percentDecode(raw, "UTF-8") ?: raw
                return decoded
            }
        }
        return null
    }

    fun fromUrl(url: String): String? {
        return try {
            val afterScheme = url.substringAfter("://", "")
            if (afterScheme.isBlank()) return null
            val path = afterScheme.substringAfter('/', "")
            val cleanPath = path.substringBefore('?').substringBefore('#').trimEnd('/')
            val last = cleanPath.substringAfterLast('/')
            if (last.isBlank() || !last.contains('.')) null else percentDecode(last, "UTF-8")
        } catch (_: Exception) {
            null
        }
    }

    /** 从 URL 推导一个初步文件名，用于任务列表立即展示。 */
    fun suggestName(url: String): String {
        val fromPath = fromUrl(url)
        val candidate = fromPath ?: "download-${System.currentTimeMillis()}"
        return sanitize(candidate)
    }

    fun sanitize(raw: String): String {
        var name = raw
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        if (name.isBlank()) name = "download-${System.currentTimeMillis()}"
        if (name.length > MAX_NAME_LENGTH) {
            val ext = name.substringAfterLast('.', "")
            name = if (ext.isNotEmpty() && ext.length <= 10) {
                name.substring(0, MAX_NAME_LENGTH - ext.length - 1) + "." + ext
            } else {
                name.take(MAX_NAME_LENGTH)
            }
        }
        return name
    }

    fun ensureExtension(name: String, mimeType: String?): String {
        if (name.substringAfterLast('.', "").isNotEmpty()) return name
        val ext = extensionForMime(mimeType)
        return if (ext.isNullOrBlank()) name else "$name.$ext"
    }

    fun extensionForMime(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        val mime = mimeType.substringBefore(';').trim().lowercase(Locale.US)
        return MIME_TO_EXT[mime]
    }

    fun mimeForExtension(extension: String): String {
        val ext = extension.lowercase(Locale.US).removePrefix(".")
        return EXT_TO_MIME[ext] ?: "application/octet-stream"
    }

    fun baseNameWithoutExtension(name: String): String =
        if (name.contains('.')) name.substringBeforeLast('.') else name

    private fun percentDecode(raw: String, charset: String): String? {
        return try {
            // filename 中的 '+' 是字面量，不是空格；先转义避免 URLDecoder 误处理。
            URLDecoder.decode(raw.replace("+", "%2B"), charset)
        } catch (_: Exception) {
            try {
                URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
            } catch (_: Exception) {
                null
            }
        }
    }

    private val MIME_TO_EXT = mapOf(
        "video/mp4" to "mp4",
        "video/x-matroska" to "mkv",
        "video/webm" to "webm",
        "video/quicktime" to "mov",
        "video/x-msvideo" to "avi",
        "video/mp2t" to "ts",
        "audio/mpeg" to "mp3",
        "audio/mp4" to "m4a",
        "audio/aac" to "aac",
        "audio/flac" to "flac",
        "audio/ogg" to "ogg",
        "audio/wav" to "wav",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/svg+xml" to "svg",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/x-7z-compressed" to "7z",
        "application/x-rar-compressed" to "rar",
        "application/gzip" to "gz",
        "application/x-tar" to "tar",
        "application/vnd.android.package-archive" to "apk",
        "application/msword" to "doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.ms-excel" to "xls",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/vnd.ms-powerpoint" to "ppt",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        "application/json" to "json",
        "text/plain" to "txt",
        "text/html" to "html",
        "application/octet-stream" to "bin",
    )

    private val EXT_TO_MIME = mapOf(
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "ts" to "video/mp2t",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "wav" to "audio/wav",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/x-rar-compressed",
        "gz" to "application/gzip",
        "tar" to "application/x-tar",
        "apk" to "application/vnd.android.package-archive",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "json" to "application/json",
        "txt" to "text/plain",
        "html" to "text/html",
        "bin" to "application/octet-stream",
    )
}
