# 架构说明（ARCHITECTURE）

本文描述 Motrix Lite Android 应用的模块划分、运行时模型、下载内核算法、状态机、持久化与前台服务设计。所有描述均对应仓库中的真实源码，路径相对工程根目录。

- 应用：Motrix Lite（`com.motrixlite.downloader`）
- 形态：单 Activity + Compose UI + 单进程前台服务
- 下载内核：自研 OkHttp 分段下载（**不含 aria2**）

---

## 一、整体结构

```
┌──────────────────────────────────────────────────────────┐
│                     UI 层（Compose）                      │
│  MainActivity → MainScreen → TaskCard / 底部弹层 / 设置页  │
│  MainViewModel（AndroidViewModel）                         │
└───────────────┬──────────────────────────────┬───────────┘
                │ StateFlow / 方法调用           │ startForegroundService
                ▼                               ▼
┌───────────────────────────────┐   ┌──────────────────────────────┐
│ DownloadRepository（内存 SoT）│   │ DownloadService（dataSync）   │
│ StateFlow<List<DownloadTask>> │◄──┤  - 常驻通知 / WakeLock        │
│ JSON 持久化（700ms 防抖）      │   │  - 生命周期与时限处理          │
└───────┬───────────────▲───────┘   └──────────────┬───────────────┘
        │               │                          │
        │ tasks.json    │ 状态/进度回写              ▼
        ▼               │              ┌──────────────────────────┐
┌──────────────┐        └──────────────┤ DownloadCoordinator       │
│  TaskStore   │                       │ 队列 / 并发 / Wi-Fi / 重试 │
└──────────────┘                       └───────┬──────────────────┘
                                               │
                     ┌─────────────────────────┼─────────────────────────┐
                     ▼                         ▼                         ▼
            ┌─────────────────┐      ┌──────────────────┐      ┌──────────────────┐
            │  HttpProbe      │      │  TaskDownloader   │      │ DestinationPublisher │
            │  元数据探测      │      │  分段下载 + 重试   │      │ MediaStore / SAF     │
            └─────────────────┘      └────────┬─────────┘      └──────────────────┘
                                              │
                                    ┌─────────┴─────────┐
                                    ▼                   ▼
                            ┌──────────────┐   ┌──────────────┐
                            │  RateLimiter │   │  SegmentPlanner │
                            │  全局令牌桶   │   │  分段规划        │
                            └──────────────┘   └──────────────┘
```

### 模块职责

| 包 | 关键类 | 职责 |
| --- | --- | --- |
| `com.motrixlite.downloader` | `App`、`AppContainer` | 进程级单例装配（手工依赖注入） |
| `data` | `DownloadRepository`、`TaskStore`、`TaskJsonCodec`、`SettingsRepository`、`RepoEvent` | 任务状态唯一数据源、JSON 持久化、设置存储、领域事件 |
| `data.model` | `DownloadTask`、`Segment`、`TaskState`、`AppSettings` | 数据模型与状态枚举 |
| `engine` | `HttpProbe`、`SegmentPlanner`、`TaskDownloader`、`DownloadCoordinator`、`RateLimiter`、`SpeedMeter`、`FileNameUtils`、`Formatters` | 下载内核与调度 |
| `storage` | `DestinationPublisher` | staging → 用户可见位置的发布 |
| `service` | `DownloadService`、`NotificationHelper`、`ServiceController` | 前台服务、通知、UI 侧启动封装 |
| `network` | `NetworkMonitor` | 默认网络状态（Wi-Fi / 蜂窝 / 在线） |
| `util` | `HyperOSGuide`、`NotificationPermission`、`FileOpener` | 系统设置跳转、权限查询、打开文件 |
| `ui` | `MainActivity`、`MainViewModel`、`MainScreen`、`screens/`、`sheets/`、`components/`、`theme/` | Compose 界面与交互 |

---

## 二、运行时模型

### 2.1 进程与作用域

