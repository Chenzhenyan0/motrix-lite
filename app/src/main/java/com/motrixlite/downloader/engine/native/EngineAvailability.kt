package com.motrixlite.downloader.engine.native

import android.content.Context
import com.motrixlite.downloader.engine.HttpProbe
import java.io.File

/**
 * 原生下载引擎可用性探测。
 *
 * - aria2：二进制以 `jniLibs/arm64-v8a/libaria2c.so` 打包，系统会解压到 nativeLibraryDir；
 *   该目录在 Android 10+ 仍然允许 exec（应用私有数据目录不允许）。
 * - Gopeed：gomobile 生成的 AAR 类通过反射探测；AAR 缺失时 App 仍可编译/运行，引擎显示不可用。
 */
object EngineAvailability {

    data class Snapshot(
        val aria2: Boolean,
        val gopeed: Boolean,
        val aria2Path: String? = null,
        val gopeedClass: String? = null,
    )

    fun snapshot(context: Context): Snapshot {
        val aria2Path = aria2Binary(context)?.absolutePath
        return Snapshot(
            aria2 = aria2Path != null,
            gopeed = GopeedBridge.isAvailable(),
            aria2Path = aria2Path,
            gopeedClass = GopeedBridge.className,
        )
    }

    fun aria2Binary(context: Context): File? {
        val path = File(context.applicationInfo.nativeLibraryDir, ARIA2_LIB_NAME)
        return path.takeIf { it.isFile && it.length() > 1_000_000L }
    }

    fun isAria2Available(context: Context): Boolean = aria2Binary(context) != null

    fun isGopeedAvailable(): Boolean = GopeedBridge.isAvailable()

    const val ARIA2_LIB_NAME = "libaria2c.so"
    const val USER_AGENT = "MotrixLite/1.1 (Android; native engine)"
}

/**
 * gomobile AAR 的反射桥接。
 *
 * AAR 类名由 `gomobile bind` 生成（Go package `libgopeed` → 默认 Java 类 `com.gopeed.Libgopeed`），
 * 为了在没有 AAR 的源码构建中也能编译，本类完全使用反射，不产生编译期依赖。
 */
object GopeedBridge {

    private val CLASS_CANDIDATES = listOf(
        "com.gopeed.libgopeed.Libgopeed",
        "com.gopeed.Libgopeed",
        "com.gopeed.Mobile",
        "com.gopeed.Gopeed",
        "libgopeed.Libgopeed",
        "libgopeed.Mobile",
    )

    private val mobileClass: Class<*>? by lazy {
        CLASS_CANDIDATES.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name, false, GopeedBridge::class.java.classLoader) }.getOrNull()
        }
    }

    val className: String?
        get() = mobileClass?.name

    fun isAvailable(): Boolean = mobileClass != null

    /**
     * 启动 Gopeed 进程内服务，返回监听端口。
     * gomobile 把 Go 的 `Start(cfg string) (int, error)` 映射为 Java 静态方法（返回数字，错误以异常抛出）。
     */
    fun start(configJson: String): Int {
        val clazz = mobileClass ?: throw IllegalStateException("Gopeed AAR 未打包进 APK")
        val method = clazz.methods
            .filter { it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
            .firstOrNull { it.name.equals("start", ignoreCase = true) }
            ?: throw IllegalStateException("Gopeed AAR 中未找到 start(String) 方法")
        val result = try {
            method.invoke(null, configJson)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw IllegalStateException("Gopeed 启动失败: ${e.targetException?.message ?: e.targetException}", e.targetException)
        }
        return (result as? Number)?.toInt() ?: throw IllegalStateException("Gopeed start() 返回了非数字")
    }

    fun stop() {
        val clazz = mobileClass ?: return
        runCatching {
            val method = clazz.methods.firstOrNull {
                it.parameterTypes.isEmpty() && it.name.equals("stop", ignoreCase = true)
            } ?: return
            method.invoke(null)
        }
    }

    fun userAgent(): String = HttpProbe.USER_AGENT
}
