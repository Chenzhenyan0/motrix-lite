package com.motrixlite.downloader.engine.native

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Gopeed 内嵌 REST API 客户端（AAR 启动后监听 127.0.0.1 的随机端口）。
 * 认证使用 StartConfig.apiToken，通过 X-Api-Token 头传递。
 */
class GopeedApi(
    private val httpClient: OkHttpClient,
    port: Int,
    private val apiToken: String,
) {
    private val base = "http://127.0.0.1:$port/api/v1"

    suspend fun info(): JSONObject = requestObject("GET", "/info").getJSONObject("data")

    suspend fun tasks(): JSONArray {
        val data = requestObject("GET", "/tasks")["data"]
        return data as? JSONArray ?: JSONArray()
    }

    suspend fun createTask(url: String, name: String, path: String, connections: Int): String {
        val body = JSONObject().apply {
            put(
                "req",
                JSONObject().apply {
                    put("url", url)
                    put("labels", JSONObject().put("source", "motrix-lite"))
                },
            )
            put(
                "opts",
                JSONObject().apply {
                    put("name", name)
                    put("path", path)
                    put("extra", JSONObject().put("connections", connections))
                },
            )
        }
        val result = request("POST", "/tasks", body)[ "data"]
        return result as? String ?: throw IOException("Gopeed 创建任务返回异常")
    }

    suspend fun pause(taskId: String) {
        runCatching { request("PUT", "/tasks/$taskId/pause", JSONObject()) }
    }

    suspend fun continueTask(taskId: String) {
        runCatching { request("PUT", "/tasks/$taskId/continue", JSONObject()) }
    }

    suspend fun delete(taskId: String) {
        runCatching { request("DELETE", "/tasks/$taskId?force=true", null) }
    }

    private suspend fun requestObject(method: String, path: String, body: JSONObject? = null): JSONObject =
        request(method, path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(base + path)
            .header("X-Api-Token", apiToken)
            .header("Accept", "application/json")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE))
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE))
            else -> error("unsupported method $method")
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Gopeed API HTTP ${response.code}: $text")
            if (text.isBlank()) return@use JSONObject()
            val json = JSONObject(text)
            val code = json.optInt("code", 0)
            if (code != 0) throw IOException("Gopeed API 错误 $code: ${json.optString("msg")}")
            return@use json
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