- **单进程**：UI 与前台服务共享同一进程、同一个 `AppContainer` 实例，因此任务状态天然一致，无需 Binder/AIDL 或数据库。
- `AppContainer`（`AppContainer.kt`）持有：`applicationScope`（`SupervisorJob + Dispatchers.IO`）、`stagingDir`、`SettingsRepository`、`DownloadRepository`、`OkHttpClient`、`HttpProbe`、`RateLimiter`、`NetworkMonitor`、`DestinationPublisher`、`ServiceController`。
- `App.onCreate()` 中构造容器并创建通知渠道（`App.kt:11-15`）。
- `DownloadCoordinator` **不是**单例：它在 `DownloadService.onCreate()` 中创建，`onDestroy()` 中 `stop()`，与前台服务生命周期绑定（`service/DownloadService.kt:52-60`、`:196-207`）。

### 2.2 为什么不用 Hilt / Room

- 依赖关系简单（十余个对象、无 scoped 需求），手写容器可读性更好，且避免注解处理器拖慢构建（见 `AppContainer.kt` 头部注释）。
- 任务数据量小、以整表读写为主，JSON 单文件 + 原子替换即可满足；引入 Room 会增加 schema/迁移成本，而对"多任务列表 + 秒级进度"没有收益。

### 2.3 线程模型

| 场景 | 线程/调度器 |
| --- | --- |
| UI 组合与状态收集 | 主线程（`collectAsStateWithLifecycle`） |
| 仓库持久化、文件 IO、HTTP | `Dispatchers.IO` |
| 分段下载 | 每个分段一个 `async(Dispatchers.IO)`（`TaskDownloader.kt:288-292`） |
| 前台服务监控循环 | `Dispatchers.Main.immediate` + `delay`（`DownloadService.kt:35`） |
| 进度上报 | 独立 `launch` 协程，500ms 采样（`TaskDownloader.kt:280-286`） |

取消语义：协程取消即"暂停"。`TaskDownloader.run()` 捕获 `CancellationException` 并返回 `DownloadResult.Paused`，同时 `cancelActiveCalls()` 主动 `Call.cancel()` 中断阻塞读（`TaskDownloader.kt:120-121`、`:146-149`）。

---

## 三、数据模型与状态机

### 3.1 核心模型

`data/model/Models.kt`：

- `DownloadTask`：任务的全部持久化字段 + 运行期字段（`speedBytesPerSec`、`speedHistory`、`publishProgress`）。
- `Segment`：闭区间 `[start, end]`；`end = -1` 表示服务器未给总长度（单连接、不可分段）。
- `TaskState`：9 个状态，并提供 `isRunning` / `needsService` / `isFinished` 派生属性。
- `AppSettings`：并发、连接数、限速、仅 Wi-Fi、自动恢复、通知、保存模式、SAF URI、主题。
- `SaveMode`：`MEDIASTORE` / `SAF`；`ThemeMode`：`SYSTEM` / `LIGHT` / `DARK`。

### 3.2 状态机

```
                 ┌──────────────┐
     创建任务 →   │   QUEUED     │◄───────────────┐
                 └──────┬───────┘                │ 用户恢复 / 自动重试 / Wi-Fi 恢复
                        │ 调度器取到并发槽位       │
                        ▼                        │
                 ┌──────────────┐   探测完成      │
                 │  CONNECTING  ├──────────► DOWNLOADING
                 └──────┬───────┘                │
                        │ 可重试失败              │ 下载完成
                        ▼                        ▼
                 ┌──────────────┐          ┌──────────┐
                 │  RETRYING    │          │  SAVING  │
                 │ 3s/8s/15s    │          └────┬─────┘
                 └──────┬───────┘               │ 发布成功
                        │ 重试耗尽               ▼
                        ▼                  ┌───────────┐
                 ┌──────────────┐          │ COMPLETED │
                 │   FAILED     │          └───────────┘
                 └──────────────┘
   任意活动态 ──用户暂停/系统限制──► PAUSED
   仅 Wi-Fi 且当前为蜂窝 ──► WAITING_FOR_WIFI ──连回 Wi-Fi──► QUEUED
```

关键判定（`Models.kt:23-32`）：

