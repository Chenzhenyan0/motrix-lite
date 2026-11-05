# 构建与签名指南（BUILD AND SIGN）

本文说明如何在本地构建 Motrix Lite、如何运行单元测试、如何生成签名密钥并验证 APK。命令同时给出 Windows（PowerShell / `gradlew.bat`）与 macOS/Linux 两种写法。

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
| versionCode / versionName | **1 / 1.0.0** | 同上 |
| Compose BOM | 2024.12.01（Material3） | `app/build.gradle.kts` |
| OkHttp | 4.12.0 | 同上 |
| coroutines | 1.9.0 | 同上 |

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
# Debug
.\gradlew.bat :app:assembleDebug

# Release（R8 压缩 + 签名）
.\gradlew.bat :app:assembleRelease

# 单元测试
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

### 3.4 产物位置

| 构建类型 | 路径 | 本机实测大小 |
| --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 10,730,630 字节（约 10.23 MB） |
| Release | `app/build/outputs/apk/release/app-release.apk` | 1,477,909 字节（约 1.41 MB） |

`dist/` 目录中是构建完成后复制出的分发副本：

```
dist/MotrixLite-debug.apk
dist/MotrixLite-release.apk
dist/SHA256SUMS.txt
```

当前 `SHA256SUMS.txt` 内容（已在本机重新计算核对一致）：

```
349612085d362b5130b537e012cf6e14334d0bbcbb3a1c249b4b7133adcd2819  MotrixLite-debug.apk  (10.23 MB)
cd5872c1f5e7f203aa304017105ed2e8d2bc48fcc6da195ad41334b3e40196e1  MotrixLite-release.apk  (1.41 MB)
```

校验命令：

```powershell
Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256
```

```bash
shasum -a 256 dist/MotrixLite-release.apk
```

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
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes SourceFile,LineNumberTable
```

说明：

- 任务 JSON 由 `org.json` 手工读写（**无反射**），因此 `-keep` model 类只是保守做法，去掉也能工作；
- `-dontwarn` 覆盖 OkHttp 在 Android 上可选但未打包的 TLS 提供者（Conscrypt/BouncyCastle/OpenJSSE）；
- Release 包 1.41 MB，明显小于 debug 的 10.23 MB。注意两者不完全可比：debug 包额外包含 `ui-tooling`、`ui-test-manifest` 等仅调试依赖，且不经过 R8 裁剪。

**常见 R8 报错与处理**：

| 报错 | 处理 |
| --- | --- |
| `Missing class okhttp3.internal.platform...` | 已有 `-dontwarn okhttp3.**`；若新增库再补 `-dontwarn` |
| `Missing class org.bouncycastle.**` | 同上，属可选依赖 |
| 运行期 `ClassNotFoundException` / `NoSuchMethodException` | 说明被裁剪，按类名加 `-keep class <包>.** { *; }` |
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
# 或
adb install -r dist\MotrixLite-release.apk
```

覆盖安装签名不一致时会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先卸载：

```powershell
adb uninstall com.motrixlite.downloader
```

查看日志：

```powershell
adb logcat -s DownloadService:V AndroidRuntime:E
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
| `TaskJsonCodecTest` | 2 | 通过 |
| `HttpProbeTest` | 4 | 通过（MockWebServer） |
| `TaskDownloaderTest` | 3 | 通过（MockWebServer） |
| **合计** | **25** | **全部通过** |

`TaskDownloaderTest` 覆盖：多段下载字节完全一致、暂停后续传字节一致、服务器忽略 Range 时降级单连接。详见 [docs/TEST_CHECKLIST.md](TEST_CHECKLIST.md)。

---

## 八、构建故障排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `SDK location not found` | 未设置 `sdk.dir` / `ANDROID_HOME` | 见 3.2 第 4 步 |
| `Failed to find target with hash string 'android-35'` | 未装 Platform 35 | `sdkmanager "platforms;android-35"` |
| `Could not install Gradle distribution` / 下载超时 | 网络 | 换镜像或离线放置发行版（4.1） |
| `Unsupported class file major version 68` | 使用了 JDK 24 等更高版本 | 切换 Gradle JDK 到 17 |
| `Android Gradle plugin requires Java 17` | Gradle JDK 为 11 | 切换为 17 |
| `Cannot locate symbol` / NDK 相关报错 | 本工程无 native 代码 | 无需安装 NDK，检查是否误加了依赖 |
| `SigningConfig "release" not found` | `keystore.properties` 缺失但代码强引用 | 本工程已做存在性判断；若自行改动需同步修改 |
| `lintVitalRelease` 失败 | 新增代码触发致命 lint | 按报告修复，或（不推荐）在 `android.lint` 中关闭对应规则 |
| 构建内存不足 / `OutOfMemoryError` | 默认 4 GB 堆偏小（大工程） | 调大 `org.gradle.jvmargs` |
| Windows 中文乱码 | 控制台编码 | `chcp 65001`，`gradle.properties` 已设置 `-Dfile.encoding=UTF-8` |

---

## 九、版本发布检查清单

- [ ] `versionCode` 递增、`versionName` 更新（`app/build.gradle.kts`）
- [ ] `keystore.properties` 指向正式密钥库，且**未**提交到仓库
- [ ] `.\gradlew.bat clean :app:testDebugUnitTest :app:assembleRelease` 全绿
- [ ] `apksigner verify --print-certs` 显示正式证书
- [ ] 复制 APK 到 `dist/` 并更新 `SHA256SUMS.txt`
- [ ] 更新 `README.md` 中的验证状态与 `docs/KNOWN_ISSUES.md`
- [ ] 附上 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`

---

## 十、本机验证状态（诚实记录）

| 项 | 结果 | 说明 |
| --- | --- | --- |
| Windows Debug 构建 | ✅ 成功 | `app-debug.apk`，10,730,630 字节 |
| Windows Release 构建 | ✅ 成功 | `app-release.apk`，1,477,909 字节，使用演示自签名证书 |
| 单元测试 | ✅ 25/25 通过 | 7 个测试类，含 MockWebServer 实测 |
| `lintVitalRelease` | ✅ 通过 | 无致命问题 |
| 真机安装/运行 | ❌ **未验证** | 本环境无 Android 设备或模拟器 |

> 本环境的构建验证只覆盖"能编译、能过测试、能签名"，**不包含真机功能验收**。功能验收请按 [docs/TEST_CHECKLIST.md](TEST_CHECKLIST.md) 在真机上执行并填写结果。
