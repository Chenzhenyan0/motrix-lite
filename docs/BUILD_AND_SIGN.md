# 构建与签名指南（BUILD AND SIGN）

本文说明如何在本地构建 Motrix Lite 1.1.1（三引擎）、如何生成/放入两个原生内核产物、如何运行单元测试、如何生成签名密钥并验证 APK。命令同时给出 Windows（PowerShell / `gradlew.bat`）与 macOS/Linux 两种写法。

> 1.1.1 同时包含 GPL 组件（Gopeed GPL-3.0、aria2 1.35.0 GPL-2.0-or-later；aria2 二进制静态链接 **OpenSSL 1.1.1d**，OpenSSL/SSLeay 双许可证适用 aria2 的 `LICENSE.OpenSSL` 链接例外），**整体按 GPLv3 分发**。构建与发布前请阅读 [COMPLIANCE.md](COMPLIANCE.md)。

---

## 一、工具链版本（与仓库一致）

| 项 | 版本 | 来源 |
| --- | --- | --- |
| JDK | **17**（构建验证使用 Microsoft OpenJDK 17.0.20） | `compileOptions`/`kotlinOptions` 均为 17 |
| Gradle | **8.9** | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | **8.7.3** | `build.gradle.kts` |
| Kotlin | **2.0.21**（含 `org.jetbrains.kotlin.plugin.compose`） | 同上 |
| compileSdk / targetSdk | **35** | `app/build.gradle.kts` |
| minSdk | **26**（Android 8.0） | 同上 |
| versionCode / versionName | **3 / 1.1.1** | 同上 |
| ABI | **仅 arm64-v8a**（`abiFilters`） | 同上 |
| Compose BOM | 2024.12.01（Material3） | `app/build.gradle.kts` |
| OkHttp | 4.12.0 | 同上 |
| coroutines | 1.9.0 | 同上 |
| Go（Gopeed AAR 安装主版本） | **1.24.11** | `native/gopeed/build.ps1` |
| gomobile 实际编译工具链 | **go1.26.10**（`GOTOOLCHAIN=auto` 自动下载；`golang.org/x/mobile v0.0.0-20261011034528-197770444867` 要求 Go ≥ 1.26） | `.tools/gopeed-build/BUILD_REPORT.md` |
| Android NDK（仅构建 Gopeed AAR） | **27.2.12479018（r27c）** | 同上 |

> **JDK 必须是 17。** 用 JDK 21/24 构建时 AGP 8.7 通常仍可运行，但可能报 `Unsupported class file major version`；用 JDK 11 会因 AGP 要求而失败。

---

## 二、方式 A：Android Studio 直接打开

1. 安装 **Android Studio**（建议 Ladybug 2024.2.1 或更新版本，内置 AGP 8.7 支持）。
2. `File → Open`，选择工程根目录（包含 `settings.gradle.kts` 的目录，即 `motrix-lite/`）。
3. 等待 Gradle Sync。首次同步会：
   - 下载 Gradle 8.9 发行版；
   - 下载 AGP、Kotlin、Compose、AndroidX、OkHttp 等依赖。
4. 确认 **Gradle JDK = 17**：`Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`，选择 17（或 `jbr-17`）。
5. 确认 **SDK Platform 35** 已安装：`Settings → Languages & Frameworks → Android SDK`，勾选：
   - `Android SDK Platform 35`
   - `Android SDK Build-Tools 35.0.0`
   - `Android SDK Platform-Tools`
6. 运行：
   - 顶部设备下拉框选择真机（开启 USB 调试）或模拟器；
   - 点击 ▶ Run `app`，或菜单 `Build → Build Bundle(s)/APK(s) → Build APK(s)`。
7. 产物：`app/build/outputs/apk/debug/app-debug.apk`。

> Android Studio 会自动生成/维护 `local.properties`（`sdk.dir=...`）。该文件已被 `.gitignore` 忽略，不应提交。

---

## 三、方式 B：命令行（Windows）

