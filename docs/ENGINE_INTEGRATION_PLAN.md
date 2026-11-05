# 双下载内核集成方案与实施对照（Gopeed + aria2）

- 适用工程：Motrix Lite **1.1.1**（versionCode 3，整体 GPLv3）
- 状态：**已实施**。本文既是集成方案，也是"方案 → 实际代码"的对照说明；下文以实际实现为准，并明确列出与 1.0.0 规划稿的差异。
- 目标：设置页新增「下载引擎」选择；新建任务使用当前引擎；旧任务保留原引擎；三个引擎任务在 UI 统一展示；集成 Gopeed AAR 与 aria2 子进程；保持前台服务、通知、断点续传、限速、Wi-Fi 策略等能力一致。

> 前置条件（已满足）：许可证决策为 **整体 GPLv3 开源**（见 [ENGINE_LICENSE_ANALYSIS.md](ENGINE_LICENSE_ANALYSIS.md)）。任何"双内核同 APK 仍闭源"的方案都不可行。

---

## 0. 实施结果摘要

| 规划项 | 实际实现 | 状态 |
| --- | --- | --- |
| 三引擎任务模型 | `DownloadEngine { BUILTIN, GOPEED, ARIA2 }`；`DownloadTask.engine/engineTaskId/engineStatus`；`AppSettings.downloadEngine` | ✅ |
| JSON schema v2 | `TaskJsonCodec.VERSION = 2`；旧 v1 缺省 `BUILTIN`，有单元测试覆盖 | ✅ |
| 设置页引擎选择 | 三个单选项；不可用引擎禁用并显示原因；说明"仅影响新建任务" | ✅ |
| 新建任务引擎校验 | `MainViewModel.startDownload()` 校验目标引擎可用性，不可用时提示并拒绝创建 | ✅ |
| 统一任务列表 | Repository 为唯一数据源；任务卡片/详情显示引擎、engineTaskId、原始状态 | ✅ |
| 内置引擎调度 | `DownloadCoordinator` 仅处理 `engine == BUILTIN` | ✅ |
| aria2 控制器 | `Aria2EngineController`：子进程、JSON-RPC、gid 映射、轮询、发布、回收 | ✅ |
| Gopeed 控制器 | `GopeedEngineController`：反射启动 AAR、内嵌 REST、轮询、发布、停止回收 | ✅ |
| 可用性探测 | aria2 检查 `nativeLibraryDir/libaria2c.so`；Gopeed 反射探测类 | ✅ |
| 前台服务持有 | `DownloadService` 构造并启停 Coordinator + 两个 Controller | ✅ |
| 重启恢复 | Repository 恢复时清空原生引擎的 `engineTaskId/engineStatus`，由 Controller 重新创建 | ✅ |
| 单测 | **32/32 通过（9 个测试类）**：新增 `Aria2RpcClientTest` 3 个、`GopeedApiTest` 3 个、`TaskJsonCodec` engine/v1 兼容 1 个 | ✅ |
| `assembleDebug` | 通过，已放入两个原生产物；APK 29,358,263 B（28.00 MB），SHA-256 `583b6846…f611bc4` | ✅ |
| `assembleRelease` | 通过（R8 + 演示签名、zipalign -P 16）；APK 18,771,902 B（17.90 MB），SHA-256 `a8b5be56…cbd2d5d` | ✅ |
| APK 内原生产物 | `lib/arm64-v8a/libaria2c.so`（1.35.0，5,872,304 B）+ `lib/arm64-v8a/libgojni.so`（43,908,776 B）均存在；assets/licenses 17 个文件 | ✅ |
| 真机验证 | **部分抽查完成**（Android 16）：三引擎 50MB 下载、引擎切换、aria2 暂停/恢复与锁屏 10 分钟、force-stop 无残留；**Gopeed 锁屏专项/通知降级/5 任务压力未做**，E1–E8 未完整执行 | ⚠️ 部分 |

### 0.1 与 1.0.0 规划稿的主要差异（以实现为准）

