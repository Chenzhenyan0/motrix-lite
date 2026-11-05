# 第三方组件与许可声明（THIRD PARTY NOTICES）

本文件列出 Motrix Lite Android 应用（版本 **1.1.1**，versionCode 3，包名 `com.motrixlite.downloader`）所使用的第三方开源组件、许可证类型，以及是否随 APK 分发。

- 本工程整体按 **GNU GPLv3** 分发，见 [LICENSE](LICENSE)；第三方组件各自保留其原许可证。
- 依赖版本以 `app/build.gradle.kts`、`native/gopeed/build.ps1`、`native/aria2/fetch.ps1` 为准（唯一事实来源）。
- 完整许可证文本随最终 APK 打包在 `app/src/main/assets/licenses/`（17 个文件），并同时存放在仓库 `licenses/` 与 `native/licenses/`。
- 本文档只做许可信息归集，**不构成法律意见**；涉及再分发、商用或上架时请自行复核许可证原文。

> 版本提示：**v1.1.0** 曾使用 devgianlu aria2 1.37.0（静态链接 OpenSSL 3.0.0），在 Redmi Turbo 4 Pro / Android 16 上启动失败（`OSSL_PROVIDER_load 'legacy' failed`），已被 **v1.1.1 取代**。v1.1.1 改用 aria2 1.35.0（静态链接 OpenSSL 1.1.1d，适用 aria2 的 `LICENSE.OpenSSL` 链接例外）。历史 Release 保留，不删除。

---

## 一、随 APK 分发的原生组件

