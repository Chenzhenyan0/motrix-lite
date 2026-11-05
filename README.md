# Motrix Lite

**一款用 Kotlin + Jetpack Compose 编写的 Android 原生 HTTP/HTTPS 下载管理器**：多连接 Range 分段、跨重启断点续传、全局令牌桶限速、前台服务保活、零存储权限保存到系统下载目录。

> 参考 Motrix 桌面应用的产品思路与交互组织方式，**独立实现**；不是 Electron 封装，也不是 Motrix 桌面版的 Android 移植。

| 项 | 值 |
| --- | --- |
| 应用名 / 包名 | Motrix Lite / `com.motrixlite.downloader` |
| 版本 | versionCode **1**，versionName **1.0.0** |
| 最低系统 | Android 8.0（API 26） |
| 编译/目标 SDK | 35（Android 15） |
| 技术栈 | Kotlin 2.0.21 · Jetpack Compose（BOM 2024.12.01 / Material3）· OkHttp 4.12.0 · Coroutines 1.9.0 |
| 构建工具 | AGP 8.7.3 · Gradle 8.9 · JDK 17 |
| 包体积 | Debug 10.23 MB / Release 1.41 MB（R8 压缩后） |
| 许可证 | MIT（见 [LICENSE](LICENSE)） |
| 支持协议 | **HTTP / HTTPS 直链**（磁力、BT 种子不支持） |

---

## 重要声明

请先阅读这一节，避免误解项目性质：