| 规划稿写法 | 实际实现 | 原因 |
| --- | --- | --- |
| `engineState` / `engineStagingDir` 字段 | 实际只有 `engineStatus`；任务目录固定为 `staging/engines/<engine>/tasks/<taskId>` | 不需要额外持久化临时目录 |
| Gopeed 使用 `SubscribeTaskEvents` 事件 + 轮询 | **只做 REST 轮询**（约 700 ms/次），未使用 gomobile 事件订阅 | 降低 JNI 回调线程与生命周期复杂度 |
| aria2 `--save-session` + `--input-file` 会话恢复 | **未使用 session 文件**；依赖每个任务目录的 `.aria2` 控制文件 + 重新 `addUri(continue=true)` | 避免 session 文件与任务仓库双写状态不一致 |
| aria2 固定端口 / 配置化端口 | 每次启动用 `ServerSocket(0)` 取随机空闲端口 + 随机 `--rpc-secret` | 避免端口冲突与未授权访问 |
| Gopeed 固定端口或端口 0 | 配置 `address = "127.0.0.1:0"`，由内核返回随机端口 + 随机 `X-Api-Token` | 同上 |
| 指数退避重启内核 | 失败后 10 秒冷却再重试（`lastFailureAt`），非指数退避 | 实现更简单，避免频繁拉起 |
| "并发上限对所有引擎统一生效" | 不是统一槽位：内置引擎由 Coordinator 槽位控制；aria2 用 `--max-concurrent-downloads`；Gopeed 用 `maxRunning`。跨引擎总数不受统一限制 | 各引擎内部调度；文档如实说明 |
| APK 体积 60–120 MB 预估 | **实际最终 APK**：debug 29,358,263 B（28.00 MB）/ release 18,771,902 B（17.90 MB）；aria2 二进制由 v1.1.0 的 69.1 MB 降至 5.87 MB | 以实际构建为准 |
| Gopeed AAR 带事件/InvokeAsync 桥 | 实际 AAR **只导出 `start`/`stop`**，Java 入口类为 `com.gopeed.libgopeed.Libgopeed`；任务/进度全部通过 REST 轮询 | 以上游 `bind/mobile` 实际 API 为准 |

---

## 1. 技术可行性结论（本机探测结果）

| 项 | 结果 | 说明 |
|---|---|---|
| Gopeed AAR 构建 | **已完成，需下载约 0.9 GB 工具链** | 安装 Go 1.24.11；实际 gomobile 由 go1.26.10 工具链（`GOTOOLCHAIN=auto`）+ NDK 27.2.12479018 编译；AAR 15,438,987 B，SHA-256 `aa064e34…dd2d7c` |
| aria2 二进制 | **可行（预编译，已替换为 1.35.0）** | devgianlu/aria2-android `aria2c-arm64-v8a` 1.35.0（5,872,304 B），SHA-256 `88b8b5fe…bd1a7f`；静态链接 OpenSSL 1.1.1d，适用 aria2 OpenSSL 例外。v1.1.0 的 1.37.0/OpenSSL 3.0.0 在 Android 16 启动失败，已弃用 |
| 自行交叉编译 aria2 | **高成本/本机暂不可行** | 本机无 WSL/Docker/MSYS2；NDK 交叉编译 autotools 工程需要 Linux/POSIX 构建环境 |
| 真机验证 | **部分可执行（已抽查）** | Redmi Turbo 4 Pro / Android 16 上已抽查三引擎下载、aria2 锁屏 10 分钟等；完整 E1–E8 仍待补 |
| APK 体积影响 | **已实测** | v1.1.1 最终 APK：debug 29,358,263 B（28.00 MB）/ release 18,771,902 B（17.90 MB） |

### 重要技术事实：aria2 不能从 assets 释放到私有目录后直接执行

Android 10（API 29）起，`targetSdk >= 29` 的应用**不能 `exec()` 应用私有数据目录中的文件**（W^X 限制）。本工程 `targetSdk = 35`，因此"assets → 释放到私有目录 → 子进程启动"的路径在 Android 15 上会失败。

**实际做法**：把 arm64 二进制重命名为 `app/src/main/jniLibs/arm64-v8a/libaria2c.so` 随 APK 打包，由系统解压到 `applicationInfo.nativeLibraryDir`（该目录可执行），再用绝对路径启动；`app/build.gradle.kts` 设置 `packaging.jniLibs.useLegacyPackaging = true` 以确保解压。