### 3.1 安装 JDK 17

任选其一：

- Microsoft Build of OpenJDK 17：https://learn.microsoft.com/java/openjdk/download
- Eclipse Temurin 17：https://adoptium.net/temurin/releases/?version=17

安装后确认：

```powershell
java -version
# openjdk version "17.0.x" ...
```

设置（当前会话临时生效；永久生效请用"系统属性 → 环境变量"）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### 3.2 安装 Android SDK 命令行工具

1. 下载 **Command line tools only**：https://developer.android.com/studio#command-line-tools-only
2. 解压到例如 `C:\Android\sdk\cmdline-tools\latest\`（目录结构必须是 `cmdline-tools\latest\bin\sdkmanager.bat`）。
3. 安装所需组件：

```powershell
$env:ANDROID_HOME = "C:\Android\sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"

sdkmanager --version
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses     # 逐项输入 y 接受
```

4. 告诉构建系统 SDK 位置，二选一：

   - 环境变量（推荐）：
     ```powershell
     $env:ANDROID_HOME = "C:\Android\sdk"
     ```
   - 或工程根目录 `local.properties`（**Windows 路径需转义反斜杠**）。仓库内提供了示例文件 `local.properties.example`，复制后改路径即可：
     ```powershell
     Copy-Item local.properties.example local.properties
     ```
     ```properties
     sdk.dir=C\:\\Android\\sdk
     ```
     也可以直接用正斜杠，避免转义问题：
     ```properties
     sdk.dir=C:/Android/sdk
     ```

### 3.3 构建

在工程根目录（`motrix-lite/`）执行：

```powershell
# （可选，1.1.1）先放入两个原生内核产物；不放也能编译，只是设置页显示不可用
powershell -ExecutionPolicy Bypass -File native/gopeed/build.ps1
powershell -ExecutionPolicy Bypass -File native/aria2/fetch.ps1

# Debug
.\gradlew.bat :app:assembleDebug

# Release（R8 压缩 + 签名）
.\gradlew.bat :app:assembleRelease

# 单元测试（32 个用例，9 个测试类）
.\gradlew.bat :app:testDebugUnitTest

# Release Lint（构建 release 时会自动跑 lintVitalRelease）
.\gradlew.bat :app:lintVitalRelease

# 清理
.\gradlew.bat clean
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintVitalRelease
./gradlew clean
```

> `native/gopeed/build.ps1` 与 `native/aria2/fetch.ps1` 为 Windows PowerShell 脚本；Gopeed AAR 的 gomobile 构建需要 Linux/macOS/Windows + Go + NDK（脚本已固定版本）。aria2 脚本只做下载/校验/复制。

### 3.4 产物位置与体积（v1.1.1 最终构建）

v1.1.1 已在本机完成 `:app:assembleDebug` 与 `:app:assembleRelease`（release 经 R8 + 演示签名、`zipalign -P 16` 通过），两个原生产物（`libgopeed.aar`、`libaria2c.so`）均已放入并打包进 APK：

| 构建类型 | 路径 | 大小 | SHA-256（实际计算） |
| --- | --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | **29,358,263 B（28.00 MB）** | `583b6846ff23fe2a68fb24b74038955ce1ea1b1bf313fbaed1c1ce8abf611bc4` |
| Release | `app/build/outputs/apk/release/app-release.apk` | **18,771,902 B（17.90 MB）** | `a8b5be56898a8bc40f67e5158263c988f9c602067daa6fbcfb04fd911cbd2d5d` |
| 分发副本 | `dist/MotrixLite-debug.apk` | 29,358,263 B | 同 Debug 产物 |
| 分发副本 | `dist/MotrixLite-release.apk` | 18,771,902 B | 同 Release 产物 |

`dist/SHA256SUMS.txt` 已按上述实际文件生成，可用以下命令复核：

```powershell
Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256
Get-FileHash dist\MotrixLite-debug.apk -Algorithm SHA256
```

```bash
shasum -a 256 dist/MotrixLite-release.apk dist/MotrixLite-debug.apk
```

> **APK 内容已核验**：两个 APK 都包含 `lib/arm64-v8a/libaria2c.so`（**1.35.0**，5,872,304 B）、`lib/arm64-v8a/libgojni.so`（43,908,776 B）以及 `assets/licenses/` 的 **17 个文件**（GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 1.1.1d 的 OpenSSL+SSLeay 许可证、aria2 OpenSSL 例外、Apache-2.0、各静态依赖许可证、DEPENDENCY-VERSIONS、SOURCE-OFFER、THIRD_PARTY_NOTICES、README、devgianlu notice）。release 小于 debug 是因为 R8 压缩/资源裁剪且不含 `ui-tooling` 等调试依赖。
>
> **最终 rebuild 已完成**：上表大小与 SHA-256 均为 `dist/` 实测值（与 `dist/SHA256SUMS.txt` 一致；两个 APK 均已解包核验）。
>
> 1.0.0 历史数据（仅供参考）：Debug 10,730,630 B；Release 1,477,909 B。v1.1.0 的 APK 基线已被 v1.1.1 取代，历史值以 GitHub Release 为准，不在本文固定。

---

## 四、网络受限环境的处理

### 4.1 Gradle 8.9 发行版下载慢或失败

`gradle/wrapper/gradle-wrapper.properties` 默认指向官方地址：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=60000
```

