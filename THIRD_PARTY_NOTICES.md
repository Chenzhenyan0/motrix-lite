# 第三方组件与许可声明（THIRD PARTY NOTICES）

本文件列出 Motrix Lite Android 应用（版本 1.1.0，包名 `com.motrixlite.downloader`）所使用的第三方开源组件、许可证类型，以及是否随 APK 分发。

- 本工程整体按 **GNU GPLv3** 分发，见 [LICENSE](LICENSE)；第三方组件各自保留其原许可证。
- 依赖版本以 `app/build.gradle.kts`、`native/gopeed/build.ps1`、`native/aria2/fetch.ps1` 为准（唯一事实来源）。
- 完整许可证文本随 APK 打包在 `app/src/main/assets/licenses/`，并同时存放在仓库 `licenses/` 与 `native/licenses/`。
- 本文档只做许可信息归集，**不构成法律意见**；涉及再分发、商用或上架时请自行复核许可证原文。

---

## 一、随 APK 分发的原生产生组件（1.1.0 新增）

| 组件 | 版本 | 许可证 | 集成形态 | 说明 |
| --- | --- | --- | --- | --- |
| Gopeed | `v1.9.3`（commit `a5cd53f94c18ac65add684b1113fa5f0b47cc4da`） | **GPL-3.0** | `libgopeed.aar`（gomobile，Go 内核），**与 App 同进程**，通过 JNI/反射调用 | 与 App 构成结合作品，导致整体必须 GPLv3；AAR 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`；源码：https://github.com/GopeedLab/gopeed/tree/v1.9.3；构建：`native/gopeed/build.ps1`（安装 Go 1.24.11、NDK 27.2.12479018，gomobile 实际用 go1.26.10 toolchain） |
| aria2 | `1.37.0`（源码 commit `02f2d0d8472b3c38c29b4dba8c75ebd5fdd2899a`） | **GPL-2.0-or-later** | 预编译 arm64 可执行文件，重命名为 `jniLibs/arm64-v8a/libaria2c.so`，运行时子进程 + JSON-RPC | aria2 仓库提供历史 `LICENSE.OpenSSL` 链接例外（针对旧 OpenSSL/SSLeay 双许可证），**不覆盖本二进制实际使用的 OpenSSL 3.0.0**；整体按 GPLv3 分发（GPLv3 与 Apache-2.0 兼容）。二进制来源 devgianlu/aria2-android `v1.37.0`（69,107,592 B，SHA-256 `dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a`）；源码：https://github.com/aria2/aria2/tree/release-1.37.0；获取/校验：`native/aria2/fetch.ps1` |
| Go runtime（Gopeed AAR 内） | 随实际编译工具链 **go1.26.10**（安装的 Go 主版本为 1.24.11，`GOTOOLCHAIN=auto` 自动切换） | **BSD-3-Clause** | 静态编译进 `libgopeed.aar`（`libgojni.so` 43,908,776 B） | Go 标准库与运行时版权声明随 AAR 分发；构建记录见 `native/gopeed/build.ps1` 与 `.tools/gopeed-build/BUILD_REPORT.md` |
| OpenSSL（aria2 静态链接） | **3.0.0**（2021-09-07；devgianlu v1.37.0 的 openssl submodule pin `89cd17a031e022211684eb7eb41190cf1910f9fa`） | **Apache-2.0** | 静态链接进 `libaria2c.so` | 证据：该 pin 的 `VERSION.dat` 为 MAJOR=3/MINOR=0/PATCH=0，二进制含 `OSSL_PROVIDER_load`、`OSSL_LIB_CTX_*`、`EVP_MD_fetch` 等 OpenSSL 3 API；须随附 Apache-2.0 全文与 OpenSSL 版权声明（`OpenSSL-3.0.0-Apache-2.0.txt`、`OpenSSL-3.0.0-COPYRIGHT.txt`） |
| libssh2（aria2 静态链接） | **1.9.0_DEV**（开发快照，submodule pin `635caa90787220ac3773c1d5ba11f1236c22eae8`） | BSD-3-Clause | 静态链接进 `libaria2c.so` | aria2 以 `--with-libssh2` 构建；非正式发行版，复现构建需按 pin 检出（`libssh2-1.9.0-BSD-3-Clause.txt`） |
| c-ares（aria2 静态链接） | **1.18.1**（pin `2aa086f822aad5017a6f2061ef656f237a62d0ed`） | MIT | 静态链接进 `libaria2c.so` | aria2 以 `--with-libcares` 构建（`c-ares-1.18.1-MIT.txt`） |
| libexpat（aria2 静态链接） | **2.4.1**（pin `a28238bdeebc087071777001245df1876a11f5ee`） | MIT | 静态链接进 `libaria2c.so` | aria2 以 `--with-libexpat` 构建；构建时 `--without-libxml2`、`--without-sqlite3`（`libexpat-2.4.1-MIT.txt`） |
| zlib（aria2 静态链接） | **1.2.11**（pin `cacf7f1d4e3d44d871b605da3b647f07d718623f`） | zlib License | 静态链接进 `libaria2c.so` | aria2 以 `--with-libz` 构建（`zlib-1.2.11-zlib-license.txt`） |
| libc++（aria2 静态链接） | NDK **r23b** 工具链（clang/LLVM 12.0.8） | Apache-2.0 WITH LLVM-exception | 静态链接进 `libaria2c.so` | 构建参数 `-static-libstdc++`；`.comment` 记录 clang 12.0.8 / LLD 12.0.8 |

> **说明 1（aria2 二进制来源的许可状态与 OpenSSL 事实修正）**：devgianlu/aria2-android 仓库本身**没有声明 LICENSE/COPYING**（截至 2026-11-05 核查，GitHub API 返回 `license: null`）。该 v1.37.0 预编译二进制**实际静态链接 OpenSSL 3.0.0（Apache-2.0）**（openssl submodule pin `89cd17a031e022211684eb7eb41190cf1910f9fa`，`VERSION.dat = 3.0.0`，二进制含 `OSSL_PROVIDER_load` 等 3.x API），**不是**旧文档所称的 OpenSSL 1.1.1 / SSLeay 双许可证。aria2 仓库中的 `LICENSE.OpenSSL` 是历史链接例外文本（针对旧 OpenSSL/SSLeay），**不能用来解释本二进制与 OpenSSL 3.0.0 的组合**；本工程整体按 GPLv3 分发，GPLv3 与 Apache-2.0 兼容，因此组合分发成立，但必须随附 Apache-2.0 全文与 OpenSSL 版权声明。分发该预编译二进制时，许可义务来自 aria2（GPL-2.0-or-later）及其静态链接依赖各自的许可证；随附源码获取方式、构建文件快照与全部许可证文本，并建议发布前自行按构建脚本重建。核查记录见 `licenses/devgianlu-aria2-android-LICENSE`、`licenses/DEPENDENCY-VERSIONS.md` 与 `native/sources/devgianlu-build-files.zip`。
>
> **说明 2（Gopeed Go 依赖）**：Gopeed AAR 静态包含 Go 模块依赖（`go.mod`/`go.sum` 锁定）。完整模块清单与各自许可证应以构建时 `go version -m libgopeed.so`、`go list -m all` 与 `go.sum` 为准，并随 Release 源码归档提供；Gopeed 自身主许可证为 GPL-3.0。
>
> **说明 3（Motrix）**：Motrix 桌面版为 MIT；本项目仅参考其产品定位与交互思路，**未使用其代码、界面资源、图标或品牌素材**，不属于本 APK 的分发组件。
>
> **说明 4（供应链提示）**：OpenSSL 3.0.0 发布于 2021-09，后续 3.0.x 已修复多个 CVE；libssh2 pin 为 1.9.0_DEV 开发快照（非正式发行版）。正式分发前建议用更新版本 OpenSSL/libssh2 重新构建 aria2，或至少在 Release 说明中披露该供应链时点与风险（见 [KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) KI-30）。

---

## 二、随 APK 分发的 JVM/Android 运行时组件（与 1.0.0 相同）

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

> 说明：构建脚本 `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }` 会把部分 AAR 中的许可证占位文件从 APK 中排除。1.1.0 的补偿措施是：**在 APK `assets/licenses/` 中打包完整许可证全文**（GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 3.0.0 Apache-2.0 全文+版权、各静态依赖许可证、历史 OpenSSL 例外说明、DEPENDENCY-VERSIONS、SOURCE-OFFER），并在应用内「设置 → 关于 → 开源许可 / 源码获取」提供说明与源码地址；仓库同时保留 `THIRD_PARTY_NOTICES.md` 与 `licenses/`。若再分发 APK，请一并提供本文件与 `LICENSE`。

---

## 三、仅用于构建与测试、不随 APK 分发的组件

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | 构建 |
| Kotlin Gradle Plugin（`org.jetbrains.kotlin.android`、`...plugin.compose`） | 2.0.21 | Apache-2.0 | 构建 |
| Gradle Wrapper / Gradle | 8.9 | Apache-2.0 | 构建 |
| Go 工具链 | 1.24.11 | BSD-3-Clause | 构建 Gopeed AAR（`native/gopeed/build.ps1`） |
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
| Gopeed LICENSE（GPL-3.0） | `licenses/Gopeed-LICENSE.txt`、`native/licenses/Gopeed-LICENSE` | https://github.com/GopeedLab/gopeed/blob/v1.9.3/LICENSE |
| aria2 COPYING（GPLv2） | `licenses/aria2-COPYING.txt`、`native/licenses/aria2-COPYING` | https://github.com/aria2/aria2/blob/release-1.37.0/COPYING |
| aria2 OpenSSL 链接例外（`LICENSE.OpenSSL`，历史文本） | `licenses/aria2-LICENSE.OpenSSL`、`native/licenses/aria2-LICENSE.OpenSSL`（全文）、`aria2-OpenSSL-Exception.txt`（说明：该例外针对旧 OpenSSL/SSLeay，不覆盖本二进制实际使用的 OpenSSL 3.0.0） | https://github.com/aria2/aria2/blob/release-1.37.0/LICENSE.OpenSSL |
| **OpenSSL 3.0.0 许可证（实际使用）** | `licenses/OpenSSL-3.0.0-Apache-2.0.txt`（Apache-2.0 全文）、`licenses/OpenSSL-3.0.0-COPYRIGHT.txt`、APK `assets/licenses/` 同名文件 | https://www.openssl.org/source/license.html 、https://www.apache.org/licenses/LICENSE-2.0 |
| zlib 1.2.11 许可证 | `licenses/zlib-1.2.11-zlib-license.txt` | https://opensource.org/license/zlib |
| c-ares 1.18.1 / libexpat 2.4.1（MIT） | `licenses/c-ares-1.18.1-MIT.txt`、`licenses/libexpat-2.4.1-MIT.txt` | https://opensource.org/license/mit |
| libssh2 1.9.0_DEV（BSD-3-Clause） | `licenses/libssh2-1.9.0-BSD-3-Clause.txt` | https://opensource.org/license/bsd-3-clause |
| 依赖版本/commit 清单 | `licenses/DEPENDENCY-VERSIONS.md`、APK `assets/licenses/DEPENDENCY-VERSIONS.md` | `native/sources/devgianlu-build-files.zip`（构建文件快照） |
| 源码获取要约 | `licenses/SOURCE-OFFER.txt`、APK `assets/licenses/SOURCE-OFFER.txt` | 本仓库 Release / Issue |
| 第三方组件清单 | `THIRD_PARTY_NOTICES.md`、APK `assets/licenses/THIRD_PARTY_NOTICES.md` | 本文件 |
| Apache-2.0 / MIT / BSD-3 / EPL-1.0 / zlib | 各组件 AAR/源码内声明；本文档列出适用范围 | https://www.apache.org/licenses/LICENSE-2.0 、https://opensource.org/license/mit 、https://opensource.org/license/bsd-3-clause 、https://www.eclipse.org/legal/epl-1.0/ 、https://opensource.org/license/zlib |

> v1.1.0 最终构建的 APK `assets/licenses/` 已包含以下 16 个文件：`GPL-3.0.txt`、`GPL-2.0.txt`、`aria2-COPYING.txt`、`aria2-LICENSE.OpenSSL`、`aria2-OpenSSL-Exception.txt`、`Gopeed-LICENSE.txt`、`OpenSSL-3.0.0-Apache-2.0.txt`、`OpenSSL-3.0.0-COPYRIGHT.txt`、`zlib-1.2.11-zlib-license.txt`、`c-ares-1.18.1-MIT.txt`、`libexpat-2.4.1-MIT.txt`、`libssh2-1.9.0-BSD-3-Clause.txt`、`DEPENDENCY-VERSIONS.md`、`devgianlu-aria2-android-LICENSE`、`SOURCE-OFFER.txt`、`THIRD_PARTY_NOTICES.md`（已通过解包最终 APK 核验）。

如发现清单遗漏或许可证标注错误，请在项目仓库提交 issue 更正。

---

**一句话总结**：Motrix Lite 1.1.0 打包了 Gopeed（GPL-3.0）与 aria2（GPL-2.0-or-later，静态链接 OpenSSL 3.0.0 / Apache-2.0），因此**整个 APK 按 GPLv3 分发**；任何包含这两个内核的 APK 都**不能闭源分发**。