---

## 2. 总体架构

```
             Settings（下载引擎：内置 / Gopeed / aria2）
                              │ 仅影响"新建任务"
                              ▼
DownloadRepository（统一任务列表，task.engine 持久化，schema v2）
   │ StateFlow                                ▲ 统一进度/状态
   ▼                                          │
DownloadService（唯一 dataSync 前台服务，持有 WakeLock/通知）
   ├── DownloadCoordinator（仅处理 engine=BUILTIN）
   ├── Aria2EngineController（子进程 + JSON-RPC，处理 engine=ARIA2）
   └── GopeedEngineController（AAR + 内嵌 REST，处理 engine=GOPEED）
                              │
                              ▼
                   DestinationPublisher（MediaStore / SAF）
```

原则：

- **一个前台服务、一个通知聚合、一个 Repository**；三个引擎只是"执行器"，状态回写同一数据源。
- 每个任务创建时固定 `engine`，UI 切换只影响之后新建的任务；旧任务继续由原引擎执行。
- 引擎按需启动/停止：只有存在该引擎的未完成任务时才启动对应内核；任务全部完成/暂停后停止内核，避免常驻资源泄漏。
- 两个原生内核可能同时运行（旧任务 + 新任务使用不同引擎），资源开销相应增加。

---

## 3. 数据模型与持久化变更（已实现）

```kotlin
enum class DownloadEngine { BUILTIN, GOPEED, ARIA2 }

// DownloadTask 新增（持久化，TaskJsonCodec v2）：
val engine: DownloadEngine = DownloadEngine.BUILTIN,
val engineTaskId: String? = null,   // aria2 gid / Gopeed task id
val engineStatus: String? = null,   // 引擎原始状态字符串，便于诊断

// AppSettings 新增（SharedPreferences）：
val downloadEngine: DownloadEngine = DownloadEngine.BUILTIN
```

- JSON schema 从 1 升到 2；`decodeTask()` 对缺失 `engine` 的旧数据默认 `BUILTIN`（单测覆盖）。
- 任务状态机复用现有 `TaskState`；引擎原始状态通过 `engineStatus` 记录，不直接暴露给 UI（详情页仅展示）。
- `DownloadRepository.recoverAfterRestart()` 对原生引擎任务清空 `engineTaskId/engineStatus`，由 Controller 重新 `addUri`/创建任务。
- 删除任务：Controller 检测到任务从 Repository 消失后，删除引擎侧任务（RPC/HTTP）并清理任务目录；Repository 负责 staging 文件的删除。

---

## 4. Gopeed 集成设计（AAR，进程内）

### 4.1 AAR 构建（`native/gopeed/build.ps1`）

```powershell
# 脚本内部固定：Go 1.24.11、NDK 27.2.12479018、Gopeed v1.9.3
# GOPROXY=https://goproxy.cn,https://mirrors.aliyun.com/goproxy,direct
# GOSUMDB=off；产物 app/libs/libgopeed.aar（.gitignore 忽略）
powershell -ExecutionPolicy Bypass -File native/gopeed/build.ps1
```

核心命令（脚本节选）：

```
gomobile bind -tags nosqlite -ldflags="-w -s" -o libgopeed.aar \
  -target=android/arm64 -javapkg=com.gopeed \
  github.com/GopeedLab/gopeed/bind/mobile
```

