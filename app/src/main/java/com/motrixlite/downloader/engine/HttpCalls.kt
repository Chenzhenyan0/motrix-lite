package com.motrixlite.downloader.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * 把 OkHttp 的回调式请求桥接为协程；协程被取消时会主动 cancel 底层 Call。
 * 这里必须使用同一个 Call 对象执行，否则上层持有的 Call 取消不到真正的请求。
 */

internal suspend fun OkHttpClient.executeAwait(request: Request): Response = newCall(request).await()

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) {
                continuation.resume(response) { _, value, _ ->
                    value.close()
                }
            } else {
                response.close()
            }
        }
    })
}
