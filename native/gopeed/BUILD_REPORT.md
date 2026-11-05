# Gopeed v1.9.3 Android AAR 构建报告（Motrix Lite 集成用）

- **构建状态**：✅ 成功
- **构建主机**：Windows 11，PowerShell（pwsh），JDK 17
- **构建日期**：2026-11-05 21:07 ~ 21:27（Asia/Shanghai），产物生成 21:27:20
- **构建产物**：`motrix-lite\app\libs\libgopeed.aar`
- **大小**：15,438,987 字节（14.72 MiB）
- **SHA-256**：`AA064E347705067A19F61222F85F199E0A8527F57FCD80F6E082BDB899DD2D7C`
- **ABI**：仅 `arm64-v8a`（按 Motrix Lite 要求单 ABI 控制体积）
- **Gopeed 源码版本**：v1.9.3（GitHub tag `v1.9.3`，commit `a5cd53f94c18ac65add684b1113fa5f0b47cc4da`）
- **Java 入口类**：`com.gopeed.libgopeed.Libgopeed`（**不是** `com.gopeed.Mobile`，见 §6）

> 重要差异：`docs/ENGINE_INTEGRATION_PLAN.md` 中设想的 `InvokeAsync` / `SubscribeTaskEvents` / `GetAPIServerState` / `StartAPIServer` /
> `StopAPIServer` / `RestartAPIServer` 在 Gopeed v1.9.3 的 `bind/mobile` 中**不存在**（已核对源码与 javap 产物）。
> AAR 实际只导出 `start` / `stop` 两个方法；任务与事件需在内嵌 REST 服务（HTTP API v1）上完成，见 §6.4。

---

## 1. 结果摘要

| 项目 | 值 |
|---|---|
| AAR 工作区副本 | `C:\Users\Mark3\Documents\deepseek-harness\default-workspace\motrix-lite\app\libs\libgopeed.aar` |
| AAR 构建目录副本 | `C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\bind\libgopeed.aar` |
| 大小 | 15,438,987 字节 / 14.72 MiB |
| SHA-256 | `AA064E347705067A19F61222F85F199E0A8527F57FCD80F6E082BDB899DD2D7C` |
| AAR 内部结构 | `AndroidManifest.xml`、`proguard.txt`、`classes.jar`、`R.txt`（空）、`jni/arm64-v8a/libgojni.so`、`res/`（空目录） |
| `classes.jar` | 9,984 字节；13 个 class（1 个主类 + `go.*` 支持类） |
| `jni/arm64-v8a/libgojni.so` | 43,908,776 字节（41.87 MiB），ELF64 / AArch64 / DYN（shared object），已用 `-ldflags="-w -s"` 去符号 |
| AAR 内 `AndroidManifest.xml` | `<manifest package="go.libgopeed.gojni">` + `<uses-sdk android:minSdkVersion="21"/>` |
| AAR 内 `proguard.txt` | `-keep class go.** { *; }` / `-keep class com.gopeed.** { *; }`（AGP 消费 AAR 时自动应用） |
| 总耗时 | 约 20 分钟（21:07:37 开始环境检查 → 21:27:20 AAR 完成），其中真正 `gomobile bind` 仅 2.54 分钟，其余为工具链/依赖下载 |

---

## 2. 工具链版本

| 工具 | 版本 / 路径 |
|---|---|
| JDK | Microsoft OpenJDK 17.0.20（`C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot`） |
| Go（安装的主版本，符合 go.mod 的 `go 1.24.9` + `toolchain go1.24.11`） | **go1.24.11 windows/amd64**，`C:\...\.tools\go1.24.11` |
| 实际参与移动端编译的 Go 工具链 | **go1.26.10 windows/amd64**（原因见 §8.3：`golang.org/x/mobile@latest` 要求 `go >= 1.26.0`，由 `GOTOOLCHAIN=auto` 自动下载，模块缓存路径 `gopath\pkg\mod\golang.org\toolchain@v0.0.1-go1.26.10.windows-amd64`） |
| gomobile / gobind | `golang.org/x/mobile v0.0.0-20261011034528-197770444867`（gomobile.exe、gobind.exe 均由 go1.26.10 构建，位于 `gopath\bin`） |
| Android NDK | **r27c / 27.2.12479018**（`C:\...\.tools\android-ndk-r27c`），clang 18.0.3 |
| Android SDK | `C:\...\.tools\android-sdk`，platform `android-35`，build-tools 34/35，platform-tools |
| Gopeed | `github.com/GopeedLab/gopeed v1.9.3` |
| 构建 tags | `nosqlite`（与官方一致；存储用 bbolt，排除 sqlite） |
| Android API | `-androidapi 21`（与官方 CI 一致） |
| Java 包前缀 | `-javapkg=com.gopeed`（与官方 CI 一致） |