可临时改为国内镜像（**注意镜像必须提供完全相同的文件名**）：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

或手动下载 `gradle-8.9-bin.zip`，放入：

```
%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\<随机目录>\
```

重新执行 `gradlew.bat` 即可复用（无需联网）。

### 4.2 依赖下载慢（Maven）

`settings.gradle.kts` 使用 `google()` + `mavenCentral()`。如需镜像，可在 `pluginManagement.repositories` 与 `dependencyResolutionManagement.repositories` 的最前面加入（示例）：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

> 修改仓库配置属于构建环境调整，请只在本机生效或使用 `~/.gradle/init.gradle.kts`，不要提交到仓库。

### 4.3 完全离线构建

依赖已缓存后可加 `--offline`：

```powershell
.\gradlew.bat --offline :app:assembleDebug
```

若本机已安装独立 Gradle 8.9，也可绕过 wrapper：

```powershell
& "C:\Gradle\gradle-8.9\bin\gradle.bat" :app:assembleDebug
```

### 4.4 原生内核工具链的网络处理

| 资源 | 下载地址/镜像 | 说明 |
| --- | --- | --- |
| Go 1.24.11 | `https://dl.google.com/go/go1.24.11.windows-amd64.zip` | `native/gopeed/build.ps1` 固定版本（也可用其它可达镜像手动放入 `downloads/`） |
| Android NDK 27.2.12479018 | `https://dl.google.com/android/repository/android-ndk-r27c-windows.zip`（或 sdkmanager） | 脚本优先用 `sdkmanager`，否则下载 r27c 压缩包 |
| Go modules | `GOPROXY=https://goproxy.cn,https://mirrors.aliyun.com/goproxy,direct` | 脚本已设置；`proxy.golang.org` 在部分网络不可达 |
| aria2 二进制 | `https://gh-proxy.com/https://github.com/devgianlu/aria2-android/releases/download/v1.35.0/aria2c-arm64-v8a` | 可用 `-Mirror` 更换代理；下载后校验大小 5,872,304 字节与 SHA-256 |