| 组件 | 版本 | 许可证 | 集成形态 | 说明 |
| --- | --- | --- | --- | --- |
| Gopeed | `v1.9.3`（commit `a5cd53f94c18ac65add684b1113fa5f0b47cc4da`） | **GPL-3.0** | `libgopeed.aar`（gomobile，Go 内核），**与 App 同进程**，通过 JNI/反射调用 | 与 App 构成结合作品，导致整体必须 GPLv3；AAR 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`；源码：https://github.com/GopeedLab/gopeed/tree/v1.9.3；构建：`native/gopeed/build.ps1`（安装 Go 1.24.11、NDK 27.2.12479018，gomobile 实际用 go1.26.10 toolchain） |
| aria2 | **`1.35.0`**（源码 commit `7f79854b2ed6ef4978a8526831034b34b1fb3871`） | **GPL-2.0-or-later**（含 OpenSSL 链接例外，适用本二进制） | 预编译 arm64 可执行文件，重命名为 `jniLibs/arm64-v8a/libaria2c.so`，运行时子进程 + JSON-RPC | 来源 devgianlu/aria2-android `v1.35.0`（5,872,304 B，SHA-256 `88b8b5feec7e4e7392f129fed4cf61f52a88fdc5dfe1e1464ed467fb41bd1a7f`，ELF64 ET_DYN AArch64）；源码：https://github.com/aria2/aria2/tree/release-1.35.0；获取/校验：`native/aria2/fetch.ps1` |
| OpenSSL（aria2 静态链接） | **1.1.1d**（2019-09-10；devgianlu v1.35.0 的 openssl submodule pin `894da2fb7ed5d314ee5c2fc9fd2d9b8b74111596`） | **OpenSSL License + SSLeay License**（双许可证） | 静态链接进 `libaria2c.so` | 真机 `aria2 --version` 显示 `OpenSSL/1.1.1d`；**aria2 的 `LICENSE.OpenSSL` 链接例外适用于该许可证版本** |
| zlib（aria2 静态链接） | **1.2.9**（pin `2fa463bacfff79181df1a5270fb67cc679a53e71`） | zlib License | 静态链接进 `libaria2c.so` | 真机 `--version` 显示 `zlib/1.2.9` |
| c-ares（aria2 静态链接） | **1.14.0**（pin `e982924acee7f7313b4baa4ee5ec000c5e373c30`） | MIT | 静态链接进 `libaria2c.so` | 真机 `--version` 显示 `c-ares/1.14.0` |
| libexpat（aria2 静态链接） | **2.2.9**（pin `a7bc26b69768f7fb24f0c7976fae24b157b85b13`） | MIT | 静态链接进 `libaria2c.so` | 真机 `--version` 显示 `expat/2.2.9`；aria2 构建时 `--without-libxml2`、`--without-sqlite3` |
| libssh2（aria2 静态链接） | **1.9.0_DEV**（开发快照，pin `42d37aa63129a1b2644bf6495198923534322d64`） | BSD-3-Clause | 静态链接进 `libaria2c.so` | 真机 `--version` 显示 `libssh2/1.9.0_DEV`；非正式发行版，复现构建需按 pin 检出 |
| libc++（aria2 静态链接） | clang **8.0.7**（2019 Android NDK 工具链） | Apache-2.0 WITH LLVM-exception | 静态链接进 `libaria2c.so` | 真机 `--version` 显示 `Compiler: clang 8.0.7` |
| Go runtime（Gopeed AAR 内） | 随实际编译工具链 **go1.26.10**（安装的 Go 主版本为 1.24.11，`GOTOOLCHAIN=auto` 自动切换） | **BSD-3-Clause** | 静态编译进 `libgopeed.aar`（`libgojni.so` 43,908,776 B） | Go 标准库与运行时版权声明随 AAR 分发；构建记录见 `native/gopeed/build.ps1` 与 `.tools/gopeed-build/BUILD_REPORT.md` |

> **说明 1（aria2 + OpenSSL 1.1.1d 的许可关系）**：aria2 为 **GPL-2.0-or-later**；其仓库提供了 OpenSSL 链接例外文件 `LICENSE.OpenSSL`，明确允许与"许可证与 `LICENSE.OpenSSL` 中所列文本 identical"的 OpenSSL 版本链接。**OpenSSL 1.1.1d 使用 OpenSSL License + SSLeay License 双许可证，正是该例外适用的对象**，因此 aria2 1.35.0 + OpenSSL 1.1.1d 的组合可以按 GPLv2+ 分发；完整例外文本与 OpenSSL/SSLeay 许可证已随包提供（`aria2-LICENSE.OpenSSL`、`aria2-OpenSSL-Exception.txt`、`OpenSSL-1.1.1d-LICENSE.txt`）。整个 App 另含 Gopeed，按 GPLv3 分发，GPLv3 与 GPLv2+ 兼容。
>
> **说明 2（aria2 二进制来源的许可状态）**：devgianlu/aria2-android 仓库本身**没有声明 LICENSE/COPYING**（截至 2026-11-05 核查，GitHub API 返回 `license: null`）。分发该预编译二进制时，其许可义务来自 aria2（GPL-2.0-or-later）及其静态链接依赖各自的许可证；本工程随附源码获取方式、构建文件快照（`native/sources/devgianlu-build-files-v1.35.0.zip`）与全部许可证文本，并建议发布前自行按构建脚本重建。核查记录见 `licenses/devgianlu-aria2-android-LICENSE`、`licenses/DEPENDENCY-VERSIONS.md`。
>
> **说明 3（Gopeed Go 依赖）**：Gopeed AAR 静态包含 Go 模块依赖（`go.mod`/`go.sum` 锁定）。完整模块清单与各自许可证应以构建时 `go version -m libgopeed.so`、`go list -m all` 与 `go.sum` 为准，并随 Release 源码归档提供；Gopeed 自身主许可证为 GPL-3.0。
>
> **说明 4（安全/供应链提示）**：OpenSSL 1.1.1d（2019-09）与 aria2 1.35.0（2019-10）**均已停止维护，存在已知 CVE**；libssh2 为 1.9.0_DEV 开发快照。本版本为兼容 Android 16 而沿用旧预编译二进制；正式分发前建议用受支持的 OpenSSL（1.1.1w / 3.x）与新版 aria2 按 devgianlu 构建脚本自行复现，并同步更新 `licenses/DEPENDENCY-VERSIONS.md` 与哈希（见 [KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) KI-30）。
>
> **说明 5（Motrix）**：Motrix 桌面版为 MIT；本项目仅参考其产品定位与交互思路，**未使用其代码、界面资源、图标或品牌素材**，不属于本 APK 的分发组件。

---

## 二、随 APK 分发的 JVM/Android 运行时组件

| 组件 | 版本 | 许可证 | 用途 | 备注 |
| --- | --- | --- | --- | --- |
| Kotlin Standard Library | 2.0.21（由 Kotlin 插件引入） | Apache-2.0 | 语言运行时 | 随 R8 裁剪 |
| kotlinx-coroutines-android / kotlinx-coroutines-core | 1.9.0 | Apache-2.0 | 协程与并发 | — |
| OkHttp | 4.12.0 | Apache-2.0 | HTTP 客户端、RPC、Range 请求 | 内置引擎核心；aria2 JSON-RPC / Gopeed REST 客户端 |
| Okio | 随 OkHttp 传递（4.12.0 对应 3.x） | Apache-2.0 | OkHttp 的 I/O 层 | 传递依赖 |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | Android 兼容与 KTX 扩展 | — |
| androidx.activity:activity-compose | 1.9.3 | Apache-2.0 | Compose Activity 与结果回调 | — |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | Apache-2.0 | 生命周期 | — |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 | Apache-2.0 | `collectAsStateWithLifecycle` 等 | — |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | Apache-2.0 | ViewModel 与 Compose 集成 | — |
| androidx.documentfile | 1.0.1 | Apache-2.0 | SAF 目录读写（自定义保存位置） | — |
| Jetpack Compose BOM | 2024.12.01 | Apache-2.0 | 统一 Compose 版本 | 仅版本约束，不含代码 |
| androidx.compose.ui:ui / ui-graphics / ui-tooling-preview | 由 BOM 决定 | Apache-2.0 | Compose UI 基础 | — |
| androidx.compose.material3:material3 | 由 BOM 决定 | Apache-2.0 | Material 3 组件 | — |
| androidx.compose.material:material-icons-core | 由 BOM 决定 | Apache-2.0 | 图标集（`Icons.Default.*`） | 仅 core 子集 |
| androidx.compose.ui:ui-tooling（debug） | 由 BOM 决定 | Apache-2.0 | 仅调试构建的预览工具 | 仅 debug 包 |
| androidx.compose.ui:ui-test-manifest（debug） | 由 BOM 决定 | Apache-2.0 | 仅调试构建 | 仅 debug 包 |

> 说明：构建脚本 `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }` 会把部分 AAR 中的许可证占位文件从 APK 中排除。1.1.1 的补偿措施是：**在 APK `assets/licenses/` 中打包完整许可证全文**（GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 1.1.1d 许可证、历史 OpenSSL 例外、各静态依赖许可证、DEPENDENCY-VERSIONS、SOURCE-OFFER、Apache-2.0.txt、THIRD_PARTY_NOTICES），并在应用内「设置 → 关于 → 开源许可 / 源码获取」提供说明与源码地址；仓库同时保留 `THIRD_PARTY_NOTICES.md` 与 `licenses/`。若再分发 APK，请一并提供本文件与 `LICENSE`。

---

## 三、仅用于构建与测试、不随 APK 分发的组件

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | 构建 |
| Kotlin Gradle Plugin（`org.jetbrains.kotlin.android`、`...plugin.compose`） | 2.0.21 | Apache-2.0 | 构建 |
| Gradle Wrapper / Gradle | 8.9 | Apache-2.0 | 构建 |
| Go 工具链 | 1.24.11（实际 gomobile 编译用 go1.26.10） | BSD-3-Clause | 构建 Gopeed AAR（`native/gopeed/build.ps1`） |
| Android NDK | 27.2.12479018 | 各组件许可证（LLVM/Android NDK 条款） | 构建 Gopeed AAR |
| gomobile / gobind | 由 `go install ...@latest` 安装 | BSD-3-Clause | gomobile bind |
| JUnit 4 | 4.13.2 | EPL-1.0 | 单元测试 |
| kotlinx-coroutines-test | 1.9.0 | Apache-2.0 | 单元测试 |
| OkHttp MockWebServer | 4.12.0 | Apache-2.0 | 单元测试（本地 HTTP 服务桩） |
| org.json:json | 20240303 | Public Domain（Maven Central 标注；历史版本为 JSON License） | 单元测试中提供 `org.json` 实现（Android 运行时由系统提供） |

上述组件只在开发机或 CI 上运行，未打包进发布产物，因此不构成对最终用户的分发（Gopeed AAR 内的 Go runtime 除外，见第一节）。

---

## 四、被参考但未作为组件分发的项目

| 项目 | 许可证 | 关系 |
| --- | --- | --- |
| Motrix（桌面版，Electron） | MIT | **仅参考产品定位、功能组织与交互思路**。不包含 Motrix 的源代码、界面资源、图标或品牌素材，也不是 Motrix 桌面版的封装。 |
| aria2 / Gopeed 官方桌面客户端 | 各自许可证 | 仅作为引擎上游与文档参考；APK 中只包含上述 AAR / aria2 二进制，不包含官方 GUI 客户端代码。 |

关于许可证结论的详细论证见 [docs/ENGINE_LICENSE_ANALYSIS.md](docs/ENGINE_LICENSE_ANALYSIS.md) 与 [docs/COMPLIANCE.md](docs/COMPLIANCE.md)。

---

## 五、图标与素材来源

- 应用图标（`app/src/main/res/drawable/ic_launcher_foreground.xml`、`mipmap-anydpi-v26/ic_launcher.xml`）与界面内图标（`ic_download`、`ic_pause`、`ic_folder`、`ic_wifi`、`ic_bolt`、`ic_link`、`ic_battery`、`ic_rocket`、`ic_notification`）为本工程自绘的 VectorDrawable。
- 未使用 Motrix 官方图标、未使用 Gopeed/aria2 官方视觉素材、未使用任何第三方付费素材库。

---

## 六、许可证原文获取

| 许可证 / 文本 | 仓库位置 | 参考地址 |
| --- | --- | --- |
| GNU GPLv3 全文 | `LICENSE`、`licenses/GPL-3.0.txt`、APK `assets/licenses/GPL-3.0.txt` | https://www.gnu.org/licenses/gpl-3.0.txt |
| GNU GPLv2 全文 | `licenses/GPL-2.0.txt`、APK `assets/licenses/GPL-2.0.txt` | https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt |
| Gopeed LICENSE（GPL-3.0） | `licenses/Gopeed-LICENSE.txt`、APK `assets/licenses/Gopeed-LICENSE.txt` | https://github.com/GopeedLab/gopeed/blob/v1.9.3/LICENSE |
| aria2 COPYING（GPLv2） | `licenses/aria2-COPYING.txt`、APK `assets/licenses/aria2-COPYING.txt` | https://github.com/aria2/aria2/blob/release-1.35.0/COPYING |
| aria2 OpenSSL 链接例外（`LICENSE.OpenSSL`） | `licenses/aria2-LICENSE.OpenSSL`、`aria2-OpenSSL-Exception.txt`（说明该例外适用于 OpenSSL/SSLeay 双许可证版本，如 1.1.1d） | https://github.com/aria2/aria2/blob/release-1.35.0/LICENSE.OpenSSL |
| OpenSSL 1.1.1d（OpenSSL License + SSLeay License） | `licenses/OpenSSL-1.1.1d-LICENSE.txt` | https://www.openssl.org/source/license.html |
| zlib 1.2.9 许可证 | `licenses/zlib-1.2.9-zlib-license.txt` | https://opensource.org/license/zlib |
| c-ares 1.14.0 / libexpat 2.2.9（MIT） | `licenses/c-ares-1.14.0-MIT.txt`、`licenses/libexpat-2.2.9-MIT.txt` | https://opensource.org/license/mit |
| libssh2 1.9.0_DEV（BSD-3-Clause） | `licenses/libssh2-1.9.0_DEV-BSD-3-Clause.txt` | https://opensource.org/license/bsd-3-clause |
| Apache-2.0 全文 | `licenses/Apache-2.0.txt`（Kotlin/AndroidX/libc++ 等） | https://www.apache.org/licenses/LICENSE-2.0 |
| 依赖版本/commit 清单 | `licenses/DEPENDENCY-VERSIONS.md` | `native/sources/devgianlu-build-files-v1.35.0.zip`（构建文件快照） |
| 源码获取要约 | `licenses/SOURCE-OFFER.txt` | 本仓库 Release / Issue |
| 第三方组件清单 | `THIRD_PARTY_NOTICES.md`、APK `assets/licenses/THIRD_PARTY_NOTICES.md` | 本文件 |
| 二进制构建仓库许可状态 | `licenses/devgianlu-aria2-android-LICENSE` | GitHub API 核查记录 |

> v1.1.1 最终 APK 的 `assets/licenses/` 包含 17 个文件（上述文本 + `README.md` 索引），已随 APK 打包；最终 APK：debug 29,358,263 B（28.00 MB）/ SHA-256 `583b6846…f611bc4`，release 18,771,902 B（17.90 MB）/ SHA-256 `a8b5be56…cbd2d5d`（完整值见 `dist/SHA256SUMS.txt` 与 Release 页面）。

如发现清单遗漏或许可证标注错误，请在项目仓库提交 issue 更正。

---

**一句话总结**：Motrix Lite 1.1.1 打包了 Gopeed（GPL-3.0）与 aria2 1.35.0（GPL-2.0-or-later，静态链接 OpenSSL 1.1.1d，适用 aria2 的 OpenSSL 链接例外），因此**整个 APK 按 GPLv3 分发**；任何包含这两个内核的 APK 都**不能闭源分发**。