## 3. 时间线与耗时（约）

| 时间 | 步骤 | 耗时/说明 |
|---|---|---|
| 21:07:37 | 开始环境检查、探测网络 | — |
| 21:09~21:11 | 尝试 dl.google.com 下载 Go zip | 停滞在 ~650 KB（0 KB/s），放弃 |
| 21:11~21:16 | 改从阿里云镜像下载 Go 1.24.11（87,287,538 B）并用 `tar.exe -xf` 解压 | ~4-5 分钟（约 0.45 MB/s） |
| 21:12~21:17 | 改从腾讯镜像下载 NDK r27c（781,511,249 B）并解压 | 下载约 29 MB/s（~30 s），解压 0.5 min，解压后 2.2 GB |
| 21:18~21:19:38 | `go install gomobile@latest`（含 GOTOOLCHAIN 自动下载 go1.26.10） | 1.3 分钟 |
| 21:21:33 | `go install gobind@latest` | ~1 分钟 |
| 21:19:59~21:22:05 | 创建 bind module、`go get` gopeed + x/mobile、`go mod download all` | 依赖 2 GB 模块缓存，`go mod download all` 22 秒 |
| 21:24:48~21:27:20 | **`gomobile bind` 生成 AAR** | **2.54 分钟**（exit=0） |
| 21:27:20 | 复制 AAR 至 `motrix-lite\app\libs\`、SHA-256、jar/javap 检查 | — |

关键进程命令为后台 job 运行并轮询；`build.log`（30,169 字节 / 905 行）保留了 `gomobile bind -v` 全过程日志。

---

## 4. 完整构建命令与环境

### 4.1 环境变量（`env.ps1`，已保存于 `.tools\gopeed-build\env.ps1`）

```powershell
$env:JAVA_HOME        = 'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot'
$env:ANDROID_HOME     = 'C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = 'C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\android-ndk-r27c'
$env:GOROOT           = 'C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\go1.24.11'
$env:GOPATH           = 'C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\gopath'
$env:GOBIN            = "$env:GOPATH\bin"
$env:GOPROXY          = 'https://goproxy.cn,https://mirrors.aliyun.com/goproxy,direct'
$env:GOSUMDB          = 'sum.golang.org'   # 见 §8.4；通过 goproxy.cn 的 /sumdb/ 镜像工作
$env:GOCACHE          = '...\.tools\gopeed-build\gocache'
$env:GOTMPDIR         = '...\.tools\gopeed-build\tmp'
$env:TMP = $env:TEMP  = $env:GOTMPDIR
$env:GO111MODULE      = 'on'
$env:GOTOOLCHAIN      = 'auto'
$env:CGO_ENABLED      = '1'
$env:PATH = "$env:GOROOT\bin;$env:GOBIN;$env:JAVA_HOME\bin;$env:ANDROID_NDK_HOME;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

### 4.2 安装 Go 1.24.11

```powershell
# dl.google.com 实测仅 ~0.1-0.5 MB/s 且多次停滞，改用阿里云镜像（官方同版本 zip）
curl.exe -L -o go1.24.11.windows-amd64.zip https://mirrors.aliyun.com/golang/go1.24.11.windows-amd64.zip
tar.exe -xf go1.24.11.windows-amd64.zip -C .\go-extract
Move-Item .\go-extract\go C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\go1.24.11
# 验证
go version   # go version go1.24.11 windows/amd64
```