> Gopeed AAR 构建需约 0.9 GB 工具链下载；aria2 只需下载约 5.87 MB 二进制。两者均可跳过——不放入产物时 APK 仍可编译运行。
>
> **v1.1.1 的 Gopeed 构建实际情况**：安装的 Go 主版本为 1.24.11，但 `golang.org/x/mobile@latest` 要求 Go ≥ 1.26，`GOTOOLCHAIN=auto` 自动下载并使用 **go1.26.10** 完成编译；NDK 为 27.2.12479018（r27c，clang 18.0.3）；AAR 产物 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`。完整过程见 `.tools/gopeed-build/BUILD_REPORT.md`（不提交仓库）。新版 `gomobile init` 不再接受 `-ndk` 参数，NDK 位置由 `ANDROID_NDK_HOME` 指定。

---

## 五、R8 与 ProGuard

Release 构建开启：

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

`app/proguard-rules.pro` 现有规则：

```proguard
-keep class com.motrixlite.downloader.data.model.** { *; }
# Gopeed gomobile AAR 通过反射加载（实际类 com.gopeed.libgopeed.Libgopeed）
-keep class com.gopeed.** { *; }
-keep class libgopeed.** { *; }
-keep class go.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.gopeed.**
-dontwarn go.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes SourceFile,LineNumberTable
```

说明：

- 任务 JSON 由 `org.json` 手工读写（**无反射**），因此 `-keep` model 类只是保守做法，去掉也能工作；
- **Gopeed AAR 通过反射调用，实际 Java 入口类为 `com.gopeed.libgopeed.Libgopeed`**（`GopeedBridge` 的候选列表包含它）；AAR 自带 `proguard.txt`（`-keep class go.**`、`-keep class com.gopeed.**`）会由 AGP 自动应用，本工程又在 `proguard-rules.pro` 中显式保留 `com.gopeed.**` / `libgopeed.**` / `go.**` 与 native 方法，避免 release 包运行期 `ClassNotFoundException` / `UnsatisfiedLinkError`；
- AAR 只导出 `start`/`stop`，没有事件/InvokeAsync 桥，因此 Controller 通过 REST 轮询完成全部任务控制；
- `-dontwarn` 覆盖 OkHttp 在 Android 上可选但未打包的 TLS 提供者（Conscrypt/BouncyCastle/OpenJSSE）；
- v1.1.1 最终包：debug 29,358,263 B（28.00 MB）/ release 18,771,902 B（17.90 MB）；release 因 R8 明显小于 debug，整体也显著小于 v1.1.0（aria2 二进制 69.1 MB → 5.87 MB）。debug 额外包含 `ui-tooling`、`ui-test-manifest` 等调试依赖，且不经过 R8 裁剪，两者不完全可比。

**常见 R8 报错与处理**：

| 报错 | 处理 |
| --- | --- |
| `Missing class okhttp3.internal.platform...` | 已有 `-dontwarn okhttp3.**`；若新增库再补 `-dontwarn` |
| `Missing class org.bouncycastle.**` | 同上，属可选依赖 |
| 运行期 `ClassNotFoundException: com.gopeed...` | 确认 `-keep class com.gopeed.**`；检查 AAR 是否真的打包进 APK |
| 运行期 `NoSuchMethodException`（反射 start/stop） | 确认 AAR 版本/包名候选与 `GopeedBridge` 一致；不要混淆 gomobile 生成的类 |
| 反射读取枚举/JSON 字段失败 | 本工程不使用反射序列化；如未来引入 Gson/Moshi，需保留对应 model 与泛型签名 |
| `R8: java.lang.OutOfMemoryError` | 提高 `gradle.properties` 的 `org.gradle.jvmargs`（当前 4096m） |

定位裁剪问题时，可临时把 `app/build.gradle.kts` 中 `release` 的 `isMinifyEnabled` 改为 `false` 做一次对比构建，确认是否为混淆/裁剪导致（**仅用于定位，不要用于发布**）。

---

## 六、签名

### 6.1 当前签名配置

`app/build.gradle.kts` 在 **`keystore.properties` 存在时**读取并创建 `release` 签名配置；不存在时回退到 **debug 签名**（可构建，但**不能用于发布**）。

`keystore.properties`（仓库内为演示配置，已被 `.gitignore` 忽略）：

```properties
storeFile=keystore/release.keystore
storePassword=motrixlite2026
keyAlias=motrixlite
keyPassword=motrixlite2026
```

`keystore/release.keystore` 为**演示用自签名证书**（PKCS12）：

| 项 | 值 |
| --- | --- |
| 别名 | `motrixlite` |
| 类型 | PKCS12 |
| 生成日期 | 2026-11-05 |
| 证书 SHA-256 | `30:B8:4F:AE:D6:F0:33:C5:27:4A:FA:D0:3D:09:7B:EB:F3:A9:81:27:D6:49:2E:F3:60:BB:32:14:90:3B:A0:FC` |

> ⚠️ **演示证书不能上架。** 私钥与口令已随仓库公开，等同于无保护。正式发布前必须生成自己的密钥库并替换 `keystore.properties`，同时把新密钥库排除出版本控制。

### 6.2 生成新的发布密钥

```powershell
keytool -genkeypair -v `
  -keystore keystore\release.keystore `
  -alias motrixlite `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype PKCS12 `
  -storepass "你的强口令" -keypass "你的强口令" `
  -dname "CN=Your Name, OU=Mobile, O=Your Org, L=Beijing, ST=Beijing, C=CN"
```