```kotlin
val isRunning: Boolean
    get() = this == CONNECTING || this == DOWNLOADING || this == RETRYING || this == SAVING

val needsService: Boolean
    get() = this == QUEUED || isRunning || this == WAITING_FOR_WIFI
```

`needsService` 决定前台服务是否还需要存活：没有任何任务需要服务且空闲超过 4 秒后，服务自行 `stopSelf()`（`DownloadService.kt:140-151`）。

### 3.3 `desiredRunning`：用户意图与当前状态的分离

`desiredRunning` 记录"用户希望这个任务处于运行状态"，与 `state` 解耦：

- 用户暂停 → `state=PAUSED`、`desiredRunning=false`；
- App 被系统杀死后重启 → 若 `state` 是活动态且 `desiredRunning=true`，恢复为 `QUEUED`；否则保持 `PAUSED`（`DownloadRepository.kt:81-100`）。

这个字段解决了"进程被杀时正在下载"与"用户主动暂停"两种情况在持久化后无法区分的问题。

---

## 四、下载引擎

### 4.1 端到端流程

```
createTask()                  用户点击「开始下载」
   │
   ▼
Coordinator.kick()            有并发槽位且网络允许
   │
   ▼
TaskDownloader.run()
   ├─ 1. HttpProbe.probe()     GET + Range: bytes=0-0（必要时回退 HEAD）
   ├─ 2. prepare()             校验 ETag/Last-Modified/长度，决定是否复用旧分段
   ├─ 3. 0 字节特判            直接生成空文件 → COMPLETED
   ├─ 4. SegmentPlanner.plan() 1–16 段
   ├─ 5. ensureStagingFileLength()  RAF.setLength(totalBytes) 预分配
   ├─ 6. downloadAll()         每段一个协程，独立 Range 请求
   │      └─ 失败 → 段级重试 3 次（1.5s/3s/6s，上限 12s）
   ├─ 7. finalizeTask()        分段完整性 + 文件长度校验
   └─ 8. 返回 Completed
   │
   ▼
Coordinator.publishResult()
   ├─ 状态 SAVING
   ├─ DestinationPublisher.publish()  MediaStore / SAF / 旧系统回退
   └─ markCompleted() → COMPLETED，删除 staging
```

### 4.2 元数据探测（`engine/HttpProbe.kt`）

只发一个 `Range: bytes=0-0` 的 GET，**不读响应体**，开销极小：

| 响应 | 解释 | `supportsRanges` | `totalBytes` |
| --- | --- | --- | --- |
| `206` | 服务器按 Range 返回 1 字节 | `true` | 从 `Content-Range: bytes 0-0/N` 解析 |
| `416` | 空文件或范围不可满足 | `true` | 从 `Content-Range: bytes */N` 解析 |
| `200` | 服务器忽略 Range | `false` | `Content-Length` |
| `4xx` | 部分签名链接拒绝 Range | — | 回退 `HEAD` 再解析 |
| `5xx` | 服务端暂时不可用 | — | 抛 `ProbeException(retryable=true)` |

细节：

- 文件名优先级：`Content-Disposition` 的 `filename*`（RFC 5987）→ `filename=` → URL 路径末段 → `download-<时间戳>`（`FileNameUtils.fromContentDisposition` / `fromUrl`）。
- `finalUrl` 取自重定向后的最终请求 URL，后续所有分段请求都打到最终 URL，避免每次都走重定向。
- User-Agent 固定为 `MotrixLite/1.0 (Android; okhttp)`（`HttpProbe.kt:147`），不带浏览器 UA 伪装。
- 若 `200` 但响应头声称 `Accept-Ranges: bytes`，仍按**不支持 Range** 处理——以实际响应为准更安全（`HttpProbe.kt:106-109`）。

### 4.3 分段规划（`engine/SegmentPlanner.kt`）