### 4.3 安装 Android NDK r27c

```powershell
# 首选 sdkmanager 实测下载仅 ~0.4 MB/s（781 MB 需 30+ 分钟），已中止；改为直接下载官方 NDK zip
curl.exe -L -o android-ndk-r27c-windows.zip https://mirrors.cloud.tencent.com/AndroidSDK/android-ndk-r27c-windows.zip
tar.exe -xf android-ndk-r27c-windows.zip -C .\ndk-extract
Move-Item .\ndk-extract\android-ndk-r27c C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\android-ndk-r27c
# 验证
Get-Content ...\android-ndk-r27c\source.properties
# Pkg.Desc = Android NDK
# Pkg.Revision = 27.2.12479018
# Pkg.ReleaseName = r27c
```

> 说明：腾讯镜像文件长度 781,511,249 B 与 dl.google.com 上 `android-ndk-r27c-windows.zip` 完全一致，解压后版本/clang 校验通过；
> 但**未**与 Google 官方 AGP 仓库 XML 中的 SHA-1 比对（见 §10 风险）。

### 4.4 安装 gomobile / gobind 并初始化

```powershell
go install golang.org/x/mobile/cmd/gomobile@latest   # 自动切换并下载 go1.26.10 工具链
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
# 该版本 gomobile init 不支持 -ndk 参数，NDK 位置由 ANDROID_NDK_HOME 决定（优先级最高），init exit=0
```

### 4.5 创建 bind module 并拉取依赖

```powershell
$bind = 'C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\bind'
Set-Location $bind
go mod init motrixlite/gopeedbind
go get github.com/GopeedLab/gopeed@v1.9.3      # 官方命令：go get GopeedLab/gopeed
go get golang.org/x/mobile/bind                 # 官方 CI 同款（gomobile bind 要求 x/mobile 在当前模块依赖图中）
go mod download all                             # 预热模块缓存并补全 go.sum（318 行）
```

> 官方 CI 是在 Gopeed 仓库根目录直接执行，我们按任务要求改为独立 module（`motrixlite/gopeedbind`）后 `gomobile bind` 同样的包路径。
> `go.mod` 最终内容（`golang.org/x/mobile` 将 go 版本要求提升到 1.26.0）：
>
> ```
> module motrixlite/gopeedbind
>
> go 1.26.0
>
> require (
> 	github.com/GopeedLab/gopeed v1.9.3 // indirect
> 	golang.org/x/mobile v0.0.0-20261011034528-197770444867 // indirect
> 	golang.org/x/mod v0.42.0 // indirect
> 	golang.org/x/sync v0.24.0 // indirect
> 	golang.org/x/tools v0.52.0 // indirect
> )
> ```

### 4.6 生成 AAR（最终成功命令，原文）

```powershell
Set-Location C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\bind
$ldflags = '-w -s -checklinkname=0 -X github.com/GopeedLab/gopeed/pkg/base.Version=v1.9.3'
& "...\.tools\gopeed-build\gopath\bin\gomobile.exe" bind -v -tags nosqlite -ldflags="$ldflags" `
    -o libgopeed.aar -target=android/arm64 -androidapi 21 -javapkg=com.gopeed `
    github.com/GopeedLab/gopeed/bind/mobile
# exit=0，耗时 2.54 min，输出 libgopeed.aar + libgopeed-sources.jar
```

对照 Gopeed v1.9.3 官方 CI（`.github/workflows/build.yml` 的 `build-android` job）：

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go get golang.org/x/mobile/bind
gomobile init
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0 -X github.com/GopeedLab/gopeed/pkg/base.Version=$VERSION" \
  -o ui/flutter/android/app/libs/libgopeed.aar -target=android -androidapi 21 -javapkg=com.gopeed \
  github.com/GopeedLab/gopeed/bind/mobile
```

差异仅：官方 `-target=android`（arm/arm64/386/amd64 全 ABI），本构建按任务要求只编 `android/arm64`；其余参数（`nosqlite`、
`-w -s -checklinkname=0`、`-androidapi 21`、`-javapkg=com.gopeed`）与官方完全一致。`-checklinkname=0` 是官方必需参数
（Go 1.23+ 链接器默认校验 `//go:linkname`，Gopeed 依赖链中 quic-go 等使用内部 linkname 会触发链接失败）。