1. **不是桌面版封装。** 本工程是独立的 Android 原生项目，未使用 Electron、Node.js、WebView 加载 Motrix 的 Web UI，也不包含 Motrix 的任何源代码或静态资源。
2. **不是 Motrix 官方产品。** "Motrix" 的名称与商标归其作者所有；本项目为非官方独立实现，不使用其官方图标与品牌素材，与其作者无隶属或背书关系。
3. **未集成 aria2。** APK 内没有 aria2 可执行文件、共享库或 JNI 包装；下载内核是本项目自行实现的 OkHttp 分段下载器（原因见 [技术方案](#一技术方案)）。
4. **未做真机验收。** 当前环境没有 Android 设备或模拟器，验证范围仅限构建、单元测试与 Lint。功能验收清单见 [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md)，**所有真机项均"待验证"**。
5. **仅支持 HTTP/HTTPS 直链。** 磁力链接、BT 种子、ed2k 等协议不支持；需要登录/防盗链的链接也不支持（见 [已知问题](docs/KNOWN_ISSUES.md)）。

---

## 一、技术方案

### 1.1 为什么是 Kotlin + Compose，而不是 Flutter

| 维度 | Kotlin + Compose（本项目） | Flutter | 结论 |
| --- | --- | --- | --- |
| 原生能力接入 | 前台服务、`MediaStore`、SAF、通知渠道、`WakeLock`、`ConnectivityManager` 全部是一等 API，直接调用 | 需要 platform channel + 原生侧代码，插件质量参差；前台服务/存储这类能力往往仍要写 Kotlin | Kotlin 胜 |
| 包体积 | Release APK **1.41 MB** | 空工程 release 通常 8–15 MB（引擎 + Skia） | Kotlin 胜 |
| 下载内核契合度 | OkHttp 是 JVM 库，与协程、`RandomAccessFile`、`ContentResolver` 天然协作 | 同样能用（Dart 的 `HttpClient`/插件），但分段随机写与协程取消语义要额外封装 | Kotlin 胜 |
| 语言与工具链 | 单一语言、单一 IDE、Gradle 生态 | Dart + Kotlin/Swift 双栈，需要维护两套原生壳 | Kotlin 胜 |
| 跨端复用 | 仅 Android | 可复用至 iOS/桌面 | Flutter 胜 |
| 适用规模 | 单平台、单 Activity、约 30 个 Kotlin 源文件 | 适合多端、复杂 UI 体系、团队已有 Dart 积累 | 视需求 |

**结论**：本项目只做 Android，核心复杂度在"下载内核 + 前台服务 + 存储发布"这些**平台能力**上，而不是跨端 UI 复用。Flutter 的跨端收益无法抵消其包体积、桥接与双栈维护成本，因此选择 Kotlin + Compose。

**代价**：Compose 版本与 Kotlin/AGP 强耦合（本工程用 Kotlin 2.0.21 的 `org.jetbrains.kotlin.plugin.compose`），升级时需要三者同步。

### 1.2 为什么自研 OkHttp 分段内核，而不是集成 aria2

| 维度 | 自研 OkHttp 内核（本项目） | 集成 aria2 |
| --- | --- | --- |
| 许可证 | MIT + Apache-2.0，**无 GPL 义务** | aria2 为 **GPLv2**；随包分发二进制必须提供完整对应源码/书面要约、许可证文本、修改说明，并与整体分发条款做兼容性评估 |
| Android 可执行性 | 纯 JVM 代码，无 ABI 问题 | Android 10（API 29）起，targetSdk ≥ 29 的应用**不能从可写数据目录执行文件**；只能把 `aria2c` 放进 `lib/<abi>/` 当"原生库"分发，需要 4 套 ABI |
| 包体积 | Release 1.41 MB | 通常 +8–15 MB（二进制 + 依赖） |
| 进程模型 | 与前台服务同进程，暂停=取消协程，状态直接可读 | 需要子进程管理、JSON-RPC 端口、token 鉴权、崩溃重启与任务映射 |
| BT 能力 | 无（当前范围外） | 有，但受 Android 后台/端口/DHT 限制，体验难以保证 |
| 可诊断性 | 错误分类明确（超时/DNS/SSL/4xx/416/服务器文件变化），可精确映射到 UI 文案 | 需要解析 aria2 的错误码与日志 |
| 维护成本 | 需自行维护 HTTP Range、断点续传、限速等细节 | 跟随上游更新、重新交叉编译 |

**结论**：在当前"轻薄、纯 HTTP 直链、MIT/Apache 分发"的定位下，自研内核显著降低了许可证与工程复杂度；代价是**不具备 BT/磁力能力**。

**如果一定要 aria2**：可选方案与风险（GPLv2 源码义务、包体、稳定性、Play 政策、进程隔离的法律不确定性）见 [docs/COMPLIANCE.md](docs/COMPLIANCE.md#33-如果未来一定要集成-aria2义务与风险)。最低风险的路径是"外置引擎 + RPC 客户端"（App 不打包 aria2）。

### 1.3 支持协议

| 协议 | 支持 | 说明 |
| --- | --- | --- |
| `http://` | ✅ | 支持重定向、Range 分段、断点续传 |
| `https://` | ✅ | 同上，证书校验使用系统信任链 |
| `magnet:?xt=...` | ❌ | 未实现 BitTorrent 元数据交换、tracker/DHT、peer wire |
| `.torrent` 文件 | ❌ | 同上 |
| ed2k / FTP / SFTP | ❌ | 未实现 |

**为什么不做 BT**（详见 [KI-01](docs/KNOWN_ISSUES.md)）：需要 bencode 解析、磁力 metadata 交换、tracker 与 DHT、peer 连接管理与 piece 校验，工作量以人月计；且 Android 的后台限制、省电策略、NAT/端口不可达使 BT 体验难以保证。

**后续扩展点**：在 `engine/` 下新增 `torrent/` 包，并在任务创建阶段按 URL scheme 分流（`magnet:` / `.torrent` 交给 BT 引擎，其余走现有 `HttpProbe` 流程）。`DownloadCoordinator` 的队列、持久化、通知聚合可以直接复用。

### 1.4 系统架构

```
Compose UI（MainActivity / MainViewModel）
        │ StateFlow                    │ startForegroundService
        ▼                              ▼
DownloadRepository（内存唯一数据源）  DownloadService（dataSync 前台服务）
        │ tasks.json（原子写）          │ 通知聚合 / WakeLock / 时限处理
        ▼                              ▼
     TaskStore                  DownloadCoordinator（队列/并发/Wi-Fi/重试）
                                        │
        ┌───────────────────────────────┼──────────────────────────────┐
        ▼                               ▼                              ▼
   HttpProbe                     TaskDownloader                DestinationPublisher
  （Range: bytes=0-0）      （每段一个 Range 请求 + 随机写）   （MediaStore / SAF / 私有目录）
                                        │
                              SegmentPlanner · RateLimiter · SpeedMeter
```

详细模块职责、状态机、并发模型见 **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**。

---

## 二、可行性与风险点

| 风险 | 影响 | 缓解措施 | 状态 |
| --- | --- | --- | --- |
| 服务器不支持 Range | 无法多段加速、暂停后可能重下 | 探测阶段识别（`206`/`200`/`416`），自动降级单连接；UI 提前显示"断点续传：不支持" | 已实现，待真机验证 |
| 服务器文件在下载期间变化 | 拼出损坏文件 | `ETag`/`Last-Modified`/总长度/staging 长度四重校验；不一致则清零重下；请求带 `If-Range` | 已实现，有单测覆盖 |
| 发布阶段磁盘翻倍 | 大文件保存失败 | UI 明示保存位置；文档要求预留 2.5 倍空间；失败时保留 staging 可重试 | 已知限制（KI-04） |
| Android 15 前台服务时限 | 6 小时后被系统暂停 | 重写 `onTimeout`，优雅暂停 + 提示；重新打开应用即续传 | 已实现，无法在本环境验证 |
| HyperOS 后台清理 | 息屏后下载中断 | 应用内引导（电池优化/自启动/多任务锁定/通知）；文档明确说明无法代码修改 | 已实现引导，效果待真机验证 |
| 通知权限被拒 | 通知不可见 | 横幅 + Snackbar + 设置页状态说明；服务仍运行 | 已实现 |
| 明文 HTTP | 内容可被篡改 | 文档风险提示；建议仅用于可信源；后续可加"仅 HTTPS"开关 | 已知限制（KI-13） |
| 无校验和 | 无法发现内容损坏/替换 | 文档建议用户自行比对官方哈希；后续增加可选 SHA-256 | 已知缺失（KI-09） |
| 依赖单文件 JSON | 大量任务时写放大 | 700 ms 防抖 + 原子替换；任务数量级小时无感 | 可接受（KI-14） |

---

## 三、功能矩阵

| 功能 | 状态 | 说明 |
| --- | --- | --- |
| HTTP/HTTPS 直链下载 | ✅ | 跟随重定向，使用最终 URL |
| 多连接 Range 分段 | ✅ | 每任务 1–16 连接（默认 8）；总长未知或服务器不支持 Range 时自动降级单连接 |
| 分段规划 | ✅ | 文件 < 2 MB 或连接数 = 1 时单连接；否则段数 = `min(连接数, max(2, 总长/1MB))`，再限制在 2–16 |
| 断点续传（跨 App 重启） | ✅ | staging 随机写 + `ETag`/`Last-Modified`/长度校验 |
| 暂停 / 恢复 | ✅ | 协程取消即暂停，`Call.cancel()` 中断阻塞读 |
| 段级重试 | ✅ | 每段最多 3 次，退避 1.5s / 3s / 6s（上限 12s） |
| 任务级自动重试 | ✅ | 仅可重试错误，3s / 8s / 15s 三次 |
| 全局限速 | ✅ | 令牌桶，KB/s，`0` 表示不限速，改设置即时生效 |
| 多任务并发 | ✅ | 1–5，默认 3 |
| 仅 Wi-Fi 下载 | ✅ | 非 Wi-Fi 时进入「等待 Wi-Fi」，连回后自动继续 |
| 前台服务常驻 | ✅ | `dataSync` 类型，常驻通知 + `WakeLock` |
| Android 14/15 前台服务适配 | ✅ | 声明并传入 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`；重写 `onTimeout` |
| 通知权限拒绝降级 | ✅ | 服务仍运行，应用内横幅 + Snackbar 提示 |
| 任务 JSON 持久化与重启恢复 | ✅ | `filesDir/tasks.json`，原子替换，`desiredRunning` 区分用户意图 |
| 保存到系统下载目录 | ✅ | Android 10+：`MediaStore.Downloads` → `Download/MotrixLite`，**无需存储权限** |
| 自定义保存目录（SAF） | ✅ | `OpenDocumentTree` + 持久化 URI 授权 |
| Android 8/9 保存回退 | ✅ | 应用专属外部目录 `.../files/Download/MotrixLite`（卸载即删） |
| 深色模式 | ✅ | 跟随系统 / 浅色 / 深色 |
| 下载完成/失败通知 | ✅ | 独立结果渠道，每任务一条 |
| 自定义文件名 | ✅ | 新建任务时可指定，指定后不被服务器元数据覆盖 |
| 任务筛选 / 清理 | ✅ | 全部 / 未完成 / 已完成 / 失败；可清理已完成任务（保留文件） |
| 速度曲线 / 剩余时间 | ✅ | 30 点滑动历史 + ETA 估算 |
| 打开文件 / 删除文件 | ✅ | `FileProvider` 转换 `file://`；删除任务时可选同时删文件 |
| 磁力 / BT | ❌ | 明确不支持（见 1.3） |
| Cookie / Referer / 鉴权 | ❌ | 不支持（KI-02） |
| 校验和验证 | ❌ | 未实现（KI-09） |
| 开机自启 | ❌ | 未实现，且 Android 15 不允许从 `BOOT_COMPLETED` 启动 dataSync 前台服务（KI-10） |
| 每任务独立通知 | ❌ | 仅一条聚合进度通知（KI-11） |

---

## 四、工程结构

```
motrix-lite/
├── app/
│   ├── build.gradle.kts                 # 模块配置：SDK、签名、R8、依赖
│   ├── proguard-rules.pro               # R8 规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # 权限、服务、FileProvider
│       │   ├── java/com/motrixlite/downloader/
│       │   │   ├── App.kt               # Application，创建容器与通知渠道
│       │   │   ├── AppContainer.kt      # 手写依赖注入（单例）
│       │   │   ├── data/                # 任务仓库、JSON 编解码、设置存储
│       │   │   ├── engine/              # 探测、分段、下载、调度、限速
│       │   │   ├── network/             # 网络状态监听
│       │   │   ├── service/             # 前台服务、通知、启动封装
│       │   │   ├── storage/             # MediaStore / SAF 发布
│       │   │   ├── ui/                  # Compose 界面、ViewModel、主题
│       │   │   └── util/                # HyperOS 引导、权限、打开文件
│       │   └── res/                     # 字符串、主题、图标（自绘 Vector）
│       └── test/java/com/motrixlite/downloader/
│           ├── data/TaskJsonCodecTest.kt
│           └── engine/{Formatters,FileNameUtils,SegmentPlanner,RateLimiter,HttpProbe,TaskDownloader}Test.kt
├── docs/
│   ├── ARCHITECTURE.md                  # 架构与内核细节
│   ├── BUILD_AND_SIGN.md                # 构建、签名、排错
│   ├── TEST_CHECKLIST.md                # 真机验收清单（A1–A7 + B1–B8）
│   ├── KNOWN_ISSUES.md                  # 已知问题
│   └── COMPLIANCE.md                    # 许可证与合规分析
├── keystore/release.keystore            # 演示签名证书（不可用于上架）
├── tools/range_test_server.py           # 本地 Range 测试服务器
├── dist/                                # 构建产物副本 + SHA256SUMS.txt
├── gradle/wrapper/                      # Gradle 8.9 wrapper
├── build.gradle.kts                     # 根构建脚本（插件版本）
├── settings.gradle.kts                  # 仓库与模块声明
├── gradle.properties                    # JVM 参数、AndroidX、缓存
├── gradlew / gradlew.bat
├── keystore.properties                  # 签名配置（演示用，已被 .gitignore 忽略）
├── local.properties                     # SDK 路径（本机生成，不提交）
├── LICENSE                              # MIT
├── README.md
└── THIRD_PARTY_NOTICES.md               # 第三方组件许可清单
```

### 关键源文件

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `engine/TaskDownloader.kt` | 535 | 单任务多段下载：探测后规划、Range 请求、随机写、重试、完整性校验 |
| `data/DownloadRepository.kt` | 359 | 任务唯一数据源、状态变更、重启恢复、持久化调度 |
| `ui/MainScreen.kt` | 494 | 任务列表、筛选、横幅、弹层入口 |
| `ui/screens/SettingsScreen.kt` | 331 | 下载/保存/通知/外观/关于设置 |
| `storage/DestinationPublisher.kt` | 243 | MediaStore / SAF / 旧系统三条发布路径 |
| `engine/DownloadCoordinator.kt` | 218 | 队列、并发、Wi-Fi 策略、任务级重试、发布编排 |
| `service/DownloadService.kt` | 221 | 前台服务、WakeLock、时限回调、空闲退出 |
| `ui/sheets/NewTaskSheet.kt` | 241 | 新建任务：链接解析、文件名、参数预览 |
| `ui/sheets/TaskDetailSheet.kt` | 224 | 任务详情：速度曲线、分段信息、操作 |
| `engine/HttpProbe.kt` | 174 | 元数据探测与 HTTP 错误文案 |
| `service/NotificationHelper.kt` | 171 | 两个通知渠道、聚合进度、完成/失败提醒 |
| `data/TaskJsonCodec.kt` | 126 | JSON schema v1 编解码（无反射） |
| `ui/MainViewModel.kt` | 230 | UI 状态、事件、系统设置跳转 |
| `engine/FileNameUtils.kt` | 186 | `Content-Disposition`/URL 文件名解析与清洗 |
| `data/model/Models.kt` | 136 | 数据模型与状态机枚举 |
| `engine/SegmentPlanner.kt` | 48 | 分段规划（纯函数，可单测） |
| `engine/RateLimiter.kt` | 54 | 全局令牌桶 |
| `util/HyperOSGuide.kt` | 107 | 电池优化/自启动/通知页跳转与回退 |

---

## 五、关键实现说明

### 5.1 下载引擎流程

```
点击「开始下载」
  → Repository.createTask()（状态 QUEUED，写盘）
  → ServiceController.ensureRunning()（拉起前台服务）
  → Coordinator.kick()（有槽位 + 网络允许）
  → TaskDownloader.run()
       ① HttpProbe: GET + Range: bytes=0-0  →  总长度 / 是否支持 Range / ETag / 文件名
       ② prepare(): 与上次记录比对（长度、ETag、Last-Modified、staging 是否存在）
       ③ 0 字节文件直接完成
       ④ SegmentPlanner: 1–16 段连续区间
       ⑤ RandomAccessFile.setLength(totalBytes) 预分配
       ⑥ 每段一个协程：Range 请求 + seek 写入 + 段级重试
       ⑦ finalize: 分段完整性 + 文件长度校验
  → Coordinator: 状态 SAVING → DestinationPublisher 发布
  → Repository.markCompleted(): COMPLETED + 删除 staging + 通知
```

核心代码位置：`app/src/main/java/com/motrixlite/downloader/engine/TaskDownloader.kt`。

**暂停语义**：暂停不是"标记一个 flag"，而是取消协程并 `Call.cancel()`：

```kotlin
// TaskDownloader.kt:146-149
fun cancelActiveCalls() {
    activeCalls.forEach { call -> runCatching { call.cancel() } }
}
```

**降级路径**：若分段请求返回 `200`（服务器忽略 Range），抛出 `RangeUnsupportedException`，由 `TaskDownloader` 内部捕获，清空 staging、改为单连接整文件重下：

```kotlin
// TaskDownloader.kt:253-267
private suspend fun downloadWithFallback() {
    var fallbackTried = false
    while (true) {
        try {
            downloadAll()
            return
        } catch (e: RangeUnsupportedException) {
            if (fallbackTried || segments.size <= 1) {
                throw FatalHttpException("服务器不支持多连接分段下载（Range 请求返回完整内容）")
            }
            fallbackTried = true
            resetToSingleConnection()
        }
    }
}
```

### 5.2 状态机

```
QUEUED → CONNECTING → DOWNLOADING → SAVING → COMPLETED
             │             │
             └→ RETRYING ──┘→ FAILED（重试耗尽 / 不可重试错误）

任意活动态 --用户暂停 或 系统/时限--> PAUSED
仅 Wi-Fi 且当前非 Wi-Fi --> WAITING_FOR_WIFI --连回 Wi-Fi--> QUEUED
```

- `desiredRunning` 记录用户意图：重启后"活动态 + 用户想运行"→ `QUEUED`；"活动态 + 用户已暂停"→ `PAUSED`。
- `needsService` 决定服务是否存活：没有任务需要服务且空闲 4 秒 → 服务自动停止。

### 5.3 保存策略（零存储权限）

| 系统 | 保存位置 | 权限 |
| --- | --- | --- |
| Android 10+（默认） | `MediaStore.Downloads` → `Download/MotrixLite/` | 无 |
| Android 10+（用户选 SAF） | 用户选择的目录（`DocumentFile`） | 仅该目录的 URI 授权 |
| Android 8/9 | `Android/data/com.motrixlite.downloader/files/Download/MotrixLite/` | 无（文件管理器可见，卸载即删） |

写入时序（MediaStore）：`insert(IS_PENDING=1)` → 复制 staging → `update(IS_PENDING=0)`；失败时 `delete(uri)` 回滚，避免用户看到半截文件。

```kotlin
// storage/DestinationPublisher.kt:74-79（节选）
val values = ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
    put(MediaStore.Downloads.MIME_TYPE, task.mimeType.ifBlank { "application/octet-stream" })
    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)   // "Download/MotrixLite/"
    put(MediaStore.Downloads.IS_PENDING, 1)
}
```

**已知代价**：发布阶段需要把 staging 复制到目标位置，**峰值临时占用约为文件大小的 2 倍**（KI-04）。

### 5.4 前台服务

Manifest 声明：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".service.DownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync"
    android:stopWithTask="false" />
```

运行时：

```kotlin
// service/DownloadService.kt:104-117
private fun startForegroundWithNotification() {
    if (foregroundStarted) return
    val notification: Notification = notificationHelper.buildForegroundNotification(
        container.repository.tasks.value,
        container.settingsRepository.settings.value,
    )
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
        0
    }
    ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, type)
    foregroundStarted = true
}
```

行为要点：

- 返回值 `START_STICKY`；`startForeground` 抛异常时降级为"暂停全部 + 提示 + `START_NOT_STICKY`"，不崩溃。
- `WakeLock`（`PARTIAL_WAKE_LOCK`，非引用计数，6 小时上限）只在有任务运行时持有。
- 没有任何任务需要服务时，4 秒后退出前台并 `stopSelf()`。
- 通知被拒绝时服务照常运行，只是通知不可见（Android 13+ 行为）。

### 5.5 全局限速

令牌桶，所有任务共享：

```kotlin
// engine/RateLimiter.kt:14-29（节选）
@Volatile var limitBytesPerSecond: Long = 0L     // 0 = 不限速