```kotlin
fun plan(totalBytes: Long, connections: Int): List<Segment> {
    val conns = connections.coerceIn(1, 16)
    if (totalBytes <= 0L) return listOf(Segment(index = 0, start = 0, end = -1))
    if (conns <= 1 || totalBytes < MIN_MULTI_SEGMENT_BYTES) {      // 2 MB
        return listOf(Segment(index = 0, start = 0, end = totalBytes - 1))
    }
    val segmentCount = minOf(conns.toLong(), maxOf(2L, totalBytes / MIN_SEGMENT_BYTES))
        .toInt().coerceIn(2, 16)                                    // 每段至少 1 MB
    ...
}
```

规则总结：

| 条件 | 分段数 |
| --- | --- |
| 总长度未知（`<= 0`） | 1 段，`end = -1`，可续传但不可并行 |
| 连接数 = 1 | 1 段 |
| 文件 < 2 MB | 1 段（小文件并行反而更慢） |
| 其他 | `min(连接数, max(2, 总长/1MB))`，限制在 `2..16` |

分段为**连续闭区间**，无空洞、无重叠，便于完整性校验和断点续传复用。

### 4.4 分段下载与校验（`engine/TaskDownloader.kt`）

每个分段：

1. `Range: bytes=<start+已下载>-<end>`（未知长度时为 `bytes=N-`）；
2. 若已知 `ETag`（非弱校验）则带 `If-Range: <etag>`，否则带 `If-Range: <Last-Modified>`；服务器若发现资源变化会返回 `200` 而不是 `206`；
3. `206` → 校验 `Content-Range` 起点与期望一致，`RandomAccessFile.seek()` 后顺序写入；
4. `200` → 服务器忽略 Range：
   - 已有下载进度且此前声明支持 Range → 抛 `ServerChangedException`（不可恢复，提示重新下载）；
   - 否则清空 staging、从 0 开始单连接写入（降级）；
5. `416` → 若该段已完整则视为完成，否则 `FatalHttpException`（需删除任务重下）；
6. 其余 `4xx` → `FatalHttpException`（不重试，如 401/403/404）；
7. `5xx`/IO 异常 → 段级重试，最多 3 次，退避 `1500ms × 2^(n-1)`，上限 12s（`TaskDownloader.kt:529-530`）。

**降级路径**：若任一分段抛出 `RangeUnsupportedException`（服务器对分段返回 `200`），`downloadWithFallback()` 会清空 staging、改为单连接，整文件重下（`TaskDownloader.kt:253-276`）。

**完整性校验**（`finalizeTask()`，`TaskDownloader.kt:443-468`）：

- 已知总长：所有段 `downloaded == length` 且求和等于 `totalBytes`，且 staging 文件长度等于 `totalBytes`；
- 未知总长：staging 文件长度等于各段下载量之和。
- 任一不满足 → 抛 `IOException`，走任务级重试。

**进度模型**：`AtomicLongArray` 按段计数，`reportProgress()` 每 500ms 汇总一次（`TaskDownloader.kt:470-491`），避免每读一个 buffer 就回调 UI。

### 4.5 断点续传

续传的判定集中在 `prepare()`（`TaskDownloader.kt:155-231`）：

| 检查 | 判定为"服务器文件已变化"的条件 |
| --- | --- |
| 总长度 | 上次已知总长 `> 0` 且与本次不同 |
| ETag | 双方都有 ETag 且不相同 |
| Last-Modified | 上次无 ETag、双方都有 Last-Modified 且不同 |
| staging 文件 | 记录有进度但 staging 文件不存在 |
| 未知长度 | staging 存在但文件长度与记录的已下载量不一致 |

一旦判定变化：删除 staging、进度清零、重新规划分段（`TaskDownloader.kt:190-193`）。

否则复用已有分段：若旧分段恰好覆盖完整文件（连续、求和等于总长）则原样沿用；若旧分段是"未知长度单段"则按新总长修正 `end`。这些安全检查保证**跨 App 重启续传不会把两代文件拼在一起**。

### 4.6 任务级重试（`engine/DownloadCoordinator.kt`）

只有 `DownloadResult.Failed.retryable = true` 才会自动重试：

