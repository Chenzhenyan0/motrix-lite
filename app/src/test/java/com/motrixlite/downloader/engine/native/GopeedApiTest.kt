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

class GopeedApiTest {

    @get:Rule
    val server = MockWebServer()

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Test
    fun `createTask sends token and parses task id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":0,"data":"task-abc"}"""))
        val api = GopeedApi(http, server.port, "secret-token")
        val id = api.createTask("https://example.com/a.bin", "a.bin", "/tmp/task", 8)
        assertEquals("task-abc", id)
        val request = server.takeRequest()
        assertEquals("/api/v1/tasks", request.path)
        assertEquals("POST", request.method)
        assertEquals("secret-token", request.getHeader("X-Api-Token"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("https://example.com/a.bin", body.getJSONObject("req").getString("url"))
        assertEquals("a.bin", body.getJSONObject("opts").getString("name"))
        assertEquals("/tmp/task", body.getJSONObject("opts").getString("path"))
        assertEquals(8, body.getJSONObject("opts").getJSONObject("extra").getInt("connections"))
    }

    @Test
    fun `tasks parses list and pause uses PUT`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"code":0,"data":[{"id":"t1","name":"a.bin","status":"running","progress":{"downloaded":10,"speed":2},"meta":{"res":{"size":100}}}]}"""),
        )
        val api = GopeedApi(http, server.port, "tok")
        val tasks = api.tasks()
        assertEquals(1, tasks.length())
        assertEquals("running", tasks.getJSONObject(0).getString("status"))
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":0}"""))
        api.pause("t1")
        val request = server.takeRequest()
        assertEquals("/api/v1/tasks/t1/pause", request.path)
        assertEquals("PUT", request.method)
    }

    @Test
    fun `non zero envelope code throws`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":2001,"msg":"task not found"}"""))
        val api = GopeedApi(http, server.port, "tok")
        val err = runCatching { api.tasks() }.exceptionOrNull()
        assertTrue(err?.message?.contains("task not found") == true)
    }
}