### 4.7 检查与复制

```powershell
# 复制到 Motrix Lite（仅写允许路径 app\libs）
New-Item -ItemType Directory -Force ...\motrix-lite\app\libs
Copy-Item libgopeed.aar ...\motrix-lite\app\libs\libgopeed.aar
# 校验
Get-FileHash libgopeed.aar -Algorithm SHA256
jar tf libgopeed.aar
# 解压并 javap（详见 §6）
jar xf libgopeed.aar
javap -classpath classes.jar -public com.gopeed.libgopeed.Libgopeed
javap -classpath classes.jar -public -s com.gopeed.libgopeed.Libgopeed
```

---

## 5. AAR 内部结构

`jar tf libgopeed.aar`：

```
AndroidManifest.xml
proguard.txt
classes.jar
jni/arm64-v8a/libgojni.so
R.txt
res/
```

`classes.jar` 内容（13 个 class）：

```
META-INF/MANIFEST.MF
com/gopeed/libgopeed/Libgopeed.class
go/Seq$GoObject.class
go/Seq$GoRef.class
go/Seq$GoRefQueue$1.class
go/Seq$GoRefQueue.class
go/Seq$Proxy.class
go/Seq$Ref.class
go/Seq$RefMap.class
go/Seq$RefTracker.class
go/Seq.class
go/Universe$proxyerror.class
go/Universe.class
go/error.class
```

- `libgojni.so`：43,908,776 B，`ELF64`、`Type: DYN`、`Machine: AArch64`；Go 符号已由 `-w -s` 去除。
- `proguard.txt`：`-keep class go.** { *; }`、`-keep class com.gopeed.** { *; }`（AAR 消费方自动生效，**无需**在 Motrix Lite 再手写 keep 规则）。
- `R.txt` 为空；`res/` 为空目录；AAR 不含任何权限声明（联网/前台服务等权限由 App 侧 Manifest 提供）。
- AAR 压缩包仅 14.72 MB，因为 41.87 MB 的 `.so` 在 zip 内高度压缩；APK 打包时 Gradle 会再压缩（`jniLibs.useLegacyPackaging` 等策略见工程现状）。

---

## 6. Java API（javap / gobind 源码原文）

### 6.1 主类（唯一业务入口）

`javap -classpath classes.jar -public com.gopeed.libgopeed.Libgopeed`：

```java
Compiled from "Libgopeed.java"
public abstract class com.gopeed.libgopeed.Libgopeed {
  public static void touch();
  public static native long start(java.lang.String) throws java.lang.Exception;
  public static native void stop();
}
```

`javap -classpath classes.jar -public -s com.gopeed.libgopeed.Libgopeed`（JNI 描述符）：

```java
public static void touch();
  descriptor: ()V

public static native long start(java.lang.String) throws java.lang.Exception;
  descriptor: (Ljava/lang/String;)J

public static native void stop();
  descriptor: ()V
```

`javap -verbose` 关键行：

```
minor version: 0
major version: 52                 # Java 8 字节码，AGP/JDK17 可直接消费
flags: (0x0421) ACC_PUBLIC, ACC_SUPER, ACC_ABSTRACT
public static native long start(java.lang.String) throws java.lang.Exception;
  flags: (0x0109) ACC_PUBLIC, ACC_STATIC, ACC_NATIVE
  Exceptions: throws java.lang.Exception
public static native void stop();
  flags: (0x0109) ACC_PUBLIC, ACC_STATIC, ACC_NATIVE
```

gobind 生成的 Java 源码（`libgopeed-sources.jar` 内 `com/gopeed/libgopeed/Libgopeed.java`）原文：

```java
// Code generated by gobind. DO NOT EDIT.
package com.gopeed.libgopeed;

import go.Seq;

public abstract class Libgopeed {
	static {
		Seq.touch(); // for loading the native library
		_init();
	}
	private Libgopeed() {} // uninstantiable
	public static void touch() {}
	private static native void _init();
	public static native long start(String cfg) throws Exception;
	public static native void stop();
}
```