```kotlin
if (result.retryable && autoRetry < AUTO_RETRY_DELAYS.size) {
    repository.setState(id, TaskState.RETRYING, result.message)
    delay(AUTO_RETRY_DELAYS[autoRetry])   // 3s / 8s / 15s
    autoRetry++
    repository.setState(id, TaskState.QUEUED, null)
    continue
}
repository.markFailed(id, result.message)
```

不可重试的错误：`ServerChangedException`（文件已变）、`FatalHttpException`（4xx/416 等）、`ProbeException(retryable=false)`。

---

## 五、调度器（`engine/DownloadCoordinator.kt`）

`kick()` 是唯一的调度入口，由四路事件触发：任务列表变化、设置变化、网络状态变化、服务启动。内部用 `synchronized(kickLock)` 串行化，避免并发启动同一任务。

```
kick():
  1. 取消不再需要运行的任务（已暂停/已删除/已完成/desiredRunning=false）
  2. 若网络允许，把 WAITING_FOR_WIFI 的任务改回 QUEUED
  3. 计算空槽位 = maxConcurrentTasks - activeJobs.size
  4. 按列表顺序取 QUEUED 任务：
       - 若 wifiOnly 且当前非 Wi-Fi → 置为 WAITING_FOR_WIFI 并跳过
       - 否则启动 runTask()，状态置 CONNECTING
```

- 并发上限 `1..5`，默认 3（`AppSettings.maxConcurrentTasks`，`SettingsRepository.setMaxConcurrentTasks` 做 `coerceIn(1,5)`）。
- Wi-Fi 策略在**启动时**判断，不做连接级中断：正在下载的任务若网络从 Wi-Fi 切到蜂窝，当前连接可能继续（由 OkHttp 的默认网络行为决定），下次 `kick()` 重排时会转为 `WAITING_FOR_WIFI`。这是已知行为，见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。
- 任务按 `DownloadRepository` 中的列表顺序（即创建顺序）调度，无优先级/插队机制。

---

## 六、持久化与恢复

### 6.1 文件布局

| 路径 | 内容 |
| --- | --- |
| `filesDir/tasks.json` | 任务列表（JSON，schema `version=1`） |
| `filesDir/tasks.json.tmp` | 写入中的临时文件，原子替换后消失 |
| `filesDir/tasks.json.bad-<ts>` | 解析失败时保留的坏文件副本 |
| `SharedPreferences("motrix_lite_settings")` | 用户设置 |
| `getExternalFilesDir(null)/staging/<uuid>.part` | 下载中的 staging 文件（外部私有目录；不可用时回退 `filesDir/staging`） |

### 6.2 原子写入

`TaskStore.save()`（`data/TaskStore.kt:33-50`）先写 `.tmp`，再 `Files.move(..., ATOMIC_MOVE)`；不支持原子移动时退化为 `REPLACE_EXISTING`。因此进程被杀最多丢失约 1 秒的进度（`SAVE_DEBOUNCE_MS = 700ms` + 写入耗时），不会产生半截 JSON。

写入触发策略：

- `setState`、`updateProgress` 等高频路径 → `requestSave()`（合流 Channel + 700ms 防抖）；
- `createTask`、`pause`、`resume`、`markCompleted`、`markFailed`、`removeTask` → `persistNow()`（防抖 + 立即写一次）；
- `MainActivity.onStop` 与服务 `onDestroy` → `flush()` 强制落盘。

### 6.3 恢复规则

`recoverAfterRestart()`（`DownloadRepository.kt:81-100`）：

| 上次状态 | `desiredRunning` | 恢复后 |
| --- | --- | --- |
| QUEUED / CONNECTING / DOWNLOADING / RETRYING / SAVING / WAITING_FOR_WIFI | `true` | `QUEUED`（重新排队） |
| 同上 | `false` | `PAUSED` |
| PAUSED / COMPLETED / FAILED | 任意 | 保持原状态 |

运行期字段（速度、速度历史、发布进度）一律清零。

> 注意：恢复为 `QUEUED` 后是否真正开始，还取决于服务是否在运行、并发槽位、Wi-Fi 策略与设置项 `autoResumeUnfinished`。`MainViewModel.init` 在 `autoResumeUnfinished=true` 且存在需要服务的任务时会拉起服务（`ui/MainViewModel.kt:63-70`）。