macOS / Linux 把反引号续行改为 `\`，路径改为 `keystore/release.keystore`。

查看指纹与别名：

```powershell
keytool -list -v -keystore keystore\release.keystore -storepass "你的强口令"
```

### 6.3 `keystore.properties` 字段说明

| 字段 | 含义 | 注意 |
| --- | --- | --- |
| `storeFile` | 密钥库相对工程根目录的路径 | 用 `/` 或转义 `\\` |
| `storePassword` | 密钥库口令 | 不要提交到公开仓库 |
| `keyAlias` | 密钥别名 | 必须与 `keytool -list` 输出一致 |
| `keyPassword` | 密钥口令 | PKCS12 通常与 `storePassword` 相同 |

**CI/多人协作建议**：不要提交 `keystore.properties`，改用环境变量注入（例如在 CI 中由 secret 生成该文件），并把 `keystore/*.keystore` 保留在 `.gitignore` 中。

### 6.4 验证签名

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

预期输出包含：

```
Verifies
Verified using v1 scheme (JAR signing): true/false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Signer #1 certificate DN: CN=...
```

也可以确认是否误用了 debug 证书：

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk |
  Select-String "certificate DN"
```

若输出为 `CN=Android Debug`，说明 `keystore.properties` 缺失，走了 debug 签名回退。

### 6.5 安装到设备

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
# 或使用 dist/ 中的最终发布副本
adb install -r dist\MotrixLite-release.apk
```

> 1.1.1 仅支持 **arm64-v8a** 设备；x86 模拟器/wear 等无法安装。当前最终 APK 已在 Android 16（Redmi Turbo 4 Pro）上抽查运行。

覆盖安装签名不一致时会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先卸载：

```powershell
adb uninstall com.motrixlite.downloader
```

查看日志（含两个原生引擎）：

```powershell
adb logcat -s DownloadService:V Aria2Engine:W GopeedEngine:W AndroidRuntime:E
```

---

## 七、测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

报告位置：

```
app/build/reports/tests/testDebugUnitTest/index.html
app/build/test-results/testDebugUnitTest/*.xml
```

本机实测（Windows，JDK 17.0.20）：

| 测试类 | 用例数 | 结果 |
| --- | --- | --- |
| `FormattersTest` | 3 | 通过 |
| `FileNameUtilsTest` | 6 | 通过 |
| `SegmentPlannerTest` | 4 | 通过 |
| `RateLimiterTest` | 3 | 通过 |
| `TaskJsonCodecTest` | 3 | 通过（新增 engine / 旧 v1 兼容用例） |
| `HttpProbeTest` | 4 | 通过（MockWebServer） |
| `TaskDownloaderTest` | 3 | 通过（MockWebServer） |
| `Aria2RpcClientTest` | 3 | 通过（MockWebServer；1.1.0 引入） |
| `GopeedApiTest` | 3 | 通过（MockWebServer；1.1.0 引入） |
| **合计** | **32** | **全部通过（9 个测试类）** |

`TaskDownloaderTest` 覆盖：多段下载字节完全一致、暂停后续传字节一致、服务器忽略 Range 时降级单连接。`TaskJsonCodecTest` 覆盖 schema v2 字段往返、运行期字段丢弃、旧 v1 默认 `BUILTIN`、空/畸形数据。`Aria2RpcClientTest` 覆盖 **`token:<secret>`**/URL/options、`tellStatus` 解析与错误映射、`changeGlobalOption` 仅 OK 生效；`GopeedApiTest` 覆盖 `createTask` 的 token/请求体、任务列表与 pause PUT、非零 envelope 抛错。