val suggestedBufferSize: Int
    get() {
        val limit = limitBytesPerSecond
        if (limit <= 0L) return 64 * 1024
        return minOf(64 * 1024, maxOf(1024, (limit / 4).toInt()))
    }
```

读块大小随限速自适应（最低 1 KB），低限速时暂停响应更及时。设置变更通过 `settings` Flow 实时写入，无需重启任务。

### 5.6 HyperOS 适配

MIUI/HyperOS 的自启动、省电策略、后台弹出界面等开关**没有公开 API**，应用只能"引导"：

| 应用内入口 | 跳转目标 | 失败回退 |
| --- | --- | --- |
| 电池优化 | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（带包名） | `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| 自启动 / 省电 | `com.miui.securitycenter/.permcenter.autostart.AutoStartManagementActivity` 等 3 个候选组件 | 应用详情页 `ACTION_APPLICATION_DETAILS_SETTINGS` |
| 通知设置 | `ACTION_APP_NOTIFICATION_SETTINGS`（带包名） | 应用详情页 |

实现见 `app/src/main/java/com/motrixlite/downloader/util/HyperOSGuide.kt`；入口在任务页顶部"后台稳定运行建议"卡片与设置页"通知与后台运行"。详见第九节。

---

## 六、构建与签名

### 6.1 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | **17**（构建验证使用 OpenJDK 17.0.20） |
| Gradle | 8.9（由 wrapper 自动下载） |
| Android SDK | Platform 35、Build-Tools 35.0.0、Platform-Tools |
| Android Studio（可选） | Ladybug 2024.2.1 或更新 |