---

## 七、保存与发布（`storage/DestinationPublisher.kt`）

### 7.1 三条路径

| 条件 | 目标 | 权限 |
| --- | --- | --- |
| `task.saveMode == SAF` | 用户选择的 SAF 目录（`DocumentFile.createFile`） | 持久化 URI 授权 |
| Android 10+（API 29+）默认 | `MediaStore.Downloads`，`RELATIVE_PATH = "Download/MotrixLite/"` | 无需任何存储权限 |
| Android 8/9（API 26–28） | `getExternalFilesDir(DIRECTORY_DOWNLOADS)/MotrixLite` | 无需权限，卸载即删 |

### 7.2 MediaStore 写入时序

```
insert(IS_PENDING = 1)  →  openOutputStream("w")  →  256 KB 分块复制  →  update(IS_PENDING = 0)
```

- `IS_PENDING=1` 期间其他应用不可见，避免用户看到半截文件；异常时 `resolver.delete(uri)` 回滚。
- 重名处理：`name (1).ext`、`name (2).ext` … 最多尝试到 99；若仍冲突则沿用最后一个候选名，由 MediaStore 决定实际落盘名——见 `uniqueMediaStoreName()`。
- 发布进度通过 `updatePublishProgress()` 写回任务，`SAVING` 状态在 UI 上显示百分比。

### 7.3 staging + 发布的取舍

**为什么不在目标位置直接随机写？**

- MediaStore / SAF 的 `content://` 流不保证支持 `seek()`，无法做多连接随机写；
- 断点续传需要可寻址的本地文件；
- 下载中不希望在用户可见目录出现半成品文件。

**代价**：发布阶段需要把 staging 完整复制一份，**峰值临时占用约为文件大小的 2 倍**（staging + 目标文件），发布完成后删除 staging。这是已知限制，见 KI-04。

`DestinationPublisher.delete()` 支持按 `content://` / `file://` 删除已完成文件（"删除任务并同时删除文件"）。

---

## 八、前台服务与通知

### 8.1 服务契约

`service/DownloadService.kt`：

| 项 | 实现 |
| --- | --- |
| 声明类型 | `android:foregroundServiceType="dataSync"`（Manifest） |
| 运行时类型 | `FOREGROUND_SERVICE_TYPE_DATA_SYNC`（API 29+，否则 `0`） |
| 返回值 | `START_STICKY`；`startForeground` 失败时 `START_NOT_STICKY` |
| WakeLock | `PARTIAL_WAKE_LOCK`，`setReferenceCounted(false)`，上限 6 小时，仅有任务运行时持有 |
| 空闲退出 | 无任务需要服务且空闲 4 秒 → `stopForeground(REMOVE)` + `stopSelf()` |
| 通知刷新 | 800ms 轮询重建前台通知 |
| 超时回调 | `onTimeout(startId)`（Android 14 shortService，保留兼容）与 `onTimeout(startId, fgsType)`（Android 15 dataSync） |

### 8.2 启动失败与降级

```kotlin
try {
    startForegroundWithNotification()
} catch (t: Throwable) {
    Log.e(TAG, "startForeground failed", t)
    handleStartForegroundFailure()   // 暂停全部任务 + Snackbar 提示 + stopSelf
    return START_NOT_STICKY
}
```

`startForeground` 可能因 Android 12+ 后台启动限制、Android 14+ 缺少 FGS 类型权限、Android 15 时限等原因抛出 `ForegroundServiceStartNotAllowedException`。此时服务不硬扛：暂停任务、通过 `RepoEvent.Message` 提示用户回到前台继续。

UI 侧 `ServiceController.start()` 同样吞掉异常（`service/ServiceController.kt:19-28`），因为"服务是否起来"最终会反映在任务状态上。

### 8.3 通知

`service/NotificationHelper.kt`：

