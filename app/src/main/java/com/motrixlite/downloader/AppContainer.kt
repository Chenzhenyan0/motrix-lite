package com.motrixlite.downloader

import android.content.Context
import com.motrixlite.downloader.data.DownloadRepository
import com.motrixlite.downloader.data.SettingsRepository
import com.motrixlite.downloader.data.TaskStore
import com.motrixlite.downloader.engine.HttpProbe
import com.motrixlite.downloader.engine.RateLimiter
import com.motrixlite.downloader.network.NetworkMonitor
import com.motrixlite.downloader.service.ServiceController
import com.motrixlite.downloader.storage.DestinationPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 极简手工依赖注入容器（进程内单例）。
 * 之所以不引入 Hilt：本工程体积小、依赖关系简单，避免额外注解处理器拖慢构建。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val stagingDir: File = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "staging",
    ).apply { if (!exists()) mkdirs() }

    val settingsRepository = SettingsRepository(appContext)

    val repository = DownloadRepository(
        store = TaskStore(File(appContext.filesDir, "tasks.json")),
        stagingDir = stagingDir,
        scope = applicationScope,
    )

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val httpProbe: HttpProbe = HttpProbe(
        httpClient.newBuilder().callTimeout(25, TimeUnit.SECONDS).build(),
    )

    val rateLimiter = RateLimiter()

    val networkMonitor = NetworkMonitor(appContext, applicationScope)

    val publisher = DestinationPublisher(appContext)

    val serviceController = ServiceController(appContext)

    init {
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                rateLimiter.limitBytesPerSecond = settings.speedLimitKbPerSec.toLong() * 1024L
            }
        }
        applicationScope.launch { repository.load() }
    }
}