### 6.2 方式 A：Android Studio

1. `File → Open` 选择工程根目录，等待 Gradle Sync。
2. `Settings → Build Tools → Gradle → Gradle JDK` 选择 **17**。
3. 安装 SDK Platform 35 与 Build-Tools 35.0.0。
4. 点击 ▶ Run，或 `Build → Build APK(s)`。

### 6.3 方式 B：命令行（Windows）

```powershell
# 环境变量（示例）
$env:JAVA_HOME   = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "C:\Android\sdk"

# 安装 SDK 组件
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses

# 构建
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
```

macOS / Linux：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

SDK 路径也可写入 `local.properties`（Windows 需转义）：

```properties
sdk.dir=C\:\\Android\\sdk
# 或
sdk.dir=C:/Android/sdk
```

> Gradle wrapper 首次运行需要联网下载 Gradle 8.9（约 130 MB）。国内网络慢可把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成镜像，或手动下载后放到 `%USERPROFILE%\.gradle\wrapper\dists\` 离线使用。完整说明见 [docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md#四网络受限环境的处理)。

### 6.4 产物

| 类型 | 路径 | 大小 |
| --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 10,730,630 字节（10.23 MB） |
| Release | `app/build/outputs/apk/release/app-release.apk` | 1,477,909 字节（1.41 MB） |

`dist/` 中为分发副本：

```
dist/MotrixLite-debug.apk      SHA-256  349612085d362b5130b537e012cf6e14334d0bbcbb3a1c249b4b7133adcd2819
dist/MotrixLite-release.apk    SHA-256  cd5872c1f5e7f203aa304017105ed2e8d2bc48fcc6da195ad41334b3e40196e1
dist/SHA256SUMS.txt
```

校验：

```powershell
Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256
```

```bash
shasum -a 256 dist/MotrixLite-release.apk
```

### 6.5 签名

`app/build.gradle.kts` 在 `keystore.properties` 存在时使用 `release` 签名配置；不存在时回退到 debug 签名（可构建，但**不可用于发布**）。

当前仓库内的演示配置：

```properties
# keystore.properties（已被 .gitignore 忽略）
storeFile=keystore/release.keystore
storePassword=motrixlite2026
keyAlias=motrixlite
keyPassword=motrixlite2026
```

> ⚠️ **演示证书不能上架**：私钥与口令已随仓库公开。正式发布前必须生成新密钥库并替换配置。

生成新签名：

```powershell
keytool -genkeypair -v -keystore keystore\release.keystore -alias motrixlite `
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 `
  -storepass "你的强口令" -keypass "你的强口令" `
  -dname "CN=Your Name, OU=Mobile, O=Your Org, L=Beijing, ST=Beijing, C=CN"
```

