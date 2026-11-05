package com.motrixlite.downloader.engine

import java.io.IOException
import java.util.Locale

/**
 * 下载前的轻量探测：发一个 `Range: bytes=0-0` GET，判断总长度、是否支持分段、ETag、
 * Content-Disposition 文件名等。只关闭响应，不读取响应体，开销极小。
 */
class HttpProbe(private val client: okhttp3.OkHttpClient) {

    data class Metadata(
        val finalUrl: String,
        val totalBytes: Long,
        val supportsRanges: Boolean,
        val etag: String?,
        val lastModified: String?,
        val mimeType: String,
        val fileName: String,
        val contentDisposition: String?,
    )

    suspend fun probe(url: String, fallbackName: String? = null): Metadata {
        val rangeRequest = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Range", "bytes=0-0")
            .build()

        val first = try {
            executeAndParse(rangeRequest)
        } catch (e: IOException) {
            throw ProbeException("无法连接服务器：${e.message ?: "网络异常"}", retryable = true)
        }

        if (first.code in 200..299 || first.code == 416) {
            return buildMetadata(first, fallbackName)
        }

        if (first.code >= 500) {
            throw ProbeException("服务器暂时不可用（HTTP ${first.code}），请稍后重试", retryable = true)
        }

        // 部分服务器/签名的链接会拒绝 Range 请求，回退到 HEAD。
        if (first.code in 400..499) {
            val headRequest = okhttp3.Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()
            val head = try {
                executeAndParse(headRequest)
            } catch (e: IOException) {
                throw ProbeException("无法连接服务器：${e.message ?: "网络异常"}", retryable = true)
            }
            if (head.code in 200..299) return buildMetadata(head, fallbackName)
            throw ProbeException(httpErrorText(head.code), retryable = false)
        }

        throw ProbeException(httpErrorText(first.code), retryable = false)
    }

    private suspend fun executeAndParse(request: okhttp3.Request): ProbeResponse {
        val response = client.executeAwait(request)
        return try {
            ProbeResponse(
                code = response.code,
                finalUrl = response.request.url.toString(),
                headers = response.headers,
            )
        } finally {
            response.close()
        }
    }

    private fun buildMetadata(response: ProbeResponse, fallbackName: String?): Metadata {
        val headers = response.headers
        val contentRange = headers["Content-Range"]
        val contentLength = headers["Content-Length"]?.toLongOrNull() ?: -1L
        val acceptRanges = headers["Accept-Ranges"]?.contains("bytes", ignoreCase = true) == true

        var totalBytes = -1L
        var supportsRanges = false
        when (response.code) {
            206 -> {
                supportsRanges = true
                totalBytes = parseTotalFromContentRange(contentRange) ?: -1L
            }

            416 -> {
                supportsRanges = true
                totalBytes = parseUnsatisfiedTotal(contentRange) ?: 0L
            }

            200 -> {
                supportsRanges = false
                totalBytes = contentLength
            }

            else -> {
                totalBytes = contentLength
            }
        }
        if (response.code == 200 && acceptRanges) {
            // 少数服务器对 Range: 0-0 仍返回 200，但头部声明支持 Range；按不支持处理最安全。
            supportsRanges = false
        }

        val contentDisposition = headers["Content-Disposition"]
        val urlName = FileNameUtils.fromUrl(response.finalUrl)
        val rawName = FileNameUtils.fromContentDisposition(contentDisposition)
            ?: fallbackName?.takeIf { it.isNotBlank() }
            ?: urlName
            ?: "download-${System.currentTimeMillis()}"
        val mimeFromHeader = headers["Content-Type"]
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() && it.contains('/') }
        val provisionalName = FileNameUtils.sanitize(rawName)
        val mimeType = mimeFromHeader ?: FileNameUtils.mimeForExtension(provisionalName)
        val fileName = FileNameUtils.sanitize(
            FileNameUtils.ensureExtension(provisionalName, mimeType),
        )

        return Metadata(
            finalUrl = response.finalUrl,
            totalBytes = totalBytes,
            supportsRanges = supportsRanges,
            etag = headers["ETag"]?.takeIf { it.isNotBlank() },
            lastModified = headers["Last-Modified"]?.takeIf { it.isNotBlank() },
            mimeType = mimeType,
            fileName = fileName,
            contentDisposition = contentDisposition,
        )
    }

    private data class ProbeResponse(
        val code: Int,
        val finalUrl: String,
        val headers: okhttp3.Headers,
    )

    companion object {
        const val USER_AGENT = "MotrixLite/1.1 (Android; okhttp)"

        fun parseTotalFromContentRange(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val match = Regex("""bytes\s+\d+-\d+/(\d+)""", RegexOption.IGNORE_CASE).find(header) ?: return null
            return match.groupValues[1].toLongOrNull()
        }

        fun parseUnsatisfiedTotal(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val match = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE).find(header) ?: return null
            return match.groupValues[1].toLongOrNull()
        }

        fun httpErrorText(code: Int): String = when (code) {
            400 -> "请求被服务器拒绝（HTTP 400）"
            401 -> "需要登录或授权（HTTP 401），当前版本暂不支持鉴权下载"
            403 -> "服务器拒绝访问（HTTP 403），链接可能已过期或需要 Referer/Cookie"
            404 -> "文件不存在（HTTP 404），链接可能已失效"
            405, 501 -> "服务器不支持该请求方式（HTTP $code）"
            410 -> "资源已被删除（HTTP 410）"
            429 -> "请求过于频繁（HTTP 429），请稍后重试"
            else -> "服务器返回错误（HTTP $code）"
        }
    }
}

class ProbeException(message: String, val retryable: Boolean) : IOException(message)