- 渠道 `downloads`（IMPORTANCE_LOW、无声、无振动、不显示角标）：**单条聚合**前台通知，包含总百分比、总速度、等待 Wi-Fi 数量或保存进度；带「暂停全部 / 继续全部」动作。
- 渠道 `download_results`（IMPORTANCE_DEFAULT）：完成/失败提醒，按任务各自一条（`resultId = hash & 0xFFFF + 1000`）。
- 通知权限未授予时 `updateForeground` / `postCompleted` / `postFailed` 直接返回（`NotificationPermission.isGranted` 基于 `areNotificationsEnabled()`）。
- 前台通知 ID 固定 `1001`，通过 `setOnlyAlertOnce(true)` + `setSilent(true)` 避免刷屏。

### 8.4 与协调器的关系

服务 `onCreate` 时构造 `DownloadCoordinator`，`onStartCommand` 中 `coordinator.start()`；`onDestroy` 中 `coordinator.stop()`。协调器自己持有 IO 作用域，因此服务的主线程作用域只负责监控循环与事件订阅，不会被下载阻塞。

---

## 九、网络与限速

### 9.1 OkHttp 配置（`AppContainer.kt:44-55`）

| 参数 | 值 |
| --- | --- |
| connectTimeout | 20s |
| readTimeout | 60s |
| writeTimeout | 30s |
| followRedirects / followSslRedirects | true |
| retryOnConnectionFailure | true |
| 探测专用 client | 同一连接池 + `callTimeout = 25s` |

### 9.2 全局令牌桶（`engine/RateLimiter.kt`）

- 单例，所有任务、所有连接共享；`limitBytesPerSecond <= 0` 表示不限速，`acquire()` 直接返回（零开销）。
- 容量 = `max(limit, byteCount)`，按 `elapsed × limit` 补充令牌；不足时 `delay(ceil(needed / limit × 1000))`。
- 读块大小随限速自适应：`min(64KB, max(1KB, limit/4))`，低限速时用小 buffer，保证暂停响应及时、限速更平滑。
- 设置变更即时生效：`AppContainer` 与 `DownloadCoordinator` 都订阅 `settings`，写入 `rateLimiter.limitBytesPerSecond`。

### 9.3 网络状态

`NetworkMonitor` 注册 `registerDefaultNetworkCallback`，读取 `NET_CAPABILITY_INTERNET && NET_CAPABILITY_VALIDATED` 判断在线，`TRANSPORT_WIFI || TRANSPORT_ETHERNET` 视为 Wi-Fi。状态变化会触发 `kick()`，实现"仅 Wi-Fi"的自动暂停/恢复。

---

## 十、UI 架构

- **单 Activity**：`MainActivity` 只做 `setContent` 与主题注入（`ui/MainActivity.kt`）。
- **状态**：全部来自 `MainViewModel`（`AndroidViewModel`）暴露的 `StateFlow`；UI 不持有业务状态。
- **导航**：无 Navigation 组件，用 `selectedTab`（任务/设置）+ `ModalBottomSheet`（新建任务、任务详情）+ `AlertDialog`（删除确认）。
- **事件**：一次性消息走 `UiEvent`（`SharedFlow`）→ Snackbar；权限申请走 `rememberLauncherForActivityResult`。
- **主题**：自定义 `darkColorScheme` / `lightColorScheme`，支持跟随系统/浅色/深色（`ui/theme/Theme.kt`）。
- **横幅**：通知权限被拒 → 顶部横幅；电池优化未关闭 → 后台运行建议卡片（可"不再提示"）。

---

## 十一、关键设计决策

