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
import java.util.concurrent.atomic.AtomicLong

/**
 * aria2 JSON-RPC 客户端（localhost）。
 * 每个调用自动带上 --rpc-secret 对应的 token 参数。
 */
class Aria2RpcClient(
    private val httpClient: OkHttpClient,
    port: Int,
    private val secret: String,
) {
    private val endpoint = "http://127.0.0.1:$port/jsonrpc"
    private val requestId = AtomicLong(1)

    suspend fun version(): String {
        val result = call("aria2.getVersion")
        return (result as? JSONObject)?.optString("version").orEmpty()
    }

    suspend fun addUri(url: String, options: Map<String, String>): String {
        val optionsJson = JSONObject().apply { options.forEach { (k, v) -> put(k, v) } }
        val uris = JSONArray().apply { put(url) }
        val result = call("aria2.addUri", uris, optionsJson)
        return result as? String ?: throw IOException("aria2.addUri 返回异常")
    }

    suspend fun tellStatus(gid: String, keys: List<String> = emptyList()): JSONObject? {
        val result = if (keys.isEmpty()) {
            call("aria2.tellStatus", gid)
        } else {
            call("aria2.tellStatus", gid, JSONArray(keys))
        }
        return result as? JSONObject
    }

    suspend fun pause(gid: String) {
        runCatching { call("aria2.pause", gid) }
    }

    suspend fun unpause(gid: String) {
        runCatching { call("aria2.unpause", gid) }
    }

    suspend fun remove(gid: String) {
        runCatching { call("aria2.remove", gid) }
    }

    suspend fun removeDownloadResult(gid: String) {
        runCatching { call("aria2.removeDownloadResult", gid) }
    }

    suspend fun changeGlobalOption(options: JSONObject): Boolean {
        val result = runCatching { call("aria2.changeGlobalOption", options) }.getOrNull()
        return result?.toString() == "OK"
    }

    suspend fun saveSession() {
        runCatching { call("aria2.saveSession") }
    }

    suspend fun shutdown() {
        runCatching { call("aria2.shutdown") }
        runCatching { call("aria2.forceShutdown") }
    }

    private suspend fun call(method: String, vararg params: Any?): Any? = withContext(Dispatchers.IO) {
        val paramsArray = JSONArray().apply {
            put(secret)
            params.forEach { put(it) }
        }
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement().toString())
            put("method", method)
            put("params", paramsArray)
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("aria2 RPC HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("aria2 RPC 空响应")
            val json = JSONObject(body)
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                throw IOException("aria2 RPC 错误: ${error?.optString("message") ?: error}")
            }
            if (!json.has("result")) return@use null
            when (val result = json.get("result")) {
                JSONObject.NULL -> null
                is JSONObject, is JSONArray, is String, is Number, is Boolean -> result
                else -> result.toString()
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