验证签名：

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

安装：

```powershell
adb install -r dist\MotrixLite-release.apk
```

完整的签名、R8 排错、镜像与离线构建说明见 **[docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md)**。

---

## 七、安装与使用

### 7.1 安装

```powershell
# USB 调试
adb devices
adb install -r dist\MotrixLite-release.apk
```

也可以把 APK 传到手机，用文件管理器点击安装（需允许"安装未知来源应用"）。

要求：Android 8.0（API 26）及以上。

### 7.2 首次启动建议

1. **允许通知**（Android 13+）：否则后台进度不可见，应用会显示横幅提示。
2. **检查保存位置**：默认"系统下载目录（推荐）"→ `Download/MotrixLite`，无需任何存储权限。
3. **HyperOS 用户**：按任务页顶部"后台稳定运行建议"卡片完成电池优化、自启动、多任务锁定（见第九节）。

### 7.3 下载一个文件

1. 点击右下角 **新建任务**。
2. 粘贴 HTTP/HTTPS 直链（可点"粘贴"从剪贴板读取）。
3. 可选：点 **解析链接** 预览文件名、大小、是否支持断点续传。
4. 可选：修改文件名（留空则用服务器/链接中的名字）。
5. 点 **开始下载**。

### 7.4 任务操作

| 操作 | 入口 |
| --- | --- |
| 暂停 / 继续 | 任务卡片右侧按钮，或点击卡片进入详情 |
| 重试失败任务 | 任务卡片 / 详情页「重试」 |
| 查看进度、速度、分段、错误 | 点击任务卡片 |
| 打开已完成文件 | 详情页「打开文件」 |
| 删除任务（可选同时删文件） | 详情页「删除」/ 卡片菜单 |
| 全部暂停 / 全部继续 | 顶栏按钮 |
| 清理已完成任务（保留文件） | 顶栏「⋮」菜单 |
| 筛选 | 列表上方 全部 / 未完成 / 已完成 / 失败 |

### 7.5 文件去哪了

| 系统 | 路径 |
| --- | --- |
| Android 10+ | `内部存储/Download/MotrixLite/`（文件管理器可直接看到） |
| Android 10+ 且选了 SAF | 你选择的目录 |
| Android 8/9 | `Android/data/com.motrixlite.downloader/files/Download/MotrixLite/`（卸载会删除） |

卸载应用不会删除已发布到 `Download/MotrixLite` 的文件，但会删除未完成的 staging 数据。

### 7.6 卸载

```powershell
adb uninstall com.motrixlite.downloader
```

---

## 八、权限说明

| 权限 | 类型 | 用途 | 拒绝后果 |
| --- | --- | --- | --- |
| `INTERNET` | 普通 | 发起 HTTP/HTTPS 请求 | 无法下载（必需） |
| `ACCESS_NETWORK_STATE` | 普通 | 判断 Wi-Fi / 蜂窝 / 离线，驱动"仅 Wi-Fi"策略 | 策略失效，无法准确提示网络状态 |
| `POST_NOTIFICATIONS` | 危险（Android 13+ 运行时） | 前台服务进度通知、完成/失败提醒 | 下载仍继续，但通知不可见；应用内横幅提示 |
| `FOREGROUND_SERVICE` | 普通 | 启动前台服务 | 服务无法启动 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 普通 | 声明 `dataSync` 前台服务类型（Android 14+ 必需） | Android 14+ 启动服务时抛异常 |
| `WAKE_LOCK` | 普通 | 下载期间保持 CPU 唤醒 | 息屏后下载可能被挂起 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 普通 | 引导用户把应用加入电池优化白名单 | 只能引导失败，需手动设置；后台稳定性下降 |

**没有申请的权限（刻意为之）**：

