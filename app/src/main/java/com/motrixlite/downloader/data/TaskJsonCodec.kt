package com.motrixlite.downloader.data

import com.motrixlite.downloader.data.model.DownloadEngine
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.Segment
import com.motrixlite.downloader.data.model.TaskState
import org.json.JSONArray
import org.json.JSONObject

/**
 * 任务持久化编解码。使用 platform org.json，无反射，便于单元测试。
 * 每个字段都有默认值，升级 schema 时保持向后兼容。
 * v2: 增加 engine / engineTaskId / engineStatus。
 */
object TaskJsonCodec {

    private const val VERSION = 2

    fun encode(tasks: List<DownloadTask>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("savedAt", System.currentTimeMillis())
        val array = JSONArray()
        tasks.forEach { array.put(encodeTask(it)) }
        root.put("tasks", array)
        return root.toString()
    }

    fun decode(text: String): List<DownloadTask> {
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        val array = root.optJSONArray("tasks") ?: return emptyList()
        val result = ArrayList<DownloadTask>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            decodeTask(obj)?.let { result += it }
        }
        return result
    }

    fun encodeTask(task: DownloadTask): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("url", task.url)
        put("finalUrl", task.finalUrl ?: JSONObject.NULL)
        put("fileName", task.fileName)
        put("fileNameLocked", task.fileNameLocked)
        put("mimeType", task.mimeType)
        put("totalBytes", task.totalBytes)
        put("downloadedBytes", task.downloadedBytes)
        put("state", task.state.name)
        put("supportsRanges", task.supportsRanges)
        put("etag", task.etag ?: JSONObject.NULL)
        put("lastModified", task.lastModified ?: JSONObject.NULL)
        put("connections", task.connections)
        put("error", task.error ?: JSONObject.NULL)
        put("retryCount", task.retryCount)
        put("desiredRunning", task.desiredRunning)
        put("createdAt", task.createdAt)
        put("completedAt", task.completedAt ?: JSONObject.NULL)
        put("destinationUri", task.destinationUri ?: JSONObject.NULL)
        put("savedFileName", task.savedFileName ?: JSONObject.NULL)
        put("stagingName", task.stagingName)
        put("saveMode", task.saveMode.name)
        put("safTreeUri", task.safTreeUri ?: JSONObject.NULL)
        put("engine", task.engine.name)
        put("engineTaskId", task.engineTaskId ?: JSONObject.NULL)
        put("engineStatus", task.engineStatus ?: JSONObject.NULL)
        val segments = JSONArray()
        task.segments.forEach { segment ->
            segments.put(
                JSONObject().apply {
                    put("index", segment.index)
                    put("start", segment.start)
                    put("end", segment.end)
                    put("downloaded", segment.downloaded)
                },
            )
        }
        put("segments", segments)
    }

    fun decodeTask(obj: JSONObject): DownloadTask? {
        return try {
            val id = obj.optString("id", "").ifBlank { return null }
            val state = runCatching { TaskState.valueOf(obj.optString("state", TaskState.PAUSED.name)) }
                .getOrDefault(TaskState.PAUSED)
            val saveMode = runCatching { SaveMode.valueOf(obj.optString("saveMode", SaveMode.MEDIASTORE.name)) }
                .getOrDefault(SaveMode.MEDIASTORE)
            val engine = runCatching { DownloadEngine.valueOf(obj.optString("engine", DownloadEngine.BUILTIN.name)) }
                .getOrDefault(DownloadEngine.BUILTIN)
            val segmentsJson = obj.optJSONArray("segments") ?: JSONArray()
            val segments = ArrayList<Segment>(segmentsJson.length())
            for (i in 0 until segmentsJson.length()) {
                val s = segmentsJson.optJSONObject(i) ?: continue
                segments += Segment(
                    index = s.optInt("index", i),
                    start = s.optLong("start", 0L),
                    end = s.optLong("end", -1L),
                    downloaded = s.optLong("downloaded", 0L),
                )
            }
            DownloadTask(
                id = id,
                url = obj.optString("url", ""),
                finalUrl = obj.optString("finalUrl", "").takeIf { it.isNotBlank() },
                fileName = obj.optString("fileName", "download"),
                fileNameLocked = obj.optBoolean("fileNameLocked", false),
                mimeType = obj.optString("mimeType", "application/octet-stream"),
                totalBytes = obj.optLong("totalBytes", -1L),
                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                state = state,
                segments = segments,
                supportsRanges = obj.optBoolean("supportsRanges", false),
                etag = obj.optString("etag", "").takeIf { it.isNotBlank() },
                lastModified = obj.optString("lastModified", "").takeIf { it.isNotBlank() },
                connections = obj.optInt("connections", 8).coerceIn(1, 16),
                error = obj.optString("error", "").takeIf { it.isNotBlank() },
                retryCount = obj.optInt("retryCount", 0),
                desiredRunning = obj.optBoolean("desiredRunning", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                completedAt = obj.optLong("completedAt", 0L).takeIf { it > 0L },
                destinationUri = obj.optString("destinationUri", "").takeIf { it.isNotBlank() },
                savedFileName = obj.optString("savedFileName", "").takeIf { it.isNotBlank() },
                stagingName = obj.optString("stagingName", "$id.part"),
                saveMode = saveMode,
                safTreeUri = obj.optString("safTreeUri", "").takeIf { it.isNotBlank() },
                engine = engine,
                engineTaskId = obj.optString("engineTaskId", "").takeIf { it.isNotBlank() },
                engineStatus = obj.optString("engineStatus", "").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            null
        }
    }
}