- 产物 `libgopeed.aar` 放入 `app/libs/`，Gradle 在文件存在时自动 `implementation(files("libs/libgopeed.aar"))`；
- 需保存 Go/NDK/gomobile 版本、`go.sum` 与构建脚本，满足 GPLv3 对应源码要求；
- AAR 内包含 Go runtime 与 Gopeed 核心（GPLv3）；实际产物 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`，内含 `jni/arm64-v8a/libgojni.so` 43,908,776 B；
- 构建环境细节：安装 Go 1.24.11，但 `golang.org/x/mobile@latest` 要求 Go ≥ 1.26，gomobile 实际使用 go1.26.10 工具链完成编译（x/mobile `v0.0.0-20261011034528-197770444867`），NDK 27.2.12479018；详见 `.tools/gopeed-build/BUILD_REPORT.md`（不提交仓库）。

### 4.2 AAR 对外 API 与实际调用

gomobile 生成的 Java 入口类为 **`com.gopeed.libgopeed.Libgopeed`**（`EngineAvailability.GopeedBridge` 的反射候选列表包含它）；AAR **只导出 `start`/`stop` 两个方法**，没有 `InvokeAsync`/`SubscribeTaskEvents`/`GetAPIServerState` 等桥。实际实现（`EngineAvailability.GopeedBridge`）：

- `start(configJson): Int`：反射查找 `start(String)` 静态方法，返回监听端口；异常包装为 `IllegalStateException`；
- `stop()`：反射调用无参 `stop()`；失败静默（服务回收时不应阻塞）；
- 完全反射的好处：源码仓库即使没有 AAR 也能编译，只是运行时显示"不可用"。

### 4.3 生命周期与前台服务配合（实际实现）

1. `DownloadService.onCreate` 构造 `GopeedEngineController`；`onStartCommand` 中 `start()`，`onDestroy` 中 `stop()`。
2. Controller 维护 700 ms 轮询循环；当存在 `engine=GOPEED` 且未完成、`desiredRunning=true` 的任务时启动服务，否则 `stopServer()`。
3. `ensureServer()`：生成随机 token；`StartConfig` JSON：
   ```json
   {"network":"tcp","address":"127.0.0.1:0","refreshInterval":350,
    "storage":"mem","storageDir":".../staging/engines/gopeed/storage",
    "apiToken":"<uuid>",
    "downloadConfig":{"downloadDir":".../staging/engines/gopeed","maxRunning":<设置并发>}}
   ```
4. 启动后经 `GET /api/v1/info` 轮询等待就绪（30 × 200 ms 上限）；失败则标记相关任务失败并 10 秒冷却。
5. **任务创建**：`POST /api/v1/tasks`，`req.url` 为下载链接，`opts.name` 为文件名、`opts.path` 为任务目录、`opts.extra.connections` 为连接数；返回 task id 写入 `engineTaskId`。
6. **进度/状态**：每轮 `GET /api/v1/tasks` 拉全量列表，按 `engineTaskId` 匹配；`running` → 下载中、`ready/wait` → 连接中、`pause` → 暂停、`error` → 失败、`done` → 发布。
7. **暂停/恢复/删除**：`PUT /tasks/{id}/pause`、`PUT /tasks/{id}/continue`、`DELETE /tasks/{id}?force=true`。
8. **完成发布**：`done` → 解析任务目录中的实际文件 → `DestinationPublisher.publish()` → `markCompleted()` → 删除任务目录与引擎侧任务。
9. **停止**：没有需要运行的任务（全部完成或已暂停）时调用 `GopeedBridge.stop()` 并清空 `engineTaskId`，避免 Go 线程/端口泄漏；服务停止时同样处理。注意：暂停后恢复会重建任务，其续传行为依赖 Gopeed 内核对已有文件的处理（待 E2 验证）。
10. **崩溃恢复**：AAR 与 App 同进程，Go panic/OOM 会带崩 App；下次启动时 Repository 已清空 `engineTaskId`，按原 URL 重新创建任务（Gopeed 自身处理已有文件/续传）。

### 4.4 已知限制

- **Gopeed REST API 当前不提供全局限速**，设置页的限速对 Gopeed 任务不生效（KI-23）。
- **AAR 与 App 同进程**：Go panic/OOM 可能直接导致 App 崩溃（KI-24）；只能通过限制并发/版本固定/异常保护降低风险，无法完全隔离。
- Gopeed REST API 随版本变化，需锁定 v1.9.3 并回归测试。

---

## 5. aria2 集成设计（子进程 + JSON-RPC）

### 5.1 二进制获取/打包

- 预编译：`aria2c-arm64-v8a`（devgianlu/aria2-android **v1.35.0**，5,872,304 B，GPLv2+；静态链接 **OpenSSL 1.1.1d**（OpenSSL+SSLeay，适用 aria2 OpenSSL 例外）、zlib 1.2.9、c-ares 1.14.0、libexpat 2.2.9、libssh2 1.9.0_DEV、libc++/clang 8.0.7；精确 commit 见 `licenses/DEPENDENCY-VERSIONS.md`）。v1.1.0 的 1.37.0/OpenSSL 3.0.0 在 Android 16 因缺 legacy provider 启动失败，已弃用。
- 放置：`app/src/main/jniLibs/arm64-v8a/libaria2c.so`（**不是 assets**，原因见第 1 节 W^X 限制）；Gradle 打包后系统解压到 `nativeLibraryDir`。
- 获取：`native/aria2/fetch.ps1`（经 `gh-proxy.com`，校验大小与 SHA-256，可用 `-ExpectedSha256` 固定哈希）。
- 正式发布前建议自行按 devgianlu 构建脚本重建并保存 SBOM/源码，降低供应链风险。

### 5.2 进程启动（实际参数）

`Aria2EngineController.ensureProcess()` 以 `ProcessBuilder` 启动（IO 线程内）：

```
libaria2c.so
  --enable-rpc=true --rpc-listen-all=false --rpc-listen-port=<随机空闲端口>
  --rpc-secret=<随机 UUID> --dir=<staging/engines/aria2 根目录>
  --continue=true --auto-file-renaming=false --allow-overwrite=true
  --file-allocation=none
  --max-concurrent-downloads=<设置并发>
  --max-overall-download-limit=<限速字节数，0=不限>
  --console-log-level=warn --log-level=warn --log=<日志文件>
  --summary-interval=0 --enable-color=false --no-conf=true