| 决策 | 选择 | 理由 | 代价 |
| --- | --- | --- | --- |
| 语言/UI | Kotlin + Compose | 原生 API（FGS、MediaStore、SAF、通知）零桥接；release 包仅约 1.4 MB | Compose 与 AGP/Kotlin 版本强耦合 |
| 下载内核 | 自研 OkHttp | 无 GPL 分发义务；可精确控制暂停、限速、错误分类 | 无 BT/磁力、需自行维护协议细节 |
| 下载落盘 | 应用专属 staging + 随机写 | 支持 seek、可断点续传、不暴露半成品 | 发布阶段 2 倍峰值空间 |
| 发布方式 | MediaStore（10+）/ SAF / 私有目录（8/9） | 零存储权限，符合分区存储 | 8/9 上文件不对所有文件管理器可见 |
| 持久化 | 单 JSON + 原子替换 | 简单、可读、易调试 | 任务数很多时整表重写有开销 |
| 保活 | 前台服务 dataSync + WakeLock | 系统允许的长时间下载方式 | 受 Android 15 6h/24h 限制 |
| 依赖注入 | 手写容器 | 构建快、依赖图清晰 | 无编译期校验 |

---

## 十二、扩展点

| 方向 | 切入点 | 说明 |
| --- | --- | --- |
| 磁力/BT | 新增 `engine/torrent/`，在 `HttpProbe` 前分流 scheme（`magnet:`、`*.torrent`） | 需要 bencode、metadata 交换、peer wire、tracker/DHT；Android 后台与端口限制是主要障碍 |
| 鉴权下载 | `HttpProbe`/`TaskDownloader` 增加请求头注入（Cookie/Referer/Authorization） | 需 UI 输入与安全存储，注意不要把凭据写进 `tasks.json` 明文 |
| 校验和 | `finalizeTask()` 后增加 SHA-256/MD5 校验 | 可作为可选字段 `expectedHash` 持久化 |
| 每任务独立通知 | `NotificationHelper` 按任务分配通知 ID | 需处理大量任务的系统限流（Android 单应用通知上限） |
| 仅 HTTPS 开关 | `OkHttp` 拦截器或 `NetworkSecurityPolicy` 判断 | 可替代全局 `usesCleartextTraffic` |
| 自定义保存子目录 | `DestinationPublisher.RELATIVE_PATH` 可配置 | MediaStore 支持任意相对路径 |
| 代理 | `OkHttpClient.Builder.proxy()` | 需要 UI 与持久化 |
| 导入/导出任务 | `TaskJsonCodec` 已具备编解码能力 | 直接复用 |

---

## 十三、并发安全要点

| 共享状态 | 保护方式 |
| --- | --- |
| `_tasks`（任务列表） | `MutableStateFlow.update {}`（CAS 语义） |
| 持久化 | `persistMutex` + `TaskStore` 内部 `synchronized(lock)` |
| 加载 | `loadMutex` + `_loaded` 幂等标志 |
| 调度 | `synchronized(kickLock)` |
| 分段进度 | `AtomicLongArray`（按段索引） |
| 活动 Call 集合 | `ConcurrentHashMap.newKeySet()` |
| 活动任务/下载器映射 | `ConcurrentHashMap` |

已知不严谨之处：`DownloadRepository.speedHistory` 在 `updateProgress` 中追加并 `takeLast(30)`，每次进度回调都会复制列表；在高频回调下会产生一定的 GC 压力，但已被 500ms 采样间隔限制。

---

## 十四、源码索引

| 关注点 | 文件 |
| --- | --- |
| 进程装配 | `app/src/main/java/com/motrixlite/downloader/App.kt`、`AppContainer.kt` |
| 状态机/模型 | `.../data/model/Models.kt` |
| 任务仓库 | `.../data/DownloadRepository.kt` |
| 持久化 | `.../data/TaskStore.kt`、`.../data/TaskJsonCodec.kt` |
| 探测 | `.../engine/HttpProbe.kt` |
| 分段规划 | `.../engine/SegmentPlanner.kt` |
| 下载执行 | `.../engine/TaskDownloader.kt` |
| 调度 | `.../engine/DownloadCoordinator.kt` |
| 限速 | `.../engine/RateLimiter.kt` |
| 发布 | `.../storage/DestinationPublisher.kt` |
| 服务 | `.../service/DownloadService.kt`、`.../service/NotificationHelper.kt` |
| UI | `.../ui/MainActivity.kt`、`.../ui/MainViewModel.kt`、`.../ui/MainScreen.kt` |
| 平台适配 | `.../util/HyperOSGuide.kt`、`.../network/NetworkMonitor.kt` |
