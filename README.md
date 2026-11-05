# Motrix Lite

**一款用 Kotlin + Jetpack Compose 编写的 Android 下载管理器**：内置自研 OkHttp 分段内核，并可选集成 **Gopeed** 与 **aria2** 两个原生下载内核；统一任务列表、统一前台服务、统一保存发布流程。

> 参考 Motrix 桌面应用的产品思路与交互组织方式，**独立实现**；不是 Electron 封装，也不是 Motrix 桌面版的 Android 移植。
>
> 本版本（1.1.0）同时包含 GPL 组件，**整体按 GNU GPLv3 开源分发**。详见[重要声明](#重要声明)与[许可证与源码获取](#十二许可证与源码获取)。

| 项 | 值 |
| --- | --- |
| 应用名 / 包名 | Motrix Lite / `com.motrixlite.downloader`（包名不变） |
| 版本 | versionCode **2**，versionName **1.1.0** |
| 最低系统 | Android 8.0（API 26） |
| 编译 / 目标 SDK | compileSdk 35 / targetSdk 35（Android 15） |
| ABI | **仅 arm64-v8a**（`abiFilters`） |
| 技术栈 | Kotlin 2.0.21 · Jetpack Compose（BOM 2024.12.01 / Material3）· OkHttp 4.12.0 · Coroutines 1.9.0 |
| 构建工具 | AGP 8.7.3 · Gradle 8.9 · JDK 17 |
| 下载引擎 | 内置引擎（自研 OkHttp 分段）· Gopeed（gomobile AAR）· aria2（子进程 + JSON-RPC） |
| 许可证 | **GNU GPLv3**（见 [LICENSE](LICENSE)）；Gopeed 为 GPL-3.0；aria2 为 GPL-2.0-or-later，其预编译二进制静态链接 **OpenSSL 3.0.0（Apache-2.0）** |
| 支持协议 | **HTTP / HTTPS 直链**（磁力、BT 种子在当前任务创建流程中仍不支持） |
| 真机验收 | **未执行**（本环境无 Android 设备/模拟器），全部真机项见 [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md)，状态为"待验证" |

---

## 重要声明

请先阅读这一节，避免误解项目性质：

1. **许可证路线已确定：整体 GPLv3。** APK 中打包了 Gopeed（GPL-3.0，gomobile AAR，与 App 同进程链接）与 aria2（GPL-2.0-or-later，作为子进程执行；其二进制静态链接 OpenSSL 3.0.0 / Apache-2.0，历史 `LICENSE.OpenSSL` 例外不覆盖该版本，整包按 GPLv3 分发、GPLv3 与 Apache-2.0 兼容）。Gopeed AAR 与 App 构成**结合作品（combined work）**，因此包含两者的同一 APK **必须整体按 GPLv3 分发**，App 自身源码也必须按 GPLv3 提供。
   **明确否定：不存在"把 Gopeed AAR 和 aria2 放进同一 APK 但闭源分发"的合规方案。** 通过独立进程、RPC、加壳或"动态加载"来规避 GPL 的说法不可靠；通过 GPLv2 的"独立程序/聚合"争议也不能把 AAR 链接的 Gopeed 变成独立程序。完整分析见 [docs/ENGINE_LICENSE_ANALYSIS.md](docs/ENGINE_LICENSE_ANALYSIS.md)。
2. **不是桌面版封装，也不是 Motrix 官方产品。** 本工程是独立的 Android 原生项目，未使用 Electron、Node.js 或 Motrix 的 Web UI；不包含 Motrix 的源代码、界面资源、图标或品牌素材。Motrix 桌面版（MIT）仅作为产品思路参考。与 Motrix 作者无隶属、赞助或背书关系。
3. **三个下载引擎，任务创建时绑定。** 设置页的「下载引擎」**只影响之后新建的任务**；已有任务保留创建时固定的 `task.engine`，不会被切换设置改变。三个引擎的任务在同一个列表展示，任务卡片/详情页会显示所属引擎。
4. **两个原生内核是可选组件。** `app/libs/libgopeed.aar` 与 `app/src/main/jniLibs/arm64-v8a/libaria2c.so` 不提交到仓库（被 `.gitignore` 忽略），需运行 `native/` 下的脚本生成/获取。**未放入原生产物时工程仍可编译运行**，设置页会把 Gopeed/aria2 显示为"当前 APK 不可用"，新建任务会被拒绝并提示，不会崩溃（本仓库 v1.1.0 的最终构建**已放入**两个产物，见 [6.5 构建产物](#65-构建产物与体积)）。
5. **未做真机验收。** 当前环境没有 Android 设备或模拟器，验证范围仅限 JVM 单元测试（**32/32，9 个测试类**）与 `:app:assembleDebug` / `:app:assembleRelease` 构建（release 经 R8 + 演示签名，APK 已含两个原生内核）。红米 Turbo 4 Pro、锁屏 10 分钟、两个原生引擎的实际下载等**全部待真机验证**；本文档不声称已通过真机验收。
6. **仅支持 HTTP/HTTPS 直链。** 尽管 Gopeed 内核本身具备多协议能力，当前任务创建流程只接受 `http://`/`https://` 直链；磁力链接、BT 种子、ed2k 等仍不支持（见 [已知问题](docs/KNOWN_ISSUES.md)）。

---

## 一、技术方案

### 1.1 三引擎架构

| 维度 | 内置引擎 | Gopeed | aria2 |
| --- | --- | --- | --- |
| 实现形态 | 自研 Kotlin/OkHttp 分段下载器 | gomobile 生成的 AAR（Go 内核，**与 App 同进程**） | arm64 预编译二进制，**独立子进程** + JSON-RPC |
| 集成代码 | `engine/TaskDownloader.kt` 等 | `engine/native/GopeedEngineController.kt`、`GopeedApi.kt`、`EngineAvailability.kt` | `engine/native/Aria2EngineController.kt`、`Aria2RpcClient.kt`、`EngineAvailability.kt` |
| 全局限速 | ✅ 令牌桶，实时生效 | ❌ 当前 Gopeed REST API 不提供全局限速，设置对它不生效 | ✅ 通过 `changeGlobalOption` 下发 |
| 断点续传 | ✅ staging + ETag/Last-Modified/长度校验 | ⚠️ 由内核管理；暂停后内嵌服务停止、恢复时重建任务，续传行为待真机验证 | ✅ `.aria2` 控制文件 + `continue=true` |
| 仅 Wi-Fi | ✅ | ✅（Controller 暂停/恢复） | ✅（Controller 暂停/恢复） |
| 多连接 | ✅ 每任务 1–16 连接 | ✅ 通过任务 `extra.connections` 下发 | ✅ `split` / `max-connection-per-server` |
| 进程/资源风险 | 与 App 同进程，但无 Go 运行时 | Go panic/OOM 可能导致 **App 崩溃** | 子进程崩溃可控，但需重建 gid 与任务映射 |
| 许可证 | 项目自身代码（GPLv3） | GPL-3.0 | GPL-2.0-or-later；静态链接 OpenSSL 3.0.0（Apache-2.0） |
| APK 体积影响 | 无额外 | AAR 15,438,987 B（内含 `libgojni.so` 43,908,776 B） | 二进制 69,107,592 B，压缩后仍显著 |
| 可用性探测 | 恒可用 | 反射探测 AAR 类是否存在（实际类 `com.gopeed.libgopeed.Libgopeed`） | 检查 `nativeLibraryDir/libaria2c.so` 是否存在且大小合理 |
| 最终 APK | 三个引擎共用同一 APK：debug 67,482,342 B（64.36 MB）/ release 38,559,243 B（36.77 MB），详见 [6.5](#65-构建产物与体积) | — | — |

**可用性处理**：设置页对不可用引擎显示"当前 APK 不可用"并禁用选择；新建任务时若当前设置引擎不可用，会拒绝创建并提示切换引擎，**不会静默回退**到内置引擎。

### 1.2 许可证分析结论（摘要）

- **Gopeed v1.9.3 = GPL-3.0**。gomobile AAR 把 Go 代码编译为共享库，通过 JNI 与 Kotlin 代码在同一进程内直接调用，构成结合作品 → **App 整体必须是 GPLv3**。
- **aria2 1.37.0 = GPL-2.0-or-later**。以独立子进程方式调用、随 APK 分发，仍须履行 GPLv2 的对应源码/许可证/书面要约义务；GPL-2.0-or-later 与 GPLv3 兼容，因此可与 Gopeed 一起放入同一 GPLv3 发布物。**注意**：其预编译二进制实际静态链接的是 **OpenSSL 3.0.0（Apache-2.0）**，不是 OpenSSL 1.1.1；aria2 仓库的 `LICENSE.OpenSSL` 是历史例外文本，不覆盖该组合。整包按 GPLv3 分发（GPLv3 与 Apache-2.0 兼容），并随附 Apache-2.0 全文与 OpenSSL 版权声明。
- **Motrix 桌面版 = MIT**，仅参考思路，未复用代码/资源，不产生额外义务。
- **AndroidX / Compose / OkHttp / Kotlin / Coroutines = Apache-2.0**；**Go runtime = BSD-3-Clause**；OpenSSL 及 aria2 静态链接的其它依赖按其各自许可证声明（见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)）。
- 本项目整体按 **GPLv3** 发布，对应源码、构建脚本与书面要约见[第十二节](#十二许可证与源码获取)。

完整论证、分发场景矩阵与合规清单见 [docs/ENGINE_LICENSE_ANALYSIS.md](docs/ENGINE_LICENSE_ANALYSIS.md) 与 [docs/COMPLIANCE.md](docs/COMPLIANCE.md)。

### 1.3 为什么仍然只支持 HTTP/HTTPS 直链

三个引擎中，内置引擎只实现了 HTTP Range 分段；Gopeed/aria2 虽然自身具备多协议能力，但本项目当前**只调用它们的 HTTP 下载接口**，未实现磁力/BT 的任务创建、tracker/DHT、端口映射与做种管理，也未在 UI 暴露相关入口。因此：

- `magnet:`、`.torrent`、ed2k、FTP 等仍然**不支持**；
- 需要登录/Cookie/Referer/防盗链签名的链接仍然**不支持**；
- 把 Gopeed/aria2 接入 BT 是后续可扩展方向，不属于 1.1.0 范围。

### 1.4 系统架构

```
Compose UI（MainActivity / MainViewModel）
        │ StateFlow                    │ startForegroundService
        ▼                              ▼
DownloadRepository（内存唯一数据源）  DownloadService（dataSync 前台服务）
        │ tasks.json v2                 │ 通知聚合 / WakeLock / 时限处理
        ▼                              ▼
     TaskStore             ┌───────────┴────────────┬──────────────────────┐
                           ▼                        ▼                      ▼
                    DownloadCoordinator      Aria2EngineController   GopeedEngineController
                    （仅 BUILTIN 任务）       （子进程 + JSON-RPC）    （AAR + 内嵌 REST）
                           │                        │                      │
                           ▼                        ▼                      ▼
                    TaskDownloader            aria2c 子进程           Gopeed Go 内核
                    HttpProbe / SegmentPlanner      │                      │
                    RateLimiter / SpeedMeter        └──────────┬───────────┘
                                                               ▼
                                              DestinationPublisher（MediaStore / SAF）
```

设计原则：

- **一个前台服务、一个通知聚合、一个 Repository**。三个引擎只是"执行器"，状态/进度全部回写同一数据源；UI 不直接与引擎交互。
- **任务创建时固定引擎**：`task.engine` 持久化，切换设置只影响新任务；旧任务继续由原引擎执行。
- **按需启动**：只有存在该引擎的未完成任务时才启动对应内核；任务全部完成/暂停后关闭内核，避免常驻资源泄漏。
- **保存路径统一**：三个引擎下载完成后都先落到 staging，再由同一个 `DestinationPublisher` 发布到 MediaStore/SAF，文件管理器中可见、无需存储权限。

详细模块职责、状态机、并发模型见 **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**。

---

## 二、风险与限制

| 风险 | 影响 | 缓解措施 | 状态 |
| --- | --- | --- | --- |
| Gopeed 与 App 同进程 | Go panic/OOM 可能直接导致 App 崩溃 | 限制单任务并发、AAR 版本固定、启动/停止加异常保护；只能降低概率，无法完全隔离 | 已知限制（KI-24），待真机验证 |
| Gopeed 不支持全局限速 | 设置页的"全局限速"对 Gopeed 任务不生效 | 设置页与提示中明确说明；需要限速时改用内置引擎或 aria2 | 已知限制（KI-23） |
| 两个原生引擎同时运行 | 内存、CPU、文件句柄与耗电增加 | 只在有对应任务时启动；任务结束/暂停后关闭内核；设置页提示资源开销 | 已实现，待真机验证 |
| APK 体积显著增大 | 安装/更新成本高，低端机可能空间不足 | 仅打包 arm64-v8a；最终 v1.1.0 APK：debug 67,482,342 B（64.36 MB）、release 38,559,243 B（36.77 MB） | 已知限制（KI-25） |
| 第三方预编译二进制供应链 | 二进制可能包含未披露补丁/后门 | `fetch.ps1` 固定版本、大小与 SHA-256；aria2 二进制经 24 路 Range 分片下载并与独立下载哈希核对一致；AAR 记录 SHA-256；建议自行重建并记录 SBOM | 已缓解（KI-26） |
| 静态依赖版本较旧 | OpenSSL 3.0.0（2021-09）后续有 CVE 修复；libssh2 为 1.9.0_DEV 开发快照 | 版本/commit 已记录在 `licenses/DEPENDENCY-VERSIONS.md`；建议用更新版本重建 aria2 或在 Release 披露供应链时点 | 已知限制（KI-30） |
| Android 10+ 禁止从私有目录执行 | aria2 不能"assets → 释放 → exec" | 以 `jniLibs/arm64-v8a/libaria2c.so` 打包，系统解压到 `nativeLibraryDir`；`useLegacyPackaging=true` 确保解压 | 已实现 |
| 原生引擎不可用/加载失败 | 设置页不可选、新建任务被拒绝 | 可用性探测 + 提示 + 拒绝创建，不崩溃 | 已实现，待真机验证 |
| 服务器不支持 Range | 无法多段加速、暂停后可能重下 | 内置引擎探测后降级单连接；aria2 使用 `continue=true` 依赖服务器支持 | 已实现 |
| 服务器文件在下载期间变化 | 拼出损坏文件 | 内置引擎做 ETag/Last-Modified/长度四重校验；aria2/Gopeed 依赖各自内核校验 | 内置已实现；原生待验证 |
| 发布阶段磁盘翻倍 | 大文件保存失败 | 保留 staging 可重试；文档要求预留约 2.5 倍空间 | 已知限制（KI-04） |
| Android 15 前台服务时限 | 6 小时后被系统暂停 | 重写 `onTimeout`，优雅暂停 + 提示 | 已实现，无法在本环境验证 |
| HyperOS 后台清理 | 息屏后下载中断 | 应用内引导（电池优化/自启动/多任务锁定/通知） | 已实现引导，效果待真机验证 |
| 许可证合规 | 缺少对应源码/许可文本/要约将违反 GPL | 仓库与 APK assets 附完整许可证；固定构建脚本与版本；书面要约 | 已按 GPLv3 落实，详见 COMPLIANCE |

---

## 三、功能矩阵

### 3.1 通用能力

| 功能 | 状态 | 说明 |
| --- | --- | --- |
| HTTP/HTTPS 直链下载 | ✅ | 三个引擎均可用 |
| 下载引擎选择 | ✅ | 设置页「下载引擎」，仅影响新建任务 |
| 统一任务列表 | ✅ | 三个引擎任务混排；卡片/详情显示引擎 |
| 引擎不可用提示 | ✅ | 设置页禁用；新建任务被拒绝并提示 |
| 暂停 / 恢复 | ✅ | 通过 Repository 状态驱动各引擎 |
| 仅 Wi-Fi 下载 | ✅ | 三个引擎均支持（原生引擎由 Controller 暂停/恢复） |
| 全局限速 | ⚠️ | 内置引擎与 aria2 支持；**Gopeed 不支持** |
| 多任务并发 | ✅ | 1–5，默认 3；原生引擎的任务创建/调度由各自 Controller 驱动 |
| 前台服务常驻 | ✅ | 单一 `dataSync` 前台服务，常驻通知 + WakeLock |
| Android 14/15 前台服务适配 | ✅ | 声明并传入 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`；重写 `onTimeout` |
| 任务 JSON 持久化与重启恢复 | ✅ | schema v2（新增 `engine`/`engineTaskId`/`engineStatus`），旧 v1 数据默认 `BUILTIN` |
| 保存到系统下载目录 | ✅ | Android 10+：`MediaStore.Downloads` → `Download/MotrixLite`，无需存储权限 |
| 自定义保存目录（SAF） | ✅ | `OpenDocumentTree` + 持久化 URI 授权 |
| Android 8/9 保存回退 | ✅ | 应用专属外部目录 `.../files/Download/MotrixLite`（卸载即删） |
| 深色模式 | ✅ | 跟随系统 / 浅色 / 深色 |
| 完成/失败通知 | ✅ | 独立结果渠道，每任务一条 |
| 任务筛选 / 清理 | ✅ | 全部 / 未完成 / 已完成 / 失败；可清理已完成任务（保留文件） |
| 速度曲线 / 剩余时间 | ✅ | 内置引擎 30 点滑动历史；原生引擎轮询速度 |
| 打开文件 / 删除文件 | ✅ | `FileProvider` 转换 `file://`；删除任务时可选同时删文件 |
| 磁力 / BT | ❌ | 当前任务创建流程不支持 |
| Cookie / Referer / 鉴权 | ❌ | 不支持 |
| 校验和验证 | ❌ | 未实现 |

### 3.2 引擎差异对照

| 能力 | 内置引擎 | Gopeed | aria2 |
| --- | --- | --- | --- |
| 全局限速 | ✅ | ❌ | ✅ |
| 每任务连接数 | ✅（默认 8，可设 1–16） | ✅（经 `extra.connections`） | ✅（`split`/`max-connection-per-server`） |
| 断点续传实现 | staging 分段记录 + ETag 校验 | Gopeed 内核管理（暂停会停止内嵌服务，恢复重建任务；待验证） | `.aria2` 控制文件 + `continue=true` |
| 进程崩溃影响 | 仅本任务失败 | 可能导致 App 崩溃 | 子进程退出，Controller 清空 gid 后重建 |
| 需要额外产物 | 否 | `app/libs/libgopeed.aar` | `jniLibs/arm64-v8a/libaria2c.so` |
| 未放入产物时 | 正常 | 显示不可用 | 显示不可用 |

---

## 四、工程结构

```
motrix-lite/
├── app/
│   ├── build.gradle.kts                 # SDK/版本/ABI/签名/R8/依赖；存在 AAR 时自动打包
│   ├── libs/libgopeed.aar               # Gopeed AAR（.gitignore 忽略，由构建脚本生成）
│   ├── proguard-rules.pro               # R8 规则（含原生引擎反射类保留）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # 权限、服务、FileProvider
│       │   ├── assets/licenses/         # GPLv3/GPLv2/aria2 COPYING/Gopeed LICENSE/源码要约
│       │   ├── jniLibs/arm64-v8a/libaria2c.so   # aria2 二进制（.gitignore 忽略）
│       │   ├── java/com/motrixlite/downloader/
│       │   │   ├── App.kt / AppContainer.kt
│       │   │   ├── data/                # 任务仓库、JSON 编解码 v2、设置存储
│       │   │   ├── engine/              # 内置内核：探测、分段、下载、调度、限速
│       │   │   │   └── native/          # aria2/Gopeed 控制器、RPC/API 客户端、可用性探测
│       │   │   ├── network/             # 网络状态监听
│       │   │   ├── service/             # 前台服务、通知、启动封装
│       │   │   ├── storage/             # MediaStore / SAF 发布
│       │   │   ├── ui/                  # Compose 界面、ViewModel、主题
│       │   │   └── util/                # HyperOS 引导、权限、打开文件
│       │   └── res/
│       └── test/java/com/motrixlite/downloader/
│           ├── data/TaskJsonCodecTest.kt
│           └── engine/{Formatters,FileNameUtils,SegmentPlanner,RateLimiter,HttpProbe,TaskDownloader}Test.kt
├── native/
│   ├── README.md                        # 原生内核构建/许可证总览
│   ├── gopeed/build.ps1                 # 构建 Gopeed AAR（Go 1.24.11 / NDK 27.2.12479018 / v1.9.3）
│   ├── aria2/fetch.ps1                  # 获取/校验 aria2 arm64 预编译二进制
│   ├── aria2/README.md                  # aria2 集成与 GPLv2 合规说明
│   ├── licenses/                        # 许可证文本（GPLv3/GPLv2/aria2/Gopeed/OpenSSL 3.0.0 Apache-2.0/依赖/源码要约）
│   └── sources/                         # 二进制来源构建文件（.gitignore 忽略）
├── licenses/                            # 许可证文本（GPLv3/GPLv2/aria2/Gopeed/源码要约）
├── docs/
│   ├── ARCHITECTURE.md                  # 架构与内核细节（含双原生引擎）
│   ├── BUILD_AND_SIGN.md                # 构建、签名、排错
│   ├── TEST_CHECKLIST.md                # 真机验收清单（A1–A7 + B1–B8 + E1–E8）
│   ├── KNOWN_ISSUES.md                  # 已知问题
│   ├── COMPLIANCE.md                    # GPLv3/GPLv2 合规与分发清单
│   ├── ENGINE_LICENSE_ANALYSIS.md       # 引擎许可证分析报告
│   └── ENGINE_INTEGRATION_PLAN.md       # 双内核集成方案（已实施）
├── keystore/release.keystore            # 演示签名证书（不可用于上架）
├── tools/range_test_server.py           # 本地 Range 测试服务器
├── dist/                                # 最终 APK（v1.1.0 debug/release）+ SHA256SUMS.txt
├── gradle/wrapper/                      # Gradle 8.9 wrapper
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradlew / gradlew.bat
├── keystore.properties                  # 演示签名配置（.gitignore 忽略）
├── local.properties                     # SDK 路径（不提交）
├── LICENSE                              # GNU GPLv3 全文
├── README.md
└── THIRD_PARTY_NOTICES.md               # 第三方组件许可清单
```

### 关键源文件（1.1.0）

| 文件 | 职责 |
| --- | --- |
| `engine/native/Aria2EngineController.kt` | aria2 子进程生命周期、gid 映射、RPC 轮询、暂停/恢复、完成发布 |
| `engine/native/Aria2RpcClient.kt` | localhost JSON-RPC 客户端（token 鉴权） |
| `engine/native/GopeedEngineController.kt` | Gopeed AAR 启动/停止、REST 轮询、任务映射、完成发布 |
| `engine/native/GopeedApi.kt` | Gopeed 内嵌 REST API 客户端 |
| `engine/native/EngineAvailability.kt` | 原生内核可用性探测 + Gopeed 反射桥接 |
| `engine/DownloadCoordinator.kt` | 内置引擎队列/并发/Wi-Fi/重试（只处理 `BUILTIN`） |
| `service/DownloadService.kt` | 唯一前台服务；持有 Coordinator + 两个原生引擎 Controller |
| `data/DownloadRepository.kt` | 统一任务仓库；引擎字段持久化、重启清理原生 ID |
| `data/TaskJsonCodec.kt` | schema v2 编解码，旧 v1 兼容 |
| `data/model/Models.kt` | `DownloadEngine`、`DownloadTask.engine/engineTaskId/engineStatus`、`AppSettings.downloadEngine` |
| `ui/screens/SettingsScreen.kt` | 引擎选择、可用性/不可用提示、开源许可对话框 |
| `ui/components/TaskCard.kt` / `ui/sheets/TaskDetailSheet.kt` | 任务卡片/详情显示引擎与引擎任务 ID、原始状态 |
| `ui/MainViewModel.kt` | 引擎可用性状态、新建任务前校验、源码仓库跳转 |

其余模块（内置下载内核、发布、通知、HyperOS 引导）与 1.0.0 一致，详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 五、关键实现说明

### 5.1 引擎选择与任务绑定

- 设置页「下载引擎（仅影响新建任务）」提供 内置 / Gopeed / aria2 三个单选项；不可用的引擎显示"当前 APK 不可用"并禁用。
- `DownloadRepository.createTask()` 读取 `settings.downloadEngine` 写入 `task.engine`，随后持久化；**之后切换设置不会修改已有任务**。
- 任务卡片在非内置引擎任务上显示 `· Gopeed` / `· aria2`；任务详情显示"下载引擎"、引擎任务 ID（aria2 gid / Gopeed task id）与引擎原始状态。
- 新建任务时 `MainViewModel.startDownload()` 会再次校验目标引擎可用性；不可用则 Snackbar 提示并拒绝创建。
- 原生任务的创建/暂停/恢复/删除不是由 UI 直接调用引擎，而是**修改 Repository 状态**，由对应 Controller 的轮询循环（约 700 ms）感知后执行引擎侧操作；因此 UI 与引擎解耦，服务重启后状态可重建。

### 5.2 aria2 生命周期

**为什么不是 assets → 私有目录 → exec**：Android 10（API 29）起，targetSdk ≥ 29 的应用不能 `exec()` 应用私有数据目录中的文件。本工程把二进制重命名为 `libaria2c.so` 放入 `app/src/main/jniLibs/arm64-v8a/`，由系统安装时解压到 `applicationInfo.nativeLibraryDir`，再从该目录启动；`packaging.jniLibs.useLegacyPackaging = true` 确保系统解压而不是直接映射压缩包。

运行流程（`Aria2EngineController`）：

1. 存在 `engine=ARIA2` 且未完成/需要运行的任务时，按需启动一次 `libaria2c.so` 子进程；
2. 启动参数包含：随机本地 RPC 端口、随机 `--rpc-secret`、`--enable-rpc`、`--rpc-listen-all=false`、任务根目录、`--continue=true`、`--max-concurrent-downloads`、`--max-overall-download-limit`、`--stop-with-process=<App pid>`、日志文件等；
3. 用 `aria2.getVersion` 轮询等待就绪（最多约 10 秒），失败则标记相关任务失败并进入退避；
4. 每个任务通过 `aria2.addUri` 创建，options 指定 `dir`（`staging/engines/aria2/tasks/<taskId>`）、`out`、`continue=true`、`auto-file-renaming=false`、`allow-overwrite=true`、`split`、`max-connection-per-server`、UA 等；gid 写入 `task.engineTaskId`；
5. 每轮 `aria2.tellStatus` 更新进度/状态/速度/总长度：`active/waiting` → 下载中/连接中，`paused` → 暂停或自动恢复，`complete` → 发布，`error/removed` → 失败/重新排队；
6. 全局限速与并发通过 `changeGlobalOption` 在设置变化时下发（`max-overall-download-limit`、`max-concurrent-downloads`）；
7. 仅 Wi-Fi 时通过 `aria2.pause` 暂停任务并置 `WAITING_FOR_WIFI`，网络恢复后重新排队/继续；
8. 进程停止（服务停止、所有 aria2 任务完成/暂停）时：清空内存 gid → 清空任务的 `engineTaskId` → `aria2.shutdown`/`forceShutdown` → `waitFor` 回收，3 秒未退出则强制杀死；
9. **断点续传**：进程重启后 gid 必然失效，任务恢复时按原 URL 重新 `addUri` 且 `continue=true`，依赖任务目录中的 `.aria2` 控制文件从已有字节继续。

### 5.3 Gopeed AAR

- AAR 由 `native/gopeed/build.ps1` 生成，产物 `app/libs/libgopeed.aar`（不提交仓库）。`app/build.gradle.kts` 只有在文件存在时才 `implementation(files("libs/libgopeed.aar"))`，因此源码构建不依赖二进制。
- 为避免编译期依赖，`EngineAvailability.kt` 中的 `GopeedBridge` **完全通过反射**调用 gomobile 生成的类。实际 AAR 的 Java 入口类为 `com.gopeed.libgopeed.Libgopeed`（反射候选列表包含它）；AAR 只导出 `start` / `stop` 两个方法，没有事件订阅/InvokeAsync 桥，因此 Controller 全部通过内嵌 REST 轮询与控制。调用 `start(configJson)` 启动服务并返回随机端口；停止时调用 `stop()`。
- 启动配置：监听 `127.0.0.1` 随机端口、随机 `X-Api-Token`、内存存储、下载目录指向 `staging/engines/gopeed`、`maxRunning` 取设置并发数。
- Controller 通过 REST 轮询（约 700 ms）任务列表并控制：`POST /api/v1/tasks` 创建、`PUT .../pause`、`PUT .../continue`、`DELETE ...?force=true`；任务状态映射 `running` → 下载中、`ready/wait` → 连接中、`pause` → 暂停、`error` → 失败、`done` → 发布。
- 完成任务后从任务目录解析实际文件，交给 `DestinationPublisher` 发布，完成后删除引擎侧任务与临时目录。
- **没有需要运行的任务（全部完成或已暂停）时调用 `stop` 关闭服务**，避免 Go 线程与端口泄漏；服务 `onDestroy` 时同样停止。注意：暂停 Gopeed 任务会停止内嵌服务并清空 `engineTaskId`，恢复时重建任务，其续传行为依赖 Gopeed 内核对已有文件的处理（待 E2 真机验证）。
- **限制**：Gopeed REST API 当前不提供全局限速，设置页限速对 Gopeed 任务不生效；AAR 与 App 同进程，Go 层 panic/OOM 可能导致 App 崩溃。

### 5.4 前台服务协调

`DownloadService` 是唯一的前台服务，`onCreate` 时构造并持有一个 `DownloadCoordinator` 与两个原生引擎 Controller：

- 服务启动（`onStartCommand`）时依次 `coordinator.start()`、`aria2Controller.start()`、`gopeedController.start()`；
- 服务停止（`onDestroy`）时依次 `stop()`：取消轮询作用域、关闭 aria2 子进程（shutdown + 回收）、停止 Gopeed 服务并清空引擎任务 ID；
- 三个执行器都只处理各自引擎的任务；通知聚合、WakeLock、空闲退出仍由服务统一负责；
- 因此**两个原生引擎可能同时运行**（例如旧任务用 aria2、新任务用 Gopeed），内存/CPU/耗电相应增加，设置页有说明；
- 服务空闲 4 秒后自动停止，停止时回收两个原生内核，避免孤儿进程/线程。

### 5.5 存储与发布

- staging 根目录：`getExternalFilesDir(null)/staging`（不可用时回退 `filesDir/staging`）；
- 原生引擎任务目录：`staging/engines/aria2/tasks/<taskId>`、`staging/engines/gopeed/tasks/<taskId>`；
- 完成后统一交给 `DestinationPublisher`，按设置的保存模式写入 MediaStore `Download/MotrixLite`、SAF 目录或 Android 8/9 的应用专属目录；
- 发布成功后删除 staging/任务目录；发布失败时任务标记失败并保留 staging 以便重试（与内置引擎一致）。

内置引擎的探测、分段、限速、断点续传等细节见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 第四、五节。

---

## 六、构建

### 6.1 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | **17**（构建验证使用 Microsoft OpenJDK 17.0.20） |
| Gradle | 8.9（由 wrapper 自动下载） |
| Android SDK | Platform 35、Build-Tools 35.0.0、Platform-Tools |
| Android Studio（可选） | Ladybug 2024.2.1 或更新 |

### 6.2 方式 A：Android Studio

1. `File → Open` 选择工程根目录，等待 Gradle Sync。
2. `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` 选择 **17**。
3. 安装 SDK Platform 35 与 Build-Tools 35.0.0。
4. 点击 ▶ Run，或 `Build → Build APK(s)`。

### 6.3 方式 B：命令行（Windows）

```powershell
$env:JAVA_HOME   = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "C:\Android\sdk"

# 安装 SDK 组件（首次）
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses

# 构建
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
```

macOS / Linux：把 `.\gradlew.bat` 换成 `./gradlew`。

> Gradle wrapper 首次运行需联网下载 Gradle 8.9；网络受限时的镜像/离线处理见 [docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md) 第四节。

### 6.4 原生产物（可选，但 v1.1.0 最终构建已放入）

不放入原生产物时，Gradle 构建仍可编译运行，只是设置页显示两个原生引擎不可用。要启用双内核，先在 Windows 上生成/获取产物：

```powershell
# 1) Gopeed AAR（固定 Go 1.24.11 / NDK 27.2.12479018 / Gopeed v1.9.3）
powershell -ExecutionPolicy Bypass -File native/gopeed/build.ps1
# 产物：app/libs/libgopeed.aar（被 .gitignore 忽略）

# 2) aria2 arm64 二进制（devgianlu/aria2-android v1.37.0，69,107,592 字节）
powershell -ExecutionPolicy Bypass -File native/aria2/fetch.ps1
# 产物：app/src/main/jniLibs/arm64-v8a/libaria2c.so（被 .gitignore 忽略）
```

**v1.1.0 最终构建实际使用的原生产物**：

| 产物 | 大小 | SHA-256 |
| --- | --- | --- |
| `app/libs/libgopeed.aar`（Gopeed v1.9.3，arm64） | 15,438,987 B | `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c` |
| `app/src/main/jniLibs/arm64-v8a/libaria2c.so`（aria2 1.37.0，arm64） | 69,107,592 B | `dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a` |

- `build.ps1` 固定 Go **1.24.11**、NDK **27.2.12479018**、Gopeed **v1.9.3**；`golang.org/x/mobile@latest` 要求 Go ≥ 1.26，因此 gomobile 实际通过 `GOTOOLCHAIN=auto` 使用 **go1.26.10** 工具链完成编译（x/mobile `v0.0.0-20261011034528-197770444867`）；详细记录见 `.tools/gopeed-build/BUILD_REPORT.md`（不提交仓库）。
- AAR 内 `jni/arm64-v8a/libgojni.so` 为 43,908,776 B；APK 打包后位于 `lib/arm64-v8a/libgojni.so`。AAR 实际导出 `start`/`stop`（Java 入口类为 `com.gopeed.libgopeed.Libgopeed`），任务与进度通过内嵌 REST API 完成。
- `fetch.ps1` 默认经 `gh-proxy.com` 下载 `aria2c-arm64-v8a`（69,107,592 字节），校验大小与 SHA-256，再复制为 `libaria2c.so`；本次二进制经 24 路 Range 分片下载获取，并与独立下载方核对哈希一致。
- 两个产物均**不提交仓库**；正式发布前建议自行按 `devgianlu/aria2-android` 构建脚本重建 aria2 并记录 SBOM，降低供应链风险。

### 6.5 构建产物与体积

**v1.1.0 最终构建（Windows，JDK 17.0.20）已通过 `:app:assembleDebug` 与 `:app:assembleRelease`（release 经 R8 + 演示签名），两个原生内核均已打包。**

| 类型 | 路径 | 大小 | SHA-256（实际计算） |
| --- | --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 67,482,342 B（64.36 MB） | `b78789b787ff9a9465a45657b3a807de31460db10385581d3f410f3fbcff5689` |
| Release | `app/build/outputs/apk/release/app-release.apk` | 38,559,243 B（36.77 MB） | `5272ca1e679786a05a7f4c3abf8f013a05f3a445996084341c33f86244155d5f` |
| 分发副本 | `dist/MotrixLite-debug.apk` | 67,482,342 B | 同上（与构建产物一致） |
| 分发副本 | `dist/MotrixLite-release.apk` | 38,559,243 B | 同上（与构建产物一致） |
| 校验文件 | `dist/SHA256SUMS.txt` | — | 记录上述两个 APK 的大小与 SHA-256 |

> 说明：release 明显小于 debug，是因为 release 开启 R8 压缩/资源裁剪且不含 `ui-tooling` 等调试依赖；两者均包含 `lib/arm64-v8a/libaria2c.so`、`lib/arm64-v8a/libgojni.so` 与 `assets/licenses/` 的 16 个许可证文件（GPLv3/GPLv2/aria2/Gopeed/OpenSSL 3.0.0 Apache-2.0+版权/静态依赖许可证/DEPENDENCY-VERSIONS/SOURCE-OFFER/THIRD_PARTY_NOTICES）。以上为补齐许可证 assets 后的最终 rebuild，已用 `Get-FileHash` 与 `dist/SHA256SUMS.txt` 复核。APK 校验：

```powershell
Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256
Get-FileHash dist\MotrixLite-debug.apk -Algorithm SHA256
```

### 6.6 签名

`app/build.gradle.kts` 在 `keystore.properties` 存在时使用 `release` 签名配置；不存在时回退到 debug 签名（可构建，但**不可用于发布**）。

当前仓库内的演示配置（`keystore.properties` 已被 `.gitignore` 忽略）：

```properties
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

完整的签名、R8 排错、镜像与离线构建说明见 **[docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md)**。

---

## 七、安装与使用

### 7.1 安装

```powershell
# USB 调试
adb devices
adb install -r app\build\outputs\apk\release\app-release.apk
```

也可以把 APK 传到手机，用文件管理器点击安装（需允许"安装未知来源应用"）。

要求：

- Android 8.0（API 26）及以上；
- **仅支持 arm64-v8a 设备**（x86 模拟器/wear 等其它 ABI 无法安装）；
- 建议预留足够的可用空间：原生内核会显著增大 APK，下载大文件时还需 staging 空间（发布阶段峰值约为文件大小的 2 倍）。

### 7.2 首次启动建议

1. **允许通知**（Android 13+）：否则后台进度不可见，应用会显示横幅提示。
2. **检查保存位置**：默认"系统下载目录（推荐）"→ `Download/MotrixLite`，无需任何存储权限。
3. **选择下载引擎**：默认内置引擎；若构建时已放入原生产物，可切换 Gopeed / aria2（仅影响之后新建的任务）。
4. **HyperOS 用户**：按任务页顶部"后台稳定运行建议"卡片完成电池优化、自启动、多任务锁定（见第九节）。

### 7.3 下载一个文件

1. 点击右下角 **新建任务**。
2. 粘贴 HTTP/HTTPS 直链（可点"粘贴"从剪贴板读取）。
3. 可选：点 **解析链接** 预览文件名、大小、是否支持断点续传；弹层底部会显示当前将使用的下载引擎。
4. 可选：修改文件名（留空则用服务器/链接中的名字）。
5. 点 **开始下载**。

### 7.4 任务操作

| 操作 | 入口 |
| --- | --- |
| 暂停 / 继续 | 任务卡片右侧按钮，或点击卡片进入详情 |
| 重试失败任务 | 任务卡片 / 详情页「重试」 |
| 查看进度、速度、分段、引擎信息 | 点击任务卡片 |
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

卸载应用不会删除已发布到 `Download/MotrixLite` 的文件，但会删除未完成的 staging 数据与原生引擎临时目录。

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

原因：Android 10+ 通过 `MediaStore` 写入 `Download` 不需要存储权限；自定义目录通过 SAF 的 URI 授权；Android 8/9 回退到应用专属目录。**最小权限原则**。

### 8.1 网络安全：为什么允许明文 HTTP

`AndroidManifest.xml` 中设置了 `android:usesCleartextTraffic="true"`。原因：大量下载源（局域网服务、自建文件服务器、老站点）仍只提供 `http://`。

**风险**（必须知悉）：

- 下载内容可被同一网络中的中间人**篡改或替换**，且应用**没有校验和验证**（KI-09），无法发现；
- 请求 URL、文件名、文件大小对链路可见；
- DNS 被劫持时可能连到伪造服务器。

**建议**：优先使用 `https://`；仅在可信网络对可信源使用 HTTP；重要文件下载后自行比对官方哈希。当前版本**未实现**"仅 HTTPS"开关。

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

**为什么只能引导**：这些开关由 MIUI 安全中心/电源管理模块实现，**没有公开 API**。跳转使用组件名尝试（`HyperOSGuide` 中列出多个候选 Activity），失败时**自动回退到应用详情页**，不会崩溃。

### 9.2 Android 15 前台服务时限

Android 15 对 `dataSync` 类型前台服务限制为 **24 小时内累计 6 小时**。触发 `onTimeout` 后：暂停全部任务 → 保存进度 → 移除前台通知 → 释放 WakeLock → 提示"已达系统时限，重新打开应用即可继续"。

### 9.3 Android 14 前台服务类型

Manifest 声明 `android:foregroundServiceType="dataSync"`，运行时传入 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`，并申请 `FOREGROUND_SERVICE_DATA_SYNC` 权限。

### 9.4 其它版本适配

| 版本 | 适配点 |
| --- | --- |
| Android 13（API 33） | `POST_NOTIFICATIONS` 运行时申请；拒绝后降级并在应用内提示 |
| Android 12（API 31） | 后台启动前台服务受限；`ServiceController` 捕获异常，回到前台后重试 |
| Android 10（API 29） | 分区存储；改用 `MediaStore` 写入 `Download/MotrixLite`，不再申请存储权限 |
| Android 8/9（API 26–28） | 无分区存储；保存到应用专属外部目录并通知媒体库 |

---

## 十、测试与验收状态

### 10.1 本机已完成的验证（Windows，JDK 17.0.20）

| 项 | 结果 | 证据 |
| --- | --- | --- |
| JVM 单元测试 | ✅ **32 / 32 通过** | **9 个测试类**；`app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:assembleDebug` | ✅ 通过 | 已放入 `libgopeed.aar` 与 `libaria2c.so`；产物 67,482,342 B |
| `:app:assembleRelease` | ✅ 通过 | R8 压缩 + 演示签名；产物 38,559,243 B |
| APK 内容校验 | ✅ 通过 | 两个 APK 都包含 `lib/arm64-v8a/libaria2c.so`、`lib/arm64-v8a/libgojni.so` 与 `assets/licenses/` 的 16 个许可证文件（含 OpenSSL 3.0.0 Apache-2.0+版权、各静态依赖、DEPENDENCY-VERSIONS） |
| Release 包 SHA-256 | ✅ 已计算 | `5272ca1e679786a05a7f4c3abf8f013a05f3a445996084341c33f86244155d5f`（debug 见 6.5） |
| 真机安装与功能验收 | ❌ **未执行** | 本环境无 Android 设备/模拟器 |

单元测试明细（测试类 9 个；1.1.0 新增 `Aria2RpcClientTest`、`GopeedApiTest`，各 3 个用例，`TaskJsonCodec` 新增 1 个引擎/旧 v1 兼容用例）：

| 测试类 | 用例 | 覆盖点 |
| --- | --- | --- |
| `FormattersTest` | 3 | 字节/速度/ETA 格式化 |
| `FileNameUtilsTest` | 6 | RFC 5987、引号 filename、URL 推导、非法字符清洗、扩展名补齐、MIME 双向映射 |
| `SegmentPlannerTest` | 4 | 分段连续覆盖、小文件单段、未知长度、0 字节 |
| `RateLimiterTest` | 3 | 不限速立即返回、缓冲区自适应、限速下最终放行 |
| `TaskJsonCodecTest` | 3 | v2 字段往返/运行期字段丢弃；**engine/旧 v1 默认 BUILTIN 兼容**；空/畸形数据 |
| `HttpProbeTest` | 4 | Range 探测与元数据解析、无 Range 识别、HTTP 错误可重试性、`Content-Range` 解析 |
| `TaskDownloaderTest` | 3 | 多段下载字节完全一致、暂停后续传字节一致、无 Range 服务器降级 |
| `Aria2RpcClientTest` | 3 | `addUri` 的 secret/URL/options、`tellStatus` 解析与错误映射、`changeGlobalOption` 仅 OK 生效 |
| `GopeedApiTest` | 3 | `createTask` 的 token/请求体、任务列表与 pause PUT、非零 envelope 抛错 |
| **合计** | **32** | **全部通过** |

> 说明：新增单测通过 MockWebServer 覆盖两个原生引擎的 **RPC/REST 客户端协议层**；`Aria2EngineController` / `GopeedEngineController` 的进程与 AAR 生命周期仍无 JVM 单测，依赖真机 E 系列验证。不能把 32/32 理解为"双内核已真机验证"。

### 10.2 尚未验证的内容（必须如实说明）

**本环境没有 Android 设备，也没有模拟器**，以下内容**全部待真机验证**，本文档不声称已通过：

- 安装、启动、首屏渲染（红米 Turbo 4 Pro 未测试）；
- 内置引擎真机多连接下载、锁屏/后台 10 分钟保活；
- **Gopeed 引擎实际下载、断点续传、锁屏 10 分钟**；
- **aria2 引擎实际下载、断点续传、锁屏 10 分钟、进程/会话恢复**；
- 引擎切换后新旧任务分别使用各自引擎、双引擎任务并存；
- 服务停止/杀进程后是否残留 `aria2c` 进程、Gopeed 端口/线程是否回收；
- AAR/二进制加载失败时的提示与不闪退表现；
- 许可证页面、源码 offer、完全对应源码的可获取性；
- 通知权限拒绝降级、SAF、MediaStore 可见性、Android 15 前台服务时限、HyperOS 后台清理等原有项。

真机验收请按 **[docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md)** 执行：A1–A7 为基础验收，B1–B8 为边界用例，**E1–E8 为双内核专项**，并把结果回填汇总表与本节。

---

## 十一、已知问题（摘要）

完整列表（含现象、原因、规避、后续计划）见 **[docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)**。

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| KI-01 | 不支持 BT / 磁力链接 | 无法下载 BT 资源（即使是 Gopeed/aria2 引擎，当前也只走 HTTP 任务创建） |
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
| KI-12 | 未在真实红米 Turbo 4 Pro 上测试 | 真机行为未知 |
| KI-13 | 允许 HTTP 明文下载 | 内容可能被中间人篡改 |
| KI-17 | 网络切换不会立即中断已有连接 | 可能产生少量蜂窝流量 |
| **KI-23** | **Gopeed 不支持全局限速** | 限速设置对 Gopeed 任务不生效 |
| **KI-24** | **Gopeed 与 App 同进程** | Go panic/OOM 可能导致 App 崩溃 |
| **KI-25** | **APK 体积显著增大** | 安装/更新成本高，低端机空间紧张 |
| **KI-26** | **原生二进制供应链风险** | 未自行重建时依赖第三方预编译产物 |
| **KI-27** | **双原生引擎可同时运行** | 内存/CPU/耗电与稳定性风险增加 |
| **KI-28** | **并发上限不跨引擎统一** | 多引擎同时运行时实际活跃任务数可能超过设置 |
| **KI-29** | **Gopeed 暂停后重建任务，续传行为待验证** | 暂停/恢复可能浪费已下载流量（待 E2 真机验证） |
| **KI-30** | **供应链：OpenSSL 3.0.0 较旧、libssh2 为 dev 快照** | 潜在已知 CVE / 非正式发行版；建议重建或披露供应链时点 |

---

## 十二、许可证与源码获取

| 项目 | 许可证 | 与本项目的关系 |
| --- | --- | --- |
| 本项目代码 | **GNU GPLv3** | 见 [LICENSE](LICENSE)；整体发布物按 GPLv3 分发 |
| Gopeed v1.9.3 | **GPL-3.0** | gomobile AAR 链接进 App，构建脚本 `native/gopeed/build.ps1` |
| aria2 1.37.0 | **GPL-2.0-or-later** | arm64 预编译二进制，子进程 + JSON-RPC，获取/校验脚本 `native/aria2/fetch.ps1`；仓库另有历史 `LICENSE.OpenSSL` 例外文本 |
| Motrix 桌面版 | MIT | 仅参考产品与交互思路，**未使用其代码/资源/图标** |
| Kotlin / Coroutines / OkHttp / AndroidX / Compose / Material3 | Apache-2.0 | 随 APK 分发 |
| Go runtime（Gopeed AAR 内） | BSD-3-Clause | 随 APK 分发（实际编译工具链 go1.26.10） |
| **OpenSSL 3.0.0（aria2 静态链接）** | **Apache-2.0** | 本二进制实际使用；附 `OpenSSL-3.0.0-Apache-2.0.txt` 与 `OpenSSL-3.0.0-COPYRIGHT.txt` |
| zlib 1.2.11 / c-ares 1.18.1 / libexpat 2.4.1 / libssh2 1.9.0_DEV / libc++（NDK r23b） | zlib / MIT / MIT / BSD-3 / Apache-2.0 WITH LLVM-exception | aria2 静态链接依赖；版本与 commit 见 `licenses/DEPENDENCY-VERSIONS.md` |

**完整许可证文本**：随最终 APK 打包在 `assets/licenses/`（16 个文件，已解包核验），同时存放于仓库 `licenses/` 与 `native/licenses/`，包括 GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 3.0.0 Apache-2.0 全文+版权、各静态依赖许可证、历史 OpenSSL 例外说明、依赖版本清单与 SOURCE-OFFER、THIRD_PARTY_NOTICES。

**对应源码（Corresponding Source）与构建脚本**：

- 本项目全部源码（含引擎集成代码、构建脚本、资源）：`https://github.com/Chenzhenyan0/motrix-lite`
- Gopeed v1.9.3：`https://github.com/GopeedLab/gopeed/tree/v1.9.3`
- aria2 1.37.0：`https://github.com/aria2/aria2/tree/release-1.37.0`；二进制构建参考 `https://github.com/devgianlu/aria2-android`
- 可复现构建：`native/gopeed/build.ps1`（Go 1.24.11 / NDK 27.2.12479018 / Gopeed v1.9.3）、`native/aria2/fetch.ps1`（固定版本与校验），以及 Gradle wrapper。

**书面要约**：对物理介质分发等场景，我们提供至少三年有效的对应源码获取要约；可通过仓库 Issue 联系。详见 [licenses/SOURCE-OFFER.txt](licenses/SOURCE-OFFER.txt) 与 [docs/COMPLIANCE.md](docs/COMPLIANCE.md)。

**明确否定**：任何声称"包含 Gopeed AAR 与 aria2 的同一 APK 可以闭源分发"的说法都不成立；GPLv3 也不允许在分发时附加禁止再分发/禁止反向工程等与 GPL 冲突的额外限制。

**商标**：Motrix 的名称与商标归其作者所有。本项目为**非官方独立实现**，不使用 Motrix 官方图标、Logo 或品牌视觉素材，与官方无隶属、赞助或背书关系；Gopeed、aria2 名称仅用于事实描述。

---

## 附录 A：命令速查

```powershell
# ---------- 构建 ----------
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintVitalRelease
.\gradlew.bat clean

# ---------- 原生内核 ----------
powershell -ExecutionPolicy Bypass -File native/gopeed/build.ps1
powershell -ExecutionPolicy Bypass -File native/aria2/fetch.ps1

# ---------- 安装 / 卸载 ----------
adb devices
adb install -r app\build\outputs\apk\release\app-release.apk
adb uninstall com.motrixlite.downloader

# ---------- 签名 ----------
keytool -list -v -keystore keystore\release.keystore -storepass motrixlite2026
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk

# ---------- 哈希 ----------
Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256

# ---------- 本地测试服务器 ----------
python tools\range_test_server.py --port 8000 --size-mb 50
python tools\range_test_server.py --port 8000 --size-mb 50 --throttle-kbps 300
adb reverse tcp:8000 tcp:8000
#   应用内访问 http://127.0.0.1:8000/testfile.bin

# ---------- 调试 ----------
adb logcat -s DownloadService:V Aria2Engine:W GopeedEngine:W AndroidRuntime:E
adb shell dumpsys activity services com.motrixlite.downloader
adb shell ps -A | findstr aria2c
adb shell dumpsys notification --noredact | Select-String "MotrixLite"
adb shell ls -l /sdcard/Download/MotrixLite/
adb shell am force-stop com.motrixlite.downloader
```

macOS / Linux：把 `.\gradlew.bat` 换成 `./gradlew`，路径分隔符换成 `/`，`Select-String` 换成 `grep`，`Get-FileHash` 换成 `shasum -a 256`。

---

## 附录 B：相关文档

| 文档 | 内容 |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、状态机、三引擎、持久化、并发模型、扩展点 |
| [docs/BUILD_AND_SIGN.md](docs/BUILD_AND_SIGN.md) | 环境安装、AAR/二进制构建、R8 排错、签名与验证 |
| [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md) | 真机验收清单（A1–A7、B1–B8、E1–E8）、测试服务器用法、缺陷表 |
| [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | 已知问题 KI-01～KI-30 与限制 |
| [docs/COMPLIANCE.md](docs/COMPLIANCE.md) | GPLv3/GPLv2 义务、对应源码、书面要约、分发检查清单 |
| [docs/ENGINE_LICENSE_ANALYSIS.md](docs/ENGINE_LICENSE_ANALYSIS.md) | 引擎许可证分析报告（GPLv3 结论） |
| [docs/ENGINE_INTEGRATION_PLAN.md](docs/ENGINE_INTEGRATION_PLAN.md) | 双内核集成方案与实际实现对照 |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | 第三方组件与许可证清单 |
| [LICENSE](LICENSE) | GNU GPLv3 全文 |

---

## 附录 C：术语表

| 术语 | 含义 |
| --- | --- |
| 内置引擎 | 项目自研的 Kotlin/OkHttp 分段下载内核（`DownloadCoordinator` + `TaskDownloader`） |
| 原生引擎 | 指 Gopeed（AAR，同进程）与 aria2（子进程）两个第三方 GPL 内核 |
| AAR | Android Archive；Gopeed 由 gomobile bind 生成 `libgopeed.aar` |
| gid | aria2 为每个下载任务分配的全局 ID（本项目持久化到 `engineTaskId`） |
| JSON-RPC | aria2 的 RPC 协议；本工程通过 localhost + `--rpc-secret` 鉴权调用 |
| .aria2 控制文件 | aria2 在下载目录生成的续传控制文件，`continue=true` 时据此续传 |
| staging | 下载中的临时文件，位于应用专属目录，发布/完成后删除 |
| 分段（Segment） | 文件的一段连续字节区间 `[start, end]`，对应一个 Range 请求 |
| 断点续传 | 暂停/重启后从已下载位置继续 |
| `ETag` / `Last-Modified` | 服务器资源版本标识，用于 `If-Range` 与变化检测 |
| `IS_PENDING` | `MediaStore` 的写入中标记，置 1 时其他应用不可见 |
| FGS | Foreground Service，前台服务 |
| dataSync | Android 14+ 前台服务类型之一；Android 15 有 6 小时/24 小时限制 |
| SAF | Storage Access Framework，通过用户选择目录获得 URI 授权 |
| 令牌桶 | 限速算法：按时间补充令牌，取不到令牌则等待 |
| HyperOS | 小米/红米的 Android 定制系统（MIUI 后继） |
| `desiredRunning` | 用户是否希望任务运行，用于重启后区分"被杀"与"主动暂停" |
| Corresponding Source | GPL 术语：生成、安装、运行目标代码所需的全部源码 |

---

**Motrix Lite 1.1.0** · GNU GPLv3 · 三引擎（内置 / Gopeed / aria2）· 非官方独立实现 · **未在真机验收，使用前请阅读 [已知问题](docs/KNOWN_ISSUES.md)**