```

- 启动后轮询 `aria2.getVersion` 等待就绪（40 × 250 ms 上限）；失败则标记任务失败并 10 秒冷却。
- **JSON-RPC 鉴权参数必须为 `token:<secret>`**（只传 secret 会被 HTTP 400 拒绝）；`Aria2RpcClient` 已按此实现并有单测覆盖。
- **不使用 `--stop-with-process`**：Android 16 真机验证该选项会导致 aria2 提前以 code 7 退出；改由前台服务停止时 `shutdown` + `waitFor` 回收（真机 `am force-stop` 后无残留 aria2 进程）。
- 进程异常退出：清空内存 gid 与任务的 `engineTaskId`，下一轮按状态重建；同一时间只允许一个 aria2c。
- 服务停止/任务结束：`aria2.saveSession()`（尽力）、`aria2.shutdown`/`forceShutdown`，`waitFor` 3 秒，超时强制杀死。

### 5.3 JSON-RPC 任务映射（实际调用）

| RPC | 用途 |
|---|---|
| `aria2.addUri(uris, options)` | 新建下载；返回 `gid` 存入 `engineTaskId`；options：`dir`、`out`、`continue=true`、`auto-file-renaming=false`、`allow-overwrite=true`、`max-connection-per-server`、`split`、`min-split-size=1M`、`user-agent` 等 |
| `aria2.tellStatus(gid)` | 轮询进度/状态/速度/总长度；映射到 TaskState |
| `aria2.pause(gid)` / `aria2.unpause(gid)` | 暂停/恢复 |
| `aria2.remove(gid)` / `aria2.removeDownloadResult(gid)` | 删除/清理任务 |
| `aria2.changeGlobalOption({"max-overall-download-limit":"..."})` | 全局限速（设置页实时生效） |
| `aria2.changeGlobalOption({"max-concurrent-downloads":"..."})` | 并发上限（设置页实时生效） |
| `aria2.saveSession()` / `aria2.shutdown()` / `aria2.forceShutdown()` | 停止前保存/关闭 |

状态映射：`active/waiting` → 下载中/连接中；`paused` → 用户暂停或自动 unpause；`error` → FAILED（记录 `errorMessage`）；`complete` → SAVING → 发布后 COMPLETED；`removed` → 清空 gid 并重新排队。

### 5.4 断点续传与重启恢复（实际实现）

- 每个任务有独立目录 `staging/engines/aria2/tasks/<taskId>`，`addUri` 指定 `dir` 与 `out`；aria2 在该目录生成 `.aria2` 控制文件。
- **进程停止时清空 gid**；恢复时以原 URL 重新 `addUri` 且 `continue=true`，aria2 读取 `.aria2` 控制文件从已有字节继续。
- 不使用 session 文件（`--save-session`/`--input-file`），避免 session 状态与 Repository 双写不一致。
- 完成任务后把文件发布到 MediaStore/SAF（与内置引擎一致），确保"文件管理器可见、无需存储权限"的产品承诺不因引擎切换而改变。

### 5.5 生命周期与资源管理

- 只有存在 ARIA2 任务时启动；所有 ARIA2 任务完成/暂停/删除后停止进程并释放端口/日志文件句柄。
- 前台服务仍由 `DownloadService` 统一维持 `dataSync` 通知与 WakeLock；aria2 自身不能保持前台。
- 仅 Wi-Fi 策略：Controller 在非 Wi-Fi 时 `pause(gid)` 并置 `WAITING_FOR_WIFI`，网络恢复后重新排队/unpause。
- 日志：`staging/engines/aria2/aria2.log`（追加），用于真机诊断；注意日志可能增长，发布前应确认不会被无限写入。

---

## 6. 引擎切换逻辑与统一 UI（已实现）

1. 设置页「下载引擎（仅影响新建任务）」单选：内置（默认）/ Gopeed / aria2；每个选项显示**可用状态**（AAR 是否打包、aria2c 是否存在），不可用时禁用并提示原因。
2. 新建任务：`repository.createTask(..., settings)` 内部写入 `engine = settings.downloadEngine`；若目标引擎不可用，`MainViewModel` 在校验阶段拒绝创建并提示（禁止静默回退到内置引擎）。
3. 已有任务：`task.engine` 持久化，不因设置变化而改变；任务卡片对非内置引擎显示 `· Gopeed` / `· aria2`；详情页显示引擎、`engineTaskId`、`engineStatus`。
4. 统一列表：Repository 是唯一数据源；`DownloadCoordinator` 只筛选 `engine=BUILTIN`；两个 EngineController 处理各自任务；通知聚合由服务统一完成。
5. 切换引擎时**不重启/不影响进行中的任务**；两个引擎的任务可以并存；设置页提示原生内核会显著增大 APK 体积与资源占用。
6. UI 入口：设置页引擎区块、任务卡片引擎标识、详情页引擎信息、关于页「开源许可 / 源码获取」对话框。

---

## 7. 构建与打包（已落地）

| 步骤 | 命令/产物 | 说明 |
|---|---|---|
| 1. 构建 Gopeed AAR | `native/gopeed/build.ps1` → `app/libs/libgopeed.aar` | 固定 Go 1.24.11 / NDK 27.2.12479018 / Gopeed v1.9.3；需下载工具链 |
| 2. 获取 aria2 二进制 | `native/aria2/fetch.ps1` → `app/src/main/jniLibs/arm64-v8a/libaria2c.so` | 经 gh-proxy 下载，校验大小/SHA-256 |
| 3. Gradle 配置 | `implementation(files("libs/libgopeed.aar"))`（存在时）；`jniLibs`；`useLegacyPackaging = true`；`abiFilters ["arm64-v8a"]` | 已在 `app/build.gradle.kts` |
| 4. 构建 APK | `.\gradlew.bat :app:assembleDebug` / `:app:assembleRelease` | **已完成**：debug 29,358,263 B / release 18,771,902 B，均含两个内核与 17 个许可证文件；zipalign -P 16 通过 |
| 5. 许可证资产 | `app/src/main/assets/licenses/`、`licenses/`、`native/licenses/` | 共 17 个文件：GPLv3/GPLv2/aria2 COPYING/Gopeed/LICENSE.OpenSSL 例外/**OpenSSL-1.1.1d-LICENSE**/Apache-2.0/各静态依赖/DEPENDENCY-VERSIONS/SOURCE-OFFER/THIRD_PARTY_NOTICES/README/devgianlu notice |
| 6. 发布 | GitHub Release 附 APK + 源码归档 + 构建脚本 + 许可证 + SHA-256 | GPLv3 对应源码与书面要约，见 COMPLIANCE |

**体积（实测）**：v1.1.1 最终 APK debug **29,358,263 B（28.00 MB，SHA-256 `583b6846…f611bc4`）**、release **18,771,902 B（17.90 MB，SHA-256 `a8b5be56…cbd2d5d`）**；release 经 R8 小于 debug，且因 aria2 二进制由 69.1 MB 降至 5.87 MB 而显著小于 v1.1.0。AAR 15,438,987 B。

---

## 8. 测试计划（新增部分）

### 8.1 JVM 可验证（无需真机）

- 引擎路由：新建任务使用当前设置引擎；切换设置后旧任务 engine 不变；JSON v1→v2 迁移默认 BUILTIN。**已覆盖**：`TaskJsonCodecTest` 新增 `old v1 tasks without engine decode as builtin engine`。
- aria2 JSON-RPC 客户端：**已覆盖** `Aria2RpcClientTest`（MockWebServer）：**`token:<secret>`**/URL/options、`tellStatus` 解析与错误映射、`changeGlobalOption` 仅 OK 生效。
- Gopeed REST 客户端：**已覆盖** `GopeedApiTest`（MockWebServer）：`createTask` 的 token/请求体、任务列表与 pause PUT、非零 envelope 抛错。
- Gopeed 配置 JSON 构造、任务映射、aria2 进程参数构造：仍未单独单测，代码已通过编译与构建验证。
- 许可证资产存在性：构建后已人工检查 APK `assets/licenses/`（17 个文件），未做自动化测试。

> 诚实说明：32 个 JVM 用例中，1.1.0 引入的覆盖集中在客户端协议层（两个测试类各 3 个）与 JSON v1 兼容；`Aria2EngineController`/`GopeedEngineController` 的进程、AAR、发布与回收逻辑**仍无 JVM 单测覆盖**（依赖 Android Context / 子进程 / AAR），依赖真机抽查/后续验收补充。

### 8.2 真机抽查（2026-11-05，非完整 E1–E8）

| 抽查项 | 结果 |
|---|---|
| 内置引擎 50MB HTTP 下载、发布、暂停/恢复 | ✅ 成功（发布到 `/sdcard/Download/MotrixLite/testfile.bin`；前台服务 dataSync + 通知正常、空闲停止） |
| Gopeed 加载/REST/50MB 下载/发布/停服 | ✅ 成功（`Gopeed server started on port …`） |
| aria2 子进程/JSON-RPC 鉴权/50MB 下载/发布/暂停恢复/进程回收 | ✅ 成功（文件发布为 `testfile (N).bin`；完成后进程消失） |
| 引擎切换 | ✅ 新任务用所选引擎并显示 badge；旧任务保留原引擎 |
| aria2 锁屏 10 分钟 | ✅ 23:14:56 锁屏→23:26 检查：日志 448 KB→20 MiB/50 MiB，`PARTIAL_WAKE_LOCK` 持续，进程存活；`am force-stop` 后无残留 |
| v1.1.0 Android 16 aria2 失败复现 | ✅ 已复现并确认由 v1.1.1 修复 |
| Gopeed 锁屏 10 分钟专项 | ❌ 未执行（设备锁屏后无法继续 UI 操作） |
| 通知权限拒绝降级 / 5 任务并发压力 / 完整 E1–E8 | ❌ 未执行 |

### 8.3 真机验收（E 系列，完整项仍待执行）

| 编号 | 用例 | 预期 |
|---|---|---|
| E1 | 引擎选择器可用性与不可用提示 | 未打包/缺失内核时禁用选择并说明原因；新建任务被拒绝且不崩溃 |
| E2 | Gopeed 引擎 HTTP 端点断点续传 + 锁屏 10 分钟 | 进度持续/续传正确，文件发布到 Download/MotrixLite |
| E3 | aria2 引擎 HTTP 端点断点续传 + 锁屏 10 分钟 | 同上；重启后 `.aria2` 续传正常 |
| E4 | 切换引擎后新建任务 | 新任务 engine=新引擎；旧任务 engine/状态/进度不变 |
| E5 | 两引擎任务同时运行（旧 aria2 + 新 Gopeed） | 通知聚合正确、服务不崩、进程/线程可回收 |
| E6 | 服务停止/App 杀死后重启 | 无孤儿 aria2c 进程；Gopeed 端口/goroutine 释放；任务按引擎恢复 |
| E7 | AAR/二进制加载失败 | UI 显示原因、不闪退 |
| E8 | 许可证页面/源码 offer 检查 | 含 GPLv3/GPLv2 文本、源码 URL、版本/tag、书面要约 |

---

## 9. 风险与限制

1. **许可证**：双内核同 APK 必须 GPLv3 开源；不提供闭源方案（详见许可报告）。
2. **体积**：aria2 二进制 5.87 MB + Gopeed AAR 15.4 MB，最终 APK debug 28.00 MB / release 17.90 MB；仍显著大于纯内置版本，低端机安装/更新成本存在。
3. **稳定性**：Gopeed 与 App 同进程，Go panic/OOM 可直接崩溃；aria2 子进程崩溃需要重启与状态重建。
4. **执行限制**：aria2 只能从 `nativeLibraryDir` 执行，不能从 assets 释放目录执行（Android 10+ W^X）。
5. **Gopeed 限速缺失**：其 REST API 不提供全局限速。
6. **跨引擎并发不统一**：三个引擎各自按设置参数调度，没有全局统一槽位。
7. **兼容性**：v1.1.0 的 aria2 1.37.0/OpenSSL 3.0.0 在 Android 16 启动失败；v1.1.1 改用 1.35.0/OpenSSL 1.1.1d 已修复，但升级 aria2/OpenSSL 必须做 Android 16 回归。
8. **API 变化**：Gopeed REST / aria2 RPC 版本升级需回归。
9. **真机范围有限**：已完成部分抽查；Gopeed 锁屏专项、通知降级、5 任务压力与完整 E1–E8 尚未验证。
10. **供应链**：第三方预编译二进制与 Go 依赖需生成 SBOM、校验哈希、发布对应源码；本次 aria2 SHA-256 `88b8b5fe…bd1a7f`、AAR SHA-256 `aa064e34…dd2d7c` 已记录。**静态依赖版本较旧**：OpenSSL 1.1.1d（2019-09）与 aria2 1.35.0（2019-10）均已停维护、存在 CVE；libssh2 1.9.0_DEV 为开发快照，建议重建或披露（KI-30）。
11. **商店政策**：Google Play 对 GPL 应用/打包原生可执行文件有额外审查；侧载风险较低但不为零。

---

## 10. 实施顺序（已完成）

1. ✅ 许可证决策：整体 GPLv3 开源；
2. ✅ 工具链与内核产物脚本（AAR 构建、aria2 获取/校验）；
3. ✅ 数据层 + 引擎路由 + 设置页 UI + 旧任务兼容；
4. ✅ aria2 控制器 + RPC + 进程生命周期 + 发布；
5. ✅ Gopeed 控制器 + AAR + REST + 发布；
6. ✅ 通知/保活/限速/并发/迁移统一联调（静态与构建级验证）；
7. ✅ 许可证资产、关于页、源码要约；
8. ❌ 真机测试清单回填与问题修复（本环境无法执行，待有设备后进行）。

---

## 11. 结论

- **已实施**：三引擎（内置 / Gopeed / aria2）在同一 APK 中按任务引擎路由，统一任务列表、前台服务与保存发布流程；无原生产物时可编译运行、引擎显示不可用。
- **已构建**：`:app:assembleDebug` 与 `:app:assembleRelease` 均成功，APK 已含 `libaria2c.so`（1.35.0）/ `libgojni.so` 与 17 个许可证文件；debug 29,358,263 B / release 18,771,902 B，SHA-256 见 README 6.5 与 BUILD_AND_SIGN 3.4。
- **合规**：整体 GPLv3，Gopeed GPL-3.0 与 aria2 1.35.0 GPL-2.0-or-later（OpenSSL 1.1.1d 适用链接例外）的对应源码、构建脚本、许可证文本与书面要约已落实。
- **测试**：32/32 JVM 单测通过（9 个测试类，含两个原生客户端 MockWebServer 测试）；两个 Controller 的生命周期逻辑仍无单测。
- **已抽查真机**：Android 16（Redmi Turbo 4 Pro）三引擎 50MB 下载、引擎切换、aria2 暂停/恢复与锁屏 10 分钟、force-stop 无残留等（详见 §8.2）。
- **未验证**：Gopeed 锁屏 10 分钟专项、通知权限拒绝降级、5 任务并发压力、完整 E1–E8/A1–A7/B1–B8。
