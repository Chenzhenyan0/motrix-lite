package com.motrixlite.downloader.engine.native

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class Aria2RpcClientTest {

    @get:Rule
    val server = MockWebServer()

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Test
    fun `addUri sends secret url and options`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":"1","result":"gid-1"}"""),
        )
        val client = Aria2RpcClient(http, server.port, "tok-123")
        val gid = client.addUri("https://example.com/a.bin", mapOf("dir" to "/tmp", "out" to "a.bin"))
        assertEquals("gid-1", gid)
        val request = server.takeRequest()
        assertEquals("/jsonrpc", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("aria2.addUri", body.getString("method"))
        val params = body.getJSONArray("params")
        assertEquals("tok-123", params.getString(0))
        assertEquals("https://example.com/a.bin", params.getJSONArray(1).getString(0))
        assertEquals("/tmp", params.getJSONObject(2).getString("dir"))
        assertEquals("a.bin", params.getJSONObject(2).getString("out"))
    }

    @Test
    fun `tellStatus parses result and maps errors`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":"2","result":{"gid":"g","status":"active","completedLength":"100","totalLength":"200","downloadSpeed":"50"}}"""),
        )
        val client = Aria2RpcClient(http, server.port, "t")
        val status = client.tellStatus("g")
        assertEquals("active", status?.getString("status"))
        assertEquals(100L, status?.getLong("completedLength"))

        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":"3","error":{"code":1,"message":"Unauthorized"}}"""),
        )
        val error = runCatching { client.tellStatus("missing") }.exceptionOrNull()
        assertTrue(error?.message?.contains("Unauthorized") == true)
    }

    @Test
    fun `changeGlobalOption returns true only for OK`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"jsonrpc":"2.0","id":"1","result":"OK"}"""))
        val client = Aria2RpcClient(http, server.port, "t")
        assertTrue(client.changeGlobalOption(JSONObject().put("max-overall-download-limit", "1024")))
        server.takeRequest()
        Unit
    }
}