> 注意：两个原生引擎的 **RPC/REST 客户端协议层**已有 MockWebServer 单测覆盖；但 `Aria2EngineController` / `GopeedEngineController` 的进程、AAR、文件发布与回收逻辑仍无 JVM 单测（依赖 Android Context / 子进程 / AAR），需依赖真机抽查/后续验收。不能把 32/32 理解为"双内核已完成完整真机验收"。

详见 [docs/TEST_CHECKLIST.md](TEST_CHECKLIST.md)。

---

## 八、构建故障排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `SDK location not found` | 未设置 `sdk.dir` / `ANDROID_HOME` | 见 3.2 第 4 步 |
| `Failed to find target with hash string 'android-35'` | 未装 Platform 35 | `sdkmanager "platforms;android-35"` |
| `Could not install Gradle distribution` / 下载超时 | 网络 | 换镜像或离线放置发行版（4.1） |
| `Unsupported class file major version 68` | 使用了 JDK 24 等更高版本 | 切换 Gradle JDK 到 17 |
| `Android Gradle plugin requires Java 17` | Gradle JDK 为 11 | 切换为 17 |
| 设置页显示 Gopeed/aria2 不可用 | 未放入 `libgopeed.aar` / `libaria2c.so`，或 AAR 未打包 | 运行 `native/gopeed/build.ps1`、`native/aria2/fetch.ps1` 后重新构建；检查 `app/build.gradle.kts` 是否检测到 AAR |
| 运行期 `ClassNotFoundException: com.gopeed...` | R8 裁剪了 AAR 类 | 确认 `proguard-rules.pro` 的 `-keep class com.gopeed.**`；检查 release 是否真的打进 AAR |
| `aria2 内核未打包进当前 APK` 或 `exec` 失败 | 二进制未放入 `jniLibs/arm64-v8a/`，或 `useLegacyPackaging` 被改回 false | 重新获取二进制并确认 Gradle 配置；只支持 arm64-v8a 设备 |
| `OSSL_PROVIDER_load 'legacy' failed` / aria2 code 7（Android 16） | 使用了 v1.1.0 的 aria2 1.37.0（OpenSSL 3.0.0，缺 legacy provider） | 改用 aria2 **1.35.0**（OpenSSL 1.1.1d）：`native/aria2/fetch.ps1` 已固定 1.35.0/5,872,304 B/SHA-256；v1.1.1 已修复 |
| `aria2c RPC 启动超时` | 端口/参数/ABI 不匹配，或二进制被裁剪 | 查看 `staging/engines/aria2/aria2.log`；确认设备 ABI 与二进制一致 |
| Gopeed 构建失败（gomobile） | Go/NDK/GOPROXY 网络问题；新版 gomobile 不再支持 `init -ndk` | 按 `native/gopeed/build.ps1` 的版本与镜像重试；NDK 用 `ANDROID_NDK_HOME` 指定；`golang.org/x/mobile@latest` 需 Go ≥ 1.26（`GOTOOLCHAIN=auto` 下载 go1.26.10） |
| `SigningConfig "release" not found` | `keystore.properties` 缺失但代码强引用 | 本工程已做存在性判断；若自行改动需同步修改 |
| `lintVitalRelease` 失败 | 新增代码触发致命 lint | 按报告修复，或（不推荐）在 `android.lint` 中关闭对应规则 |
| 构建内存不足 / `OutOfMemoryError` | 默认 4 GB 堆偏小（大工程） | 调大 `org.gradle.jvmargs` |
| Windows 中文乱码 | 控制台编码 | `chcp 65001`，`gradle.properties` 已设置 `-Dfile.encoding=UTF-8` |