### 6.2 方法语义（来自 Gopeed v1.9.3 `bind/mobile/main.go` 与 `pkg/rest`）

| Java 方法 | Go 原函数 | 语义 |
|---|---|---|
| `static native long start(String cfg) throws Exception` | `func Start(cfg string) (int, error)` | 解析 JSON 配置、`ProductionMode` 强制置 true，启动内嵌 REST/下载引擎，**返回监听端口**（Java 用 `long` 承载 Go `int`）；失败抛 `java.lang.Exception`。重复调用幂等：`rest.Start` 检测到已启动时直接返回既有端口 |
| `static native void stop()` | `func Stop()` | `rest.Stop()`：3 秒超时 Shutdown HTTP 服务 + `Downloader.Close()`（同步返回；调用后引擎停止） |
| `static void touch()` | gobind 自动生成 | 仅触发类加载/JNI 初始化，业务代码不需要调用 |

> 结论：**没有** `InvokeAsync`、`SubscribeTaskEvents`、`GetAPIServerState`、`StartAPIServer`、`StopAPIServer`、`RestartAPIServer`；
> AAR 也不导出任何 Java 回调接口/Listener。与 `docs/ENGINE_INTEGRATION_PLAN.md` §4 的描述不一致（文档为设想方案）。集成方案需按 §6.4 用 HTTP REST 实现。

### 6.3 支持类（gobind 运行时，一般无需直接使用）

```
javap -public go.Seq
public class go.Seq {
  public static final go.Seq$Ref nullRef;
  public static void setContext(android.content.Context);
  public static void touch();
  public static void incRefnum(int);
  public static int incRef(java.lang.Object);
  public static int incGoObjectRef(go.Seq$GoObject);
  public static void trackGoRef(int, go.Seq$GoObject);
  public static go.Seq$Ref getRef(int);
  public static native void incGoRef(int, go.Seq$GoObject);
}
javap -public go.Universe
public abstract class go.Universe { public static void touch(); }
javap -public go.error
public interface go.error { public abstract java.lang.String error(); }
```

### 6.4 集成方式（实际可用 API）：start/stop + 内嵌 REST API v1

`start()` 返回端口后，REST 基址为 `http://127.0.0.1:<port>`（默认 `address=127.0.0.1:0` 由系统分配端口）。
Gopeed v1.9.3 `pkg/rest/server.go` 实际路由（完整表见 `.tools\gopeed-build\aar-inspect\gopeed-rest-api-routes.txt`）：

```
GET    /api/v1/info
POST   /api/v1/resolve
POST   /api/v1/tasks                  # 新建任务（返回 task id）
POST   /api/v1/tasks/batch
PATCH  /api/v1/tasks/{id}
PUT    /api/v1/tasks/{id}/pause
PUT    /api/v1/tasks/pause
PUT    /api/v1/tasks/{id}/continue
PUT    /api/v1/tasks/continue
DELETE /api/v1/tasks/{id}
DELETE /api/v1/tasks
GET    /api/v1/tasks/{id}
GET    /api/v1/tasks
GET    /api/v1/tasks/{id}/stats       # 进度/速度轮询
GET    /api/v1/config
PUT    /api/v1/config
POST   /api/v1/extensions ...        # 扩展管理（可不用）
POST   /api/v1/webhook/test
GET/POST /api/v1/proxy
```

- 事件/进度：v1.9.3 **没有** SSE / WebSocket 事件端点，也没有 `SubscribeTaskEvents` 桥；只能通过
  `GET /api/v1/tasks` 与 `GET /api/v1/tasks/{id}/stats` 定时轮询（建议 300~1000 ms），这与集成文档里"事件首选、轮询兜底"的设计需要调整为"REST 轮询"。
- 暂停/恢复/删除：分别用 `PUT /api/v1/tasks/{id}/pause|continue`、`DELETE /api/v1/tasks/{id}`。
- 认证：`StartConfig.apiToken` 非空时请求需带 token（当前 Android 绑定未设置，默认空；可自行在 JSON 中配置并在 Java 侧加 header）。