- ❌ `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- ❌ `MANAGE_EXTERNAL_STORAGE`（所有文件访问）
- ❌ `READ_MEDIA_*`
- ❌ `RECEIVE_BOOT_COMPLETED`（应用不做开机自启）

原因：Android 10+ 通过 `MediaStore` 写入 `Download` 不需要存储权限；自定义目录通过 SAF 的 URI 授权；Android 8/9 回退到应用专属目录。**最小权限原则**，也避免了应用商店对敏感权限的额外审核。

> `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 在部分分发渠道（如 Google Play）有额外政策要求，上架前请复核，见 [docs/COMPLIANCE.md](docs/COMPLIANCE.md#八分发检查清单发布前)。

### 8.1 网络安全：为什么允许明文 HTTP

`AndroidManifest.xml` 中设置了：

```xml
<application android:usesCleartextTraffic="true" ...>
```

原因：大量下载源（局域网服务、自建文件服务器、老站点）仍只提供 `http://`，若默认禁止明文，应用会对这些链接直接失败。

**风险**（必须知悉）：

- 下载内容可被同一网络中的中间人**篡改或替换**，且应用**没有校验和验证**（KI-09），无法发现；
- 请求 URL、文件名、文件大小对链路可见；
- DNS 被劫持时可能连到伪造服务器。

**建议**：优先使用 `https://`；仅在可信网络（家庭/办公）对可信源使用 HTTP；重要文件下载后自行比对官方哈希。

**后续改进**：增加"仅允许 HTTPS"开关，或改用 `network_security_config.xml` 按域名放行。当前版本**未实现**该开关。

---

## 九、HyperOS / Android 15 适配

### 9.1 HyperOS（小米 / 红米）手动配置

系统设置路径（不同 ROM 版本文案可能略有差异）：

```
设置 → 应用设置 → 应用管理 → Motrix Lite
    ├── 自启动            → 允许
    ├── 省电策略          → 无限制
    └── 通知管理          → 允许通知

设置 → 电池 → 应用电池用量 → Motrix Lite → 无限制

多任务（最近任务）界面 → 下拉 Motrix Lite 卡片 → 点击锁图标锁定
```

应用内提供跳转按钮：

- 任务页顶部"后台稳定运行建议（HyperOS）"卡片 →「电池优化」「自启动/省电」
- 设置页「通知与后台运行」→「电池优化」「HyperOS 自启动 / 省电策略」「通知权限」

**为什么只能引导**：这些开关由 MIUI 安全中心/电源管理模块实现，**没有公开 API**，第三方应用无法查询或修改。跳转使用组件名尝试（`HyperOSGuide` 中列出 3 个候选 Activity），部分组件可能随系统版本变化而失效，失败时**自动回退到应用详情页**，不会崩溃。

即便如此，HyperOS 仍可能在极端省电场景下冻结进程（KI-08）。若下载中断，重新打开应用即可从断点继续。

### 9.2 Android 15 前台服务时限

Android 15 对 `dataSync` 类型前台服务限制为 **24 小时内累计 6 小时**。本应用的处理：

```kotlin
// service/DownloadService.kt:173-178
override fun onTimeout(startId: Int, fgsType: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        handleForegroundTimeout()
    }
}
```

触发后：暂停全部任务 → 保存进度 → 移除前台通知 → 释放 WakeLock → 提示"已达系统时限，重新打开应用即可继续"。**不崩溃、不丢进度**。

### 9.3 Android 14 前台服务类型

Android 14 要求声明并传入前台服务类型，否则启动即抛 `MissingForegroundServiceTypeException`。本应用已：

- Manifest：`android:foregroundServiceType="dataSync"`
- 运行时：`FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- 权限：`FOREGROUND_SERVICE_DATA_SYNC`

### 9.4 其它版本适配

| 版本 | 适配点 |
| --- | --- |
| Android 13（API 33） | `POST_NOTIFICATIONS` 运行时申请；拒绝后降级并在应用内提示 |
| Android 12（API 31） | 后台启动前台服务受限；`ServiceController` 捕获异常，回到前台后重试 |
| Android 10（API 29） | 分区存储；改用 `MediaStore` 写入 `Download/MotrixLite`，不再申请存储权限 |
| Android 8/9（API 26–28） | 无分区存储；保存到应用专属外部目录并 `MediaScannerConnection` 通知媒体库 |

---

## 十、测试与验收状态

### 10.1 本机已完成的验证（Windows，JDK 17.0.20）

| 项 | 结果 | 证据 |
| --- | --- | --- |
| Debug 构建 | ✅ 成功（Gradle wrapper） | `app/build/outputs/apk/debug/app-debug.apk`，10,730,630 字节 |
| Release 构建（R8 + 自签名） | ✅ 成功 | `app/build/outputs/apk/release/app-release.apk`，1,477,909 字节 |
| 单元测试 | ✅ **25 / 25 通过** | 7 个测试类，`app/build/reports/tests/testDebugUnitTest/index.html` |
| `lintVitalRelease` | ✅ 通过 | 无致命问题 |
| `lintDebug` | ✅ 0 errors / 1 warning | `app/build/reports/lint-results-debug.html` |
| 真机安装与功能验收 | ❌ **未执行** | 本环境无 Android 设备/模拟器 |

单元测试明细：

| 测试类 | 用例 | 覆盖点 |
| --- | --- | --- |
| `FormattersTest` | 3 | 字节/速度/ETA 格式化 |
| `FileNameUtilsTest` | 6 | RFC 5987、引号 filename、URL 推导、非法字符清洗、扩展名补齐、MIME 双向映射 |
| `SegmentPlannerTest` | 4 | 分段连续覆盖、小文件单段、未知长度、0 字节 |
| `RateLimiterTest` | 3 | 不限速立即返回、缓冲区自适应、限速下最终放行 |
| `TaskJsonCodecTest` | 2 | 持久化字段往返、运行期字段丢弃、空/畸形数据 |
| `HttpProbeTest` | 4 | Range 探测与元数据解析、无 Range 识别、HTTP 错误可重试性、`Content-Range` 解析 |
| `TaskDownloaderTest` | 3 | 多段下载字节完全一致、暂停后续传字节一致、无 Range 服务器降级 |

### 10.2 尚未验证的内容（必须如实说明）

**本环境没有 Android 设备，也没有模拟器**，因此以下内容**全部待真机验证**，本文档不声称已通过：

- 安装、启动、首屏渲染；
- 真机多连接下载的实际速度与稳定性；
- 锁屏 / 后台 10 分钟保活；
- 通知权限拒绝后的降级表现；
- `Download/MotrixLite` 在真机文件管理器中的可见性；
- 5 任务并发、暂停/恢复/删除、进程杀死与设备重启后的状态恢复；
- 限速、并发、仅 Wi-Fi 策略的实际效果；
- Android 15 前台服务 6 小时时限的触发与恢复；
- HyperOS 后台清理与引导跳转；
- SAF 目录授权持久性；
- 大文件（≥ 1 GB）与长时间下载；
- **未在任何具体机型（包括红米 Turbo 4 Pro）上测试**。

真机验收请按 **[docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md)** 执行（A1–A7 为验收项，B1–B8 为补充边界用例），并把结果回填到该文件的汇总表与本节。

---

## 十一、已知问题（摘要）

完整列表（含现象、原因、规避、后续计划）见 **[docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)**。

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| KI-01 | 不支持 BT / 磁力链接 | 无法下载 BT 资源 |
| KI-02 | 不支持 Cookie / Referer / 登录鉴权 | 网盘直链、防盗链资源失败 |
| KI-03 | 服务器不支持 Range 时只能整文件重下 | 弱网大文件体验差 |
| KI-04 | 发布阶段约 2 倍文件大小的临时空间 | 大文件需预留空间 |
| KI-05 | SAF 授权可能被系统回收 | 保存失败，需重新选择 |
| KI-06 | 未知大小/大文件进度不精确 | 进度条与 ETA 抖动 |
| KI-07 | Android 15 前台服务 6 小时/24 小时限制 | 超长下载需用户重新打开应用 |
| KI-08 | HyperOS 激进后台清理 | 需白名单/锁定后台 |
| KI-09 | 无校验和验证 | 无法发现内容损坏或被替换 |
| KI-10 | 无开机自启 | 重启后需手动打开应用 |
| KI-11 | 仅聚合通知，无每任务独立通知 | 通知栏无法单独控制任务 |
| KI-12 | **未在真实红米 Turbo 4 Pro 上测试** | 真机行为未知 |
| KI-13 | 允许 HTTP 明文下载 | 内容可能被中间人篡改 |
| KI-17 | 网络切换不会立即中断已有连接 | 可能产生少量蜂窝流量 |

---

## 十二、许可证合规

| 项目 | 许可证 | 与本项目的关系 |
| --- | --- | --- |
| 本项目代码 | **MIT** | 见 [LICENSE](LICENSE)，版权行 `Copyright (c) 2026 Motrix Lite contributors` |
| Motrix 桌面版 | MIT | 仅参考产品与交互思路，**未使用其代码/资源/图标** |
| aria2 | GPLv2 | **未集成、未分发**，因此本 APK 不承担 GPLv2 源码提供义务 |
| OkHttp / Okio | Apache-2.0 | 随 APK 分发 |
| Kotlin / Coroutines | Apache-2.0 | 随 APK 分发 |
| AndroidX / Jetpack Compose / Material3 / material-icons | Apache-2.0 | 随 APK 分发 |

完整清单见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)；GPLv2 触发条件、未来集成 aria2 的义务与风险、商标声明、分发检查清单见 **[docs/COMPLIANCE.md](docs/COMPLIANCE.md)**。

**商标**：Motrix 的名称与商标归其作者所有。本项目为**非官方独立实现**，不使用 Motrix 官方图标、Logo 或品牌视觉素材，与官方无隶属、赞助或背书关系。

---

## 附录 A：命令速查

```powershell
# ---------- 构建 ----------
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintVitalRelease
.\gradlew.bat clean

# ---------- 安装 / 卸载 ----------
adb devices
adb install -r dist\MotrixLite-release.apk
adb uninstall com.motrixlite.downloader

# ---------- 签名 ----------
keytool -list -v -keystore keystore\release.keystore -storepass motrixlite2026
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk

# ---------- 哈希 ----------
Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256

# ---------- 本地测试服务器 ----------
python tools\range_test_server.py --port 8000 --size-mb 50
python tools\range_test_server.py --port 8000 --size-mb 50 --throttle-kbps 300
adb reverse tcp:8000 tcp:8000
#   应用内访问 http://127.0.0.1:8000/testfile.bin

# ---------- 调试 ----------
adb logcat -s DownloadService:V AndroidRuntime:E
adb shell dumpsys activity services com.motrixlite.downloader
adb shell dumpsys notification --noredact | Select-String "MotrixLite"
adb shell ls -l /sdcard/Download/MotrixLite/
adb shell am force-stop com.motrixlite.downloader
```

macOS / Linux：把 `.\gradlew.bat` 换成 `./gradlew`，路径分隔符换成 `/`，`Select-String` 换成 `grep`，`Get-FileHash` 换成 `shasum -a 256`。

---

## 附录 B：目录树（含文档）

```
motrix-lite/
├── README.md                     ← 本文件
├── LICENSE                       ← MIT
├── THIRD_PARTY_NOTICES.md        ← 第三方组件清单
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD_AND_SIGN.md
│   ├── TEST_CHECKLIST.md
│   ├── KNOWN_ISSUES.md
│   └── COMPLIANCE.md
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/motrixlite/downloader/
│       │   │   ├── App.kt
│       │   │   ├── AppContainer.kt
│       │   │   ├── data/
│       │   │   │   ├── DownloadRepository.kt
│       │   │   │   ├── RepoEvent.kt
│       │   │   │   ├── SettingsRepository.kt
│       │   │   │   ├── TaskJsonCodec.kt
│       │   │   │   ├── TaskStore.kt
│       │   │   │   └── model/Models.kt
│       │   │   ├── engine/
│       │   │   │   ├── DownloadCoordinator.kt
│       │   │   │   ├── FileNameUtils.kt
│       │   │   │   ├── Formatters.kt
│       │   │   │   ├── HttpCalls.kt
│       │   │   │   ├── HttpProbe.kt
│       │   │   │   ├── RateLimiter.kt
│       │   │   │   ├── SegmentPlanner.kt
│       │   │   │   ├── SpeedMeter.kt
│       │   │   │   └── TaskDownloader.kt
│       │   │   ├── network/NetworkMonitor.kt
│       │   │   ├── service/
│       │   │   │   ├── DownloadService.kt
│       │   │   │   ├── NotificationHelper.kt
│       │   │   │   └── ServiceController.kt
│       │   │   ├── storage/DestinationPublisher.kt
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── MainScreen.kt
│       │   │   │   ├── MainViewModel.kt
│       │   │   │   ├── components/{Common,TaskCard}.kt
│       │   │   │   ├── screens/SettingsScreen.kt
│       │   │   │   ├── sheets/{NewTaskSheet,TaskDetailSheet}.kt
│       │   │   │   └── theme/Theme.kt
│       │   │   └── util/{FileOpener,HyperOSGuide,NotificationPermission}.kt
│       │   └── res/
│       │       ├── drawable/（9 个自绘 Vector，含启动图标前景）
│       │       ├── mipmap-anydpi-v26/（自适应图标）
│       │       ├── values/{strings,colors,themes}.xml
│       │       ├── values-night/themes.xml
│       │       ├── xml/file_paths.xml
│       │       └── xml/data_extraction_rules.xml
│       └── test/java/com/motrixlite/downloader/
│           ├── data/TaskJsonCodecTest.kt
│           └── engine/{Formatters,FileNameUtils,SegmentPlanner,RateLimiter,HttpProbe,TaskDownloader}Test.kt
├── gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── keystore.properties
├── keystore/release.keystore
├── tools/range_test_server.py
├── dist/{MotrixLite-debug.apk,MotrixLite-release.apk,SHA256SUMS.txt}
├── local.properties.example（SDK 路径示例，可复制为 local.properties）
└── local.properties（本机生成，不提交）
```

---

## 附录 C：文件清单

### C.1 配置与构建

| 文件 | 说明 |
| --- | --- |
| `settings.gradle.kts` | 插件仓库（`google`/`mavenCentral`/`gradlePluginPortal`）、依赖仓库（`FAIL_ON_PROJECT_REPOS`）、模块 `:app` |
| `build.gradle.kts` | 根脚本，声明 AGP 8.7.3 与 Kotlin 2.0.21 插件（`apply false`） |
| `gradle.properties` | `-Xmx4096m`、UTF-8、并行构建、构建缓存、AndroidX、非传递 R 类 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.9 发行版地址、60s 网络超时 |
| `app/build.gradle.kts` | SDK 版本、版本号、签名配置（存在 `keystore.properties` 时）、R8、Compose、依赖清单 |
| `app/proguard-rules.pro` | R8 规则（model 保留、OkHttp 可选依赖 `-dontwarn`、保留行号） |
| `app/src/main/AndroidManifest.xml` | 7 项权限、`MainActivity`、`DownloadService`（`dataSync`）、`FileProvider`、`usesCleartextTraffic`、禁用备份（`dataExtractionRules` / `fullBackupContent="false"`） |
| `keystore.properties` | 演示签名配置（`storeFile`/`storePassword`/`keyAlias`/`keyPassword`），已被 `.gitignore` 忽略 |
| `local.properties` | 本机 SDK 路径，本机生成，不提交 |
| `local.properties.example` | SDK 路径示例，复制为 `local.properties` 后改成自己机器的路径；也可改用 `ANDROID_HOME` 环境变量 |
| `.gitignore` | 忽略 `build/`、`.gradle/`、`local.properties`、`*.keystore`、`keystore.properties`、`dist/` |

### C.2 文档

| 文件 | 说明 |
| --- | --- |
| `README.md` | 主文档（本文件） |
| `LICENSE` | MIT 全文，`Copyright (c) 2026 Motrix Lite contributors` |
| `THIRD_PARTY_NOTICES.md` | 第三方组件、版本、许可证与分发状态 |
| `docs/ARCHITECTURE.md` | 架构、状态机、内核算法、持久化、并发、扩展点 |
| `docs/BUILD_AND_SIGN.md` | 环境安装、构建、R8 排错、签名、验证、离线构建 |
| `docs/TEST_CHECKLIST.md` | A1–A7 + B1–B8 真机验收清单、测试服务器用法、缺陷表 |
| `docs/KNOWN_ISSUES.md` | KI-01～KI-22 已知问题 |
| `docs/COMPLIANCE.md` | 许可证、商标、隐私、分发检查清单 |

### C.3 工具与产物

| 文件 | 说明 |
| --- | --- |
| `tools/range_test_server.py` | 本地 HTTP Range 测试服务器（支持 `--size-mb`、`--file`、`--throttle-kbps`） |
| `keystore/release.keystore` | 演示用 PKCS12 密钥库（别名 `motrixlite`），**不可用于上架** |
| `app/build/outputs/apk/debug/app-debug.apk` | Debug 产物（10,730,630 字节） |
| `app/build/outputs/apk/release/app-release.apk` | Release 产物（1,477,909 字节，R8 + 演示签名） |
| `dist/MotrixLite-debug.apk` | 分发副本（SHA-256 `34961208…cd2819`） |
| `dist/MotrixLite-release.apk` | 分发副本（SHA-256 `cd5872c1…0196e1`） |
| `dist/SHA256SUMS.txt` | 上两个文件的哈希与大小 |

---

## 附录 D：术语表

| 术语 | 含义 |
| --- | --- |
| staging | 下载中的临时文件，位于应用专属目录，完成后删除 |
| 分段（Segment） | 文件的一段连续字节区间 `[start, end]`，对应一个 Range 请求 |
| Range 请求 | HTTP `Range: bytes=start-end`，服务器以 `206 Partial Content` 响应 |
| 断点续传 | 暂停/重启后从已下载位置继续，依赖分段进度与服务器文件一致性校验 |
| `ETag` / `Last-Modified` | 服务器资源版本标识，用于 `If-Range` 与变化检测 |
| `IS_PENDING` | `MediaStore` 的写入中标记，置 1 时其他应用不可见 |
| FGS | Foreground Service，前台服务 |
| dataSync | Android 14+ 前台服务类型之一，适用于数据同步/下载；Android 15 有 6 小时/24 小时限制 |
| SAF | Storage Access Framework，通过用户选择目录获得 URI 授权 |
| 令牌桶 | 限速算法：按时间补充令牌，取不到令牌则等待 |
| HyperOS | 小米/红米的 Android 定制系统（MIUI 后继） |
| `desiredRunning` | 用户是否希望任务运行，用于重启后区分"被杀"与"主动暂停" |

---

## 附录 E：相关文档

| 文档 | 内容 |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、状态机、下载内核、持久化、并发模型、扩展点 |
| [docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md) | 环境安装、构建、R8 排错、签名与验证、产物校验 |
| [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md) | 真机验收清单（A1–A7、B1–B8）、测试服务器用法、缺陷表 |
| [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | 22 条已知问题与限制 |
| [docs/COMPLIANCE.md](docs/COMPLIANCE.md) | MIT / GPLv2 / Apache-2.0 分析、商标、隐私、分发检查清单 |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | 第三方组件与许可证清单 |
| [LICENSE](LICENSE) | MIT 许可证全文 |

---

**Motrix Lite** · MIT License · 非官方独立实现 · 未在真机验收，使用前请阅读 [已知问题](docs/KNOWN_ISSUES.md)