---

## 九、版本发布检查清单

- [ ] `versionCode` 递增、`versionName` 更新（当前 **3 / 1.1.1**）
- [ ] 放入原生产物并记录版本/哈希：`app/libs/libgopeed.aar`（Gopeed v1.9.3，15,438,987 B，SHA-256 `aa064e34…dd2d7c`）、`app/src/main/jniLibs/arm64-v8a/libaria2c.so`（aria2 1.35.0，5,872,304 B，SHA-256 `88b8b5fe…bd1a7f`）
- [ ] `keystore.properties` 指向正式密钥库，且**未**提交到仓库
- [ ] `.\gradlew.bat clean :app:testDebugUnitTest :app:assembleRelease` 全绿
- [ ] `apksigner verify --print-certs` 显示正式证书（当前为演示证书，发布前必须替换）
- [ ] 复制 APK 到 `dist/` 并更新 `SHA256SUMS.txt`（v1.1.1 已生成：debug 29,358,263 B / release 18,771,902 B）
- [ ] APK `assets/licenses/` 含 17 个许可证文件（GPLv3/GPLv2/aria2 COPYING/Gopeed/OpenSSL 1.1.1d/aria2 OpenSSL 例外/Apache-2.0/各静态依赖/DEPENDENCY-VERSIONS/SOURCE-OFFER/THIRD_PARTY_NOTICES/README/devgianlu notice）
- [ ] Release 附源码归档（Gopeed/aria2 对应源码或获取方式、构建脚本）与书面要约
- [ ] v1.1.0 历史 Release 说明补注“Android 16 上 aria2 内核启动失败，已被 v1.1.1 取代”
- [ ] 更新 `README.md` 验证状态、`docs/KNOWN_ISSUES.md`、`docs/TEST_CHECKLIST.md` 结果
- [ ] 附上 `LICENSE`（GPLv3）与 `THIRD_PARTY_NOTICES.md`

---

## 十、本机验证状态（诚实记录）

| 项 | 结果 | 说明 |
| --- | --- | --- |
| Windows Debug 构建（v1.1.1，含两原生内核） | ✅ 成功 | `app-debug.apk` 29,358,263 B（28.00 MB），已含 `libaria2c.so` / `libgojni.so` |
| Windows Release 构建（v1.1.1，R8 + 演示签名） | ✅ 成功 | `app-release.apk` 18,771,902 B（17.90 MB）；`zipalign -P 16` 通过 |
| 单元测试 | ✅ 32/32 通过 | 9 个测试类，含 MockWebServer 实测；原生引擎**控制器**仍无 JVM 单测 |
| APK SHA-256 | ✅ 已计算 | debug `583b6846…f611bc4`；release `a8b5be56…cbd2d5d`（完整值见 3.4） |
| 真机抽查（Android 16） | ✅ 部分通过 | 内置/Gopeed/aria2 50MB 下载、引擎切换、aria2 锁屏 10 分钟与进程回收等；范围见 README §10.2 |
| 完整真机验收（A1–A7/B/E1–E8） | ❌ **未完成** | 见 [docs/TEST_CHECKLIST.md](TEST_CHECKLIST.md) |

> 本环境验证覆盖"能编译、能过 32 个 JVM 测试、能签名、APK 内原生产物与许可证齐全，并在 Android 16 真机上完成部分抽查"；**不包含完整真机验收**。完整验收请按 [docs/TEST_CHECKLIST.md](TEST_CHECKLIST.md) 执行并填写结果。