### 6.5 Start 配置 JSON（`model.StartConfig`，来自 v1.9.3 源码）

| 字段 | 类型 | 默认/说明 |
|---|---|---|
| `network` | string | 默认 `"tcp"` |
| `address` | string | 默认 `"127.0.0.1:0"`（0 = 随机端口，start 返回实际端口） |
| `refreshInterval` | int | 默认 `350` |
| `storage` | string | 默认 `"bolt"`（`"mem"` = 内存存储） |
| `storageDir` | string | 默认 `"./"`，**必须传 App 私有目录**（如 `context.filesDir/gopeed`） |
| `whiteDownloadDirs` | []string | 允许下载的目录白名单（可选） |
| `apiToken` | string | REST Bearer token（可选） |
| `downloadConfig` | object | `base.DownloaderStoreConfig`，常用 `downloadDir`、`maxRunning`、`protocolConfig`、`proxy` 等 |
| `ProductionMode` | bool | **由绑定层强制 `true`**，JSON 里传无效（`bind/mobile/main.go` 中 `config.ProductionMode = true`） |
| `WebEnable` / `WebFS` / `WebAuth` | — | Web UI 相关，mobile 绑定不使用（`json:"-"`/非导出） |

示例（Motrix Lite 侧）：

```json
{
  "network": "tcp",
  "address": "127.0.0.1:0",
  "refreshInterval": 350,
  "storage": "bolt",
  "storageDir": "/data/user/0/<applicationId>/files/gopeed",
  "downloadConfig": {
    "downloadDir": "/data/user/0/<applicationId>/files/staging/gopeed",
    "maxRunning": 5
  }
}
```

对应 Kotlin 调用示例：

```kotlin
// 注意类名是 com.gopeed.libgopeed.Libgopeed，不是 docs 里写的 Mobile/start()
val port = com.gopeed.libgopeed.Libgopeed.start(cfgJson)   // throws Exception；返回 long，需 toInt()
val baseUrl = "http://127.0.0.1:$port"
// ... 轮询 /api/v1/tasks、创建任务等 ...
com.gopeed.libgopeed.Libgopeed.stop()
```

> 首次调用 `start` 会触发 `System.loadLibrary("gojni")`（由 `go.Seq` 静态块完成），确保 AAR 的 `jni/arm64-v8a/libgojni.so` 随 APK 打包。

---

## 7. 与 Motrix Lite 工程的关系

- AAR 已放到 `motrix-lite\app\libs\libgopeed.aar`；`app/build.gradle.kts` 已有：
  `if (file("libs/libgopeed.aar").exists()) { implementation(files("libs/libgopeed.aar")) }`，无需改 Gradle 即可被引用。
- 工程已设置 `abiFilters += listOf("arm64-v8a")` 与 `jniLibs { useLegacyPackaging = true }`，与本 AAR（仅 arm64-v8a）匹配。
- 未修改 `app/src`、Gradle、docs；仅在 `app\libs\` 新增 `libgopeed.aar`。
- AAR 内 `proguard.txt` 会自动 keep `go.**` 与 `com.gopeed.**`；若 Motrix Lite 另有 R8 配置，无需重复。

---

## 8. 遇到的问题与解决

1. **dl.google.com 大文件下载极慢/停滞**：Go zip（87 MB）单线程 curl 卡在 663,552 B（0 KB/s），HEAD 正常。
   → 改用阿里云 Go 镜像（0.45 MB/s 可用）下载同版本官方 zip；NDK 改用腾讯 Android SDK 镜像（约 29 MB/s）。
2. **sdkmanager 安装 NDK 太慢**：`ndk;27.2.12479018` 通过 sdkmanager 下载仅 ~0.4 MB/s（781 MB 预计 30+ 分钟），中途放弃并清理了 `.temp` 与半成品目录。
   → 直接从腾讯镜像下载 `android-ndk-r27c-windows.zip`（781,511,249 B）→ `tar.exe -xf` → `.tools\android-ndk-r27c`，`source.properties` 校验为 `27.2.12479018 / r27c`。
3. **golang.org/x/mobile@latest 要求 Go 1.26**：go.mod 要求 `go 1.24.9`，但 `x/mobile` 最新版 `golang.org/x/mobile v0.0.0-20261011034528-197770444867` 的 go.mod 声明 `go 1.26.0`，`go install`/`go get` 自动触发 `GOTOOLCHAIN=auto` 下载 go1.26.10 工具链（模块缓存内，约 100 MB），成功；实际编译由 go1.26.10 完成（满足 Gopeed 要求的 ≥1.24.9）。
4. **`GOSUMDB=off` 导致工具链下载失败**：`go: download go1.26.10: ... verifying module: checksum database disabled by GOSUMDB=off`；而直连 `sum.golang.org` 超时。
   → 改为 `GOSUMDB=sum.golang.org`，Go 通过 GOPROXY 的 `/sumdb/` 端点校验（`goproxy.cn` 支持，实测 200），工具链与模块校验全部通过；`GOSUMDB=off` 仅适用于无工具链切换的纯离线场景。
5. **默认 GOCACHE 不可写**：`failed to initialize build cache at C:\Users\Mark3\AppData\Local\go-build: mkdir ...: Access is denied`；`gomobile` 也因 `%TEMP%` 不可写报 `mkdir C:\Users\Mark3\AppData\Local\Temp\gomobile-work-...: Access is denied`。
   → 设置 `GOCACHE=...\gocache`、`GOTMPDIR/TMP/TEMP=...\tmp`（均在 .tools 工作区），问题消失。
6. **goproxy.io 对模块 zip 返回 403**：`https://goproxy.io/.../@v/v1.9.3.zip` → 403（与 Lead 提醒一致）。
   → 使用 `https://goproxy.cn` 为主、`https://mirrors.aliyun.com/goproxy` 备用（两者均正常，goproxy.cn 实测 ~4 MB/s）。
7. **PowerShell 原生参数被按 `.` 拆分（首次 bind 失败的直接原因）**：未加引号的 `-javapkg=com.gopeed` 被拆成 `-javapkg=com` 与 `.gopeed` 两个参数，
   gomobile 把 `.gopeed` 当成包路径，gobind 报 `no exported names in the package ".gopeed"`。
   → 给该参数加引号（或用 `cmd.exe /c` 传递整条命令行）；修正后构建成功。日志：`build.log` 第一次失败的 `gobind.exe ... -javapkg=com .gopeed ...`。
8. **新版 `gomobile init` 不再支持 `-ndk` 参数**（`flag provided but not defined: -ndk`）：该版本 `init` 仅支持 `[-openal dir]`。
   → 直接 `gomobile init`（exit=0），NDK 由 `ANDROID_NDK_HOME` 指定（`ndkRoot()` 中 ANDROID_NDK_HOME 优先级最高，并会校验 `meta/platforms.json` 支持 API 21 与 arm64 ABI）。
9. **`gomobile version` 报 "binary is out of date"**：该子命令通过 `go list -f {{.Stale}}` 在**当前目录 module**里检查，独立 module 下天然为 stale。
   → 不影响 bind/build，忽略；实际版本用 `go version -m gomobile.exe` 记录。
10. **旧版 gomobile 的 NDK wrapper `.cmd` 在 Windows 上受 cmd.exe 8191 字符限制**：本版本 gomobile 已内置处理（直接调用 clang.exe + `--target` 参数），无需额外配置。

---

## 9. 可复现性 / 产物清单

构建目录：`C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\`

| 文件/目录 | 说明 |
|---|---|
| `env.ps1` | 全部环境变量设置（见 §4.1），可 dot-source 复用 |
| `build.log` | `gomobile bind -v` 完整日志（30,169 B / 905 行） |
| `build-command.txt` | 实际执行的完整命令行原文 |
| `bind\go.mod` / `bind\go.sum` | 锁定依赖版本与校验和（go.sum 318 行），满足可复现与 GPL 对应源码要求 |
| `bind\libgopeed.aar` | AAR 构建产物（与 motrix-lite/app/libs 中一致，同一 SHA-256） |
| `bind\libgopeed-sources.jar` | gobind 生成的 Java 源码（Libgopeed.java 等） |
| `aar-inspect\` | AAR 解包结果：`AndroidManifest.xml`、`proguard.txt`、`classes.jar`、`jni\arm64-v8a\libgojni.so` |
| `aar-inspect\aar-contents.txt` / `classes-jar-contents.txt` | `jar tf` 列表 |
| `aar-inspect\javap\*.txt` | javap 公开签名/描述符/verbose 输出原文 |
| `aar-inspect\sha256.txt` | 大小与 SHA-256 记录 |
| `aar-inspect\gopeed-rest-api-routes.txt` | v1.9.3 REST 路由表（集成参考） |
| `versions-go.txt` / `versions-gomobile.txt` / `versions-goenv.txt` | 版本证据 |
| `gopath\` | 2 GB 模块缓存（含 go1.26.10 工具链、Gopeed v1.9.3 源码及依赖，可离线重建） |
| `gocache\` | Go 构建缓存（565 MB） |
| `tmp\` | GOTMPDIR/TMP（构建过程临时目录，可清理） |

重新构建（网络可用时，缓存已热）：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
. C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\env.ps1
Set-Location C:\Users\Mark3\Documents\deepseek-harness\default-workspace\.tools\gopeed-build\bind
& "$env:GOBIN\gomobile.exe" bind -tags nosqlite -ldflags="-w -s -checklinkname=0 -X github.com/GopeedLab/gopeed/pkg/base.Version=v1.9.3" `
  -o libgopeed.aar -target=android/arm64 -androidapi 21 -javapkg=com.gopeed github.com/GopeedLab/gopeed/bind/mobile
```

> 注意：PowerShell 下 `-javapkg=com.gopeed`、`-ldflags=...` 等含 `.` 的参数**必须加引号**，否则会被按 `.` 拆成两个参数（见 §8.7）。

---

## 10. 警告 / 风险 / 后续建议

1. **API 与文档预期不一致（最重要）**：AAR 只有 `start`/`stop`，没有 `InvokeAsync`/`SubscribeTaskEvents`/`GetAPIServerState` 等。
   Motrix Lite 的 `GopeedEngineController` 需按 §6.4/§6.5 用 REST 轮询实现，事件订阅需以轮询兜底。若必须用事件回调，需要修改
   Gopeed `bind/mobile` 源码并重新构建（超出本次"与官方命令一致"的范围）。
2. **GPLv3 合规**：Gopeed 为 GPL-3.0，AAR 静态链接进 APK 属于结合作品。构建记录（本报告、`env.ps1`、`build-command.txt`、
   `bind\go.mod`/`go.sum`、模块缓存中的 Gopeed 源码）应随发布物保留/公开；最终 App 需按 GPLv3 开源（与 LICENSE 分析文档一致）。
3. **NDK 来源校验**：NDK zip 来自腾讯镜像而非 dl.google.com 直连，虽长度与内容版本校验一致，建议后续网络允许时用 Google 官方
   `repository2-3.xml` 的 SHA-1 复核 zip 完整性（本次未做，zip 已删除）。
4. **gomobile/x-mobile 版本为"最新"**：与 Gopeed CI 当时的 `@latest` 等价，但非固定版本；构建记录中已锁定
   `golang.org/x/mobile v0.0.0-20261011034528-197770444867` 与 go1.26.10，重装请按该版本复现。
5. **体积**：AAR 14.72 MB（内含 41.87 MB `.so`），单 arm64；APK 预计增加 ~15-18 MB。若需进一步控制体积可考虑拆 ABI 或 UPX（不建议，Go 静态库不便处理）。
6. `-checklinkname=0` 是官方命令的一部分，但会放宽链接器 `//go:linkname` 检查；上游升级 Go 后可评估移除。
7. 首次 `start()` 若在 Android 主线程调用，JSON 解析+引擎初始化可能耗时（<100ms 量级），建议仍放到后台线程/协程。

---

*报告生成：2026-11-05，构建执行：gopeed-builder agent；原始日志与产物见 `.tools\gopeed-build\`。*
